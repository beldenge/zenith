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

package com.ciphertool.zenith.inference;

import com.ciphertool.zenith.inference.dao.CipherDao;
import com.ciphertool.zenith.inference.entities.Cipher;
import com.ciphertool.zenith.inference.entities.CipherSolution;
import com.ciphertool.zenith.inference.entities.Plaintext;
import com.ciphertool.zenith.inference.evaluator.PlaintextEvaluator;
import com.ciphertool.zenith.inference.evaluator.Zodiac408KnownPlaintextEvaluator;
import com.ciphertool.zenith.inference.probability.LetterProbability;
import com.ciphertool.zenith.inference.selection.RouletteSampler;
import com.ciphertool.zenith.model.ModelConstants;
import com.ciphertool.zenith.model.dao.LetterNGramDao;
import com.ciphertool.zenith.model.entities.TreeNGram;
import com.ciphertool.zenith.model.markov.TreeMarkovModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class BayesianDecipherManager {
	private Logger				log						= LoggerFactory.getLogger(getClass());

	@Value("${cipher.name}")
	private String							cipherName;

	@Value("${bayes.sampler.iterations}")
	private int								samplerIterations;

	@Value("${bayes.annealing.temperature.max}")
	private int								annealingTemperatureMax;

	@Value("${bayes.annealing.temperature.min}")
	private int								annealingTemperatureMin;

	@Value("${bayes.sampler.iterateRandomly}")
	private Boolean							iterateRandomly;

	@Value("${markov.letter.order}")
	private int								markovOrder;

	@Value("${bayes.useKnownEvaluator:false}")
	private boolean								useKnownEvaluator;

	@Autowired
	private PlaintextEvaluator				plaintextEvaluator;

	@Autowired
	private CipherDao						cipherDao;

	@Autowired
	private LetterNGramDao					letterNGramDao;

	@Autowired(required = false)
	private Zodiac408KnownPlaintextEvaluator knownPlaintextEvaluator;

	private Cipher							cipher;
	private int								cipherKeySize;
	private static List<LetterProbability>	letterUnigramProbabilities	= new ArrayList<>();
	private TreeMarkovModel					letterMarkovModel;

	private RouletteSampler<LetterProbability> unigramRouletteSampler = new RouletteSampler<>();
	private Double totalUnigramProbability;

	@PostConstruct
	public void setUp() {
		this.cipher = cipherDao.findByCipherName(cipherName);
		int totalCharacters = this.cipher.getCiphertextCharacters().size();
		int lastRowBegin = (this.cipher.getColumns() * (this.cipher.getRows() - 1));

		// Remove the last row altogether
		for (int i = lastRowBegin; i < totalCharacters; i++) {
			this.cipher.removeCiphertextCharacter(this.cipher.getCiphertextCharacters().get(lastRowBegin));
		}

		cipherKeySize = (int) cipher.getCiphertextCharacters().stream().map(c -> c.getValue()).distinct().count();

		long startFindAll = System.currentTimeMillis();
		log.info("Beginning retrieval of all n-grams.");

		/*
		 * Begin setting up letter n-gram model
		 */
		List<TreeNGram> nGramNodes = letterNGramDao.findAll();

		log.info("Finished retrieving {} n-grams in {}ms.", nGramNodes.size(), (System.currentTimeMillis()
				- startFindAll));

		this.letterMarkovModel = new TreeMarkovModel(this.markovOrder);

		long startAdding = System.currentTimeMillis();
		log.info("Adding nodes to the model.");

		for (TreeNGram nGramNode : nGramNodes) {
			this.letterMarkovModel.addNode(nGramNode);
		}

		List<TreeNGram> firstOrderNodes = new ArrayList<>(letterMarkovModel.getRootNode().getTransitions().values());

		long rootNodeCount = firstOrderNodes.stream().mapToLong(TreeNGram::getCount).sum();

		Double unknownLetterNGramProbability = 1d / (double) rootNodeCount;
		letterMarkovModel.setUnknownLetterNGramProbability(unknownLetterNGramProbability);
		letterMarkovModel.setUnknownLetterNGramLogProbability(Math.log(unknownLetterNGramProbability));

		log.info("Finished adding nodes to the letter n-gram model in {}ms.", (System.currentTimeMillis()
				- startAdding));

		long total = firstOrderNodes.stream()
				.filter(node -> !node.getCumulativeString().equals(" "))
				.mapToLong(TreeNGram::getCount).sum();

		Double probability;
		for (TreeNGram node : firstOrderNodes) {
			if (!node.getCumulativeString().equals(" ")) {
				probability = (double) node.getCount() / (double) total;

				letterUnigramProbabilities.add(new LetterProbability(node.getCumulativeString().charAt(0), probability));

				log.info(node.getCumulativeString().charAt(0) + ": "
						+ probability.toString().substring(0, Math.min(7, probability.toString().length())));
			}
		}

		log.info("unknownLetterNGramProbability: {}", this.letterMarkovModel.getUnknownLetterNGramProbability());
	}

	public void run() {
		// Initialize the solution key
		CipherSolution initialSolution = new CipherSolution(cipher, cipherKeySize);

		Collections.sort(letterUnigramProbabilities);
		totalUnigramProbability = unigramRouletteSampler.reIndex(letterUnigramProbabilities);

		cipher.getCiphertextCharacters().stream().map(ciphertext -> ciphertext.getValue()).distinct().forEach(ciphertext -> {
			// Pick a plaintext at random according to the language model
			String nextPlaintext = letterUnigramProbabilities.get(unigramRouletteSampler.getNextIndex(letterUnigramProbabilities, totalUnigramProbability)).getValue().toString();

			initialSolution.putMapping(ciphertext, new Plaintext(nextPlaintext));
		});

		plaintextEvaluator.evaluate(letterMarkovModel, initialSolution, null);

		if (useKnownEvaluator && knownPlaintextEvaluator != null) {
			initialSolution.setKnownSolutionProximity(knownPlaintextEvaluator.evaluate(initialSolution));
		}

		log.debug(initialSolution.toString());

		Double maxTemp = (double) annealingTemperatureMax;
		Double minTemp = (double) annealingTemperatureMin;
		Double iterations = (double) samplerIterations;
		Double temperature;

		CipherSolution next = initialSolution;
		CipherSolution maxBayes = initialSolution;
		int maxBayesIteration = 0;
		CipherSolution maxKnown = initialSolution;
		int maxKnownIteration = 0;

		log.info("Running sampler for " + samplerIterations + " iterations.");
		long start = System.currentTimeMillis();
		long startLetterSampling;
		long letterSamplingElapsed;
		long startWordSampling;
		long wordSamplingElapsed;

		Double knownProximity;
		int i;
		for (i = 0; i < samplerIterations; i++) {
			long iterationStart = System.currentTimeMillis();

			/*
			 * Set temperature as a ratio of the max temperature to the number of iterations left, offset by the min
			 * temperature so as not to go below it
			 */
			temperature = ((maxTemp - minTemp) * ((iterations - (double) i) / iterations)) + minTemp;

			startLetterSampling = System.currentTimeMillis();
			next = runLetterSampler(temperature, next);
			letterSamplingElapsed = (System.currentTimeMillis() - startLetterSampling);

			startWordSampling = System.currentTimeMillis();

			wordSamplingElapsed = (System.currentTimeMillis() - startWordSampling);

			if (useKnownEvaluator && knownPlaintextEvaluator != null) {
				knownProximity = knownPlaintextEvaluator.evaluate(next);
				next.setKnownSolutionProximity(knownProximity);

				if (maxKnown.getKnownSolutionProximity() < knownProximity) {
					maxKnown = next;
					maxKnownIteration = i + 1;
				}
			}

			if (maxBayes.getLogProbability().compareTo(next.getLogProbability()) < 0) {
				maxBayes = next;
				maxBayesIteration = i + 1;
			}

			log.debug("Iteration {} complete.  [elapsed={}ms, letterSampling={}ms, wordSampling={}ms, temp={}]", (i + 1), (System.currentTimeMillis() - iterationStart), letterSamplingElapsed, wordSamplingElapsed, String.format("%1$,.2f", temperature));
			log.debug(next.toString());
		}

		log.info("Letter sampling completed in " + (System.currentTimeMillis() - start) + "ms.  Average=" + ((double) (System.currentTimeMillis() - start) / (double) i) + "ms.");

		if (useKnownEvaluator && knownPlaintextEvaluator != null) {
			log.info("Best known found at iteration " + maxKnownIteration + ": " + maxKnown);
			log.info("Mappings for best known:");

			for (Map.Entry<String, Plaintext> entry : maxKnown.getMappings().entrySet()) {
				log.info(entry.getKey() + ": " + entry.getValue().getValue());
			}
		}

		log.info("Best probability found at iteration " + maxBayesIteration + ": " + maxBayes);
		log.info("Mappings for best probability:");

		for (Map.Entry<String, Plaintext> entry : maxBayes.getMappings().entrySet()) {
			log.info(entry.getKey() + ": " + entry.getValue().getValue());
		}
	}

	protected CipherSolution runLetterSampler(Double temperature, CipherSolution solution) {
		CipherSolution proposal;

		List<Map.Entry<String, Plaintext>> mappingList = new ArrayList<>();
		mappingList.addAll(solution.getMappings().entrySet());

		Map.Entry<String, Plaintext> nextEntry;

		// For each cipher symbol type, run the letter sampling
		for (int i = 0; i < solution.getMappings().size(); i++) {
			proposal = solution.clone();

			nextEntry = iterateRandomly ? mappingList.remove(ThreadLocalRandom.current().nextInt(mappingList.size())) : mappingList.get(i);

			// String letter = letterUnigramProbabilities.get(unigramRouletteSampler.getNextIndex(letterUnigramProbabilities, totalUnigramProbability)).getValue().toString();
			String letter = ModelConstants.LOWERCASE_LETTERS.get(ThreadLocalRandom.current().nextInt(ModelConstants.LOWERCASE_LETTERS.size())).toString();

			proposal.replaceMapping(nextEntry.getKey(), new Plaintext(letter));

			plaintextEvaluator.evaluate(letterMarkovModel, proposal, nextEntry.getKey());

			solution = selectNext(temperature, solution, proposal);
		}

		return solution;
	}

	protected CipherSolution selectNext(Double temperature, CipherSolution solution, CipherSolution proposal) {
		Double acceptanceProbability;

		Double solutionCoincidence = solution.computeIndexOfCoincidence();
		Double proposalCoincidence = proposal.computeIndexOfCoincidence();
		Double solutionScore = solution.getLogProbability() * Math.pow(solutionCoincidence, (1d/5d));
		Double proposalScore = proposal.getLogProbability() * Math.pow(proposalCoincidence, (1d/5d));

		if (proposalScore.compareTo(solutionScore) >= 0) {
			log.debug("Better solution found");
			return proposal;
		} else {
			// Need to convert to log probabilities in order for the acceptance probability calculation to be useful
			acceptanceProbability = Math.exp(((solutionScore - proposalScore) / temperature) * -1d);

			log.debug("Acceptance probability: {}", acceptanceProbability);

			if (acceptanceProbability < 0d) {
				throw new IllegalStateException("Acceptance probability was calculated to be less than zero.  Please review the math as this should not happen.");
			}

			if (acceptanceProbability > 1d || ThreadLocalRandom.current().nextDouble() < acceptanceProbability.doubleValue()) {
				return proposal;
			}
		}

		return solution;
	}
}
