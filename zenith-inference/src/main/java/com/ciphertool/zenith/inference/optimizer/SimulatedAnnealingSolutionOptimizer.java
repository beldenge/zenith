/**
 * Copyright 2017-2019 George Belden
 *
 * This file is part of Zenith.
 *
 * Zenith is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * Zenith is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Zenith. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ciphertool.zenith.inference.optimizer;

import com.ciphertool.zenith.inference.entities.Cipher;
import com.ciphertool.zenith.inference.entities.CipherSolution;
import com.ciphertool.zenith.inference.evaluator.PlaintextEvaluator;
import com.ciphertool.zenith.inference.printer.CipherSolutionPrinter;
import com.ciphertool.zenith.inference.probability.LetterProbability;
import com.ciphertool.zenith.inference.transformer.plaintext.PlaintextTransformer;
import com.ciphertool.zenith.math.selection.RouletteSampler;
import com.ciphertool.zenith.model.LanguageConstants;
import com.ciphertool.zenith.model.entities.TreeNGram;
import com.ciphertool.zenith.model.markov.MapMarkovModel;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@ConditionalOnProperty(value = "decipherment.optimizer", havingValue = "SimulatedAnnealingSolutionOptimizer")
public class SimulatedAnnealingSolutionOptimizer implements SolutionOptimizer {
    private Logger log = LoggerFactory.getLogger(getClass());

    private static SplittableRandom RANDOM = new SplittableRandom();

    @Value("${simulated-annealing.sampler.iterations}")
    private int samplerIterations;

    @Value("${simulated-annealing.temperature.max}")
    private double annealingTemperatureMax;

    @Value("${simulated-annealing.temperature.min}")
    private double annealingTemperatureMin;

    @Value("${simulated-annealing.sampler.iterate-randomly}")
    private Boolean iterateRandomly;

    @Value("${markov.letter.order}")
    private int markovOrder;

    @Value("${decipherment.epochs:1}")
    private int epochs;

    @Value("${decipherment.known-solution.correctness-threshold:0.9}")
    private double knownSolutionCorrectnessThreshold;

    @Autowired
    protected Cipher cipher;

    @Autowired
    private MapMarkovModel letterMarkovModel;

    @Autowired(required = false)
    @Qualifier("activePlaintextTransformers")
    private List<PlaintextTransformer> plaintextTransformers;

    @Autowired
    private PlaintextEvaluator plaintextEvaluator;

    @Autowired
    private CipherSolutionPrinter cipherSolutionPrinter;

    @Override
    public CipherSolution optimize() {
        int cipherKeySize = (int) cipher.getCiphertextCharacters().stream()
                .map(c -> c.getValue())
                .distinct()
                .count();

        List<LetterProbability> letterUnigramProbabilities = new ArrayList<>(LanguageConstants.LOWERCASE_LETTERS_SIZE);

        double probability;
        for (TreeNGram node : letterMarkovModel.getFirstOrderNodes()) {
            probability = (double) node.getCount() / (double) letterMarkovModel.getTotalNumberOfNgrams();

            letterUnigramProbabilities.add(new LetterProbability(node.getCumulativeString().charAt(0), probability));

            log.info(node.getCumulativeString().charAt(0) + ": " + probability);
        }

        log.info("unknownLetterNGramProbability: {}", letterMarkovModel.getUnknownLetterNGramProbability());

        Collections.sort(letterUnigramProbabilities);
        RouletteSampler<LetterProbability> unigramRouletteSampler = new RouletteSampler<>();
        unigramRouletteSampler.reIndex(letterUnigramProbabilities);

        int correctSolutions = 0;
        CipherSolution overallBest = null;

        for (int epoch = 0; epoch < epochs; epoch++) {
            CipherSolution initialSolution = generateInitialSolutionProposal(cipher, cipherKeySize, unigramRouletteSampler, letterUnigramProbabilities);

            log.info("Epoch {} of {}.  Running sampler for {} iterations.", (epoch + 1), epochs, samplerIterations);

            CipherSolution best = performEpoch(initialSolution);

            if (cipher.hasKnownSolution() && knownSolutionCorrectnessThreshold <= best.evaluateKnownSolution()) {
                correctSolutions ++;
            }

            overallBest = (overallBest == null) ? best : (best.getScore() > overallBest.getScore() ? best : overallBest);
        }

        if (cipher.hasKnownSolution()) {
            log.info("{} out of {} epochs ({}%) produced the correct solution.", correctSolutions, epochs, String.format("%1$,.2f", (correctSolutions / (double) epochs) * 100.0));
        }

        return overallBest;
    }

    private CipherSolution generateInitialSolutionProposal(Cipher cipher, int cipherKeySize, RouletteSampler<LetterProbability> unigramRouletteSampler, List<LetterProbability> letterUnigramProbabilities) {
        CipherSolution solutionProposal = new CipherSolution(cipher, cipherKeySize);

        cipher.getCiphertextCharacters().stream()
                .map(ciphertext -> ciphertext.getValue())
                .distinct()
                .forEach(ciphertext -> {
                    // Pick a plaintext at random according to the language model
                    String nextPlaintext = letterUnigramProbabilities.get(unigramRouletteSampler.getNextIndex()).getValue().toString();

                    solutionProposal.putMapping(ciphertext, nextPlaintext);
                });

        return solutionProposal;
    }

    private CipherSolution performEpoch(CipherSolution initialSolution) {
        long start = System.currentTimeMillis();

        String solutionString = initialSolution.asSingleLineString();
        if (plaintextTransformers != null) {
            for (PlaintextTransformer plaintextTransformer : plaintextTransformers) {
                solutionString = plaintextTransformer.transform(solutionString);
            }
        }

        plaintextEvaluator.evaluate(initialSolution, solutionString, null);

        if (log.isDebugEnabled()) {
            cipherSolutionPrinter.print(initialSolution);
        }

        double temperature;
        CipherSolution next = initialSolution;
        long startLetterSampling;
        char[] solutionCharArray = next.asSingleLineString().toCharArray();

        int i;
        for (i = 0; i < samplerIterations; i++) {
            long iterationStart = System.currentTimeMillis();

            /*
             * Set temperature as a ratio of the max temperature to the number of iterations left, offset by the min
             * temperature so as not to go below it
             */
            temperature = ((annealingTemperatureMax - annealingTemperatureMin) * ((samplerIterations - (double) i) / samplerIterations)) + annealingTemperatureMin;

            startLetterSampling = System.currentTimeMillis();
            next = runLetterSampler(temperature, next, solutionCharArray);

            if (log.isDebugEnabled()) {
                long now = System.currentTimeMillis();
                log.debug("Iteration {} complete.  [elapsed={}ms, letterSampling={}ms, temp={}]", (i + 1), (now - iterationStart), (now - startLetterSampling), String.format("%1$,.4f", temperature));
                cipherSolutionPrinter.print(next);
            }
        }

        long totalElapsed = System.currentTimeMillis() - start;
        log.info("Letter sampling completed in {}ms.  Average={}ms.", totalElapsed, ((double) totalElapsed / (double) i));

        if (log.isInfoEnabled()) {
            cipherSolutionPrinter.print(next);
        }

        log.info("Mappings for best probability:");

        for (Map.Entry<String, String> entry : next.getMappings().entrySet()) {
            log.info("{}: {}", entry.getKey(), entry.getValue());
        }

        return next;
    }

    private CipherSolution runLetterSampler(double temperature, CipherSolution solution, char[] solutionCharArray) {
        List<String> mappingList = new ArrayList<>(solution.getMappings().size());
        mappingList.addAll(solution.getMappings().keySet());

        String nextKey;

        // For each cipher symbol type, run the letter sampling
        for (int i = 0; i < mappingList.size(); i++) {
            nextKey = iterateRandomly ? mappingList.remove(RANDOM.nextInt(mappingList.size())) : mappingList.get(i);

            String letter = String.valueOf(LanguageConstants.LOWERCASE_LETTERS.getChar(RANDOM.nextInt(LanguageConstants.LOWERCASE_LETTERS_SIZE)));

            String originalMapping = solution.getMappings().get(nextKey);

            if (letter.equals(originalMapping)) {
                continue;
            }

            double originalScore = solution.getScore();
            double originalIndexOfCoincidence = solution.getIndexOfCoincidence();
            solution.replaceMapping(nextKey, letter);

            for (int index : cipher.getCipherSymbolIndicesMap().get(nextKey)) {
                solutionCharArray[index] = letter.charAt(0);
            }

            String proposalString = new String(solutionCharArray);

            if (plaintextTransformers != null) {
                for (PlaintextTransformer plaintextTransformer : plaintextTransformers) {
                    proposalString = plaintextTransformer.transform(proposalString);
                }
            }

            Int2DoubleMap logProbabilitiesUpdated = plaintextEvaluator.evaluate(solution, proposalString, nextKey);

            if (!selectNext(temperature, originalScore, solution.getScore())) {
                solution.setIndexOfCoincidence(originalIndexOfCoincidence);
                solution.replaceMapping(nextKey, originalMapping);

                for (Int2DoubleMap.Entry entry : logProbabilitiesUpdated.int2DoubleEntrySet()) {
                    solution.replaceLogProbability(entry.getIntKey(), entry.getDoubleValue());
                }

                for (int index : cipher.getCipherSymbolIndicesMap().get(nextKey)) {
                    solutionCharArray[index] = originalMapping.charAt(0);
                }
            }
        }

        return solution;
    }

    private boolean selectNext(double temperature, double solutionScore, double proposalScore) {
        if (proposalScore >= solutionScore) {
            return true;
        }

        // Need to convert to log probabilities in order for the acceptance probability calculation to be useful
        double acceptanceProbability = Math.exp(((solutionScore - proposalScore) / temperature) * -1d);

        log.debug("Acceptance probability: {}", acceptanceProbability);

        if (acceptanceProbability < 0d) {
            throw new IllegalStateException("Acceptance probability was calculated to be less than zero.  Please review the math as this should not happen.");
        }

        if (acceptanceProbability > 1d || RANDOM.nextDouble() < acceptanceProbability) {
            return true;
        }

        return false;
    }
}
