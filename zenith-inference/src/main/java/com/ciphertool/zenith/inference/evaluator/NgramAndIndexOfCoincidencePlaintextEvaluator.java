/*
 * Copyright 2017-2020 George Belden
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

package com.ciphertool.zenith.inference.evaluator;

import com.ciphertool.zenith.inference.entities.Cipher;
import com.ciphertool.zenith.inference.entities.CipherSolution;
import com.ciphertool.zenith.inference.entities.FormlyForm;
import com.ciphertool.zenith.inference.evaluator.model.SolutionScore;
import com.ciphertool.zenith.inference.util.IndexOfCoincidenceEvaluator;
import com.ciphertool.zenith.inference.util.MathUtils;
import com.ciphertool.zenith.model.markov.ArrayMarkovModel;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@NoArgsConstructor
@Component
public class NgramAndIndexOfCoincidencePlaintextEvaluator extends AbstractNgramEvaluator implements PlaintextEvaluator {
    private Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    private IndexOfCoincidenceEvaluator indexOfCoincidenceEvaluator;

    public NgramAndIndexOfCoincidencePlaintextEvaluator(ArrayMarkovModel letterMarkovModel, IndexOfCoincidenceEvaluator indexOfCoincidenceEvaluator, Map<String, Object> data) {
        this.letterMarkovModel = letterMarkovModel;
        this.indexOfCoincidenceEvaluator = indexOfCoincidenceEvaluator;
        super.init();
    }

    @Override
    public SolutionScore evaluate(Map<String, Object> precomputedData, Cipher cipher, CipherSolution solution, String solutionString, String ciphertextKey) {
        long startLetter = System.currentTimeMillis();

        float[][] logProbabilitiesUpdated = evaluateLetterNGrams(cipher, solution, solutionString, ciphertextKey);

        if (log.isDebugEnabled()) {
            log.debug("Letter N-Grams took {}ms.", (System.currentTimeMillis() - startLetter));
        }

        // Scaling down the index of coincidence by its sixth root seems to be the optimal amount to penalize the sum of log probabilities by
        // This has been determined through haphazard experimentation
        float score = (solution.getLogProbability() / (float) solution.getLogProbabilities().length) * MathUtils.powSixthRoot(indexOfCoincidenceEvaluator.evaluate(precomputedData, cipher, solutionString));

        return new SolutionScore(logProbabilitiesUpdated, score);
    }

    @Override
    public Map<String, Object> getPrecomputedCounterweightData(Cipher cipher) {
        return indexOfCoincidenceEvaluator.precompute(cipher);
    }

    @Override
    public PlaintextEvaluator getInstance(Map<String, Object> data) {
        return new NgramAndIndexOfCoincidencePlaintextEvaluator(letterMarkovModel, indexOfCoincidenceEvaluator, data);
    }

    @Override
    public FormlyForm getForm() {
        return null;
    }

    @Override
    public int getOrder() {
        return 1;
    }

    @Override
    public String getHelpText() {
        return "Uses a character-level n-gram model along with calculating the index of coincidence.";
    }
}