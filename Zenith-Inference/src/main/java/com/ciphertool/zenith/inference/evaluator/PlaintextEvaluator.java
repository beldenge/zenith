/**
 * Copyright 2017 George Belden
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ciphertool.zenith.inference.dto.EvaluationResults;
import com.ciphertool.zenith.inference.entities.CipherSolution;
import com.ciphertool.zenith.inference.probability.WordProbability;
import com.ciphertool.zenith.math.MathCache;
import com.ciphertool.zenith.math.MathConstants;
import com.ciphertool.zenith.model.ModelConstants;
import com.ciphertool.zenith.model.entities.TreeNGram;
import com.ciphertool.zenith.model.markov.TreeMarkovModel;

@Component
public class PlaintextEvaluator {
	private Logger		log	= LoggerFactory.getLogger(getClass());

	@Autowired
	private MathCache	bigDecimalFunctions;

	@Value("${markov.letter.include.word.boundaries}")
	private Boolean		includeWordBoundaries;

	public EvaluationResults evaluate(TreeMarkovModel letterMarkovModel, CipherSolution solution, String ciphertextKey) {
		BigDecimal interpolatedProbability = BigDecimal.ONE;
		BigDecimal interpolatedLogProbability = BigDecimal.ZERO;

		long startLetter = System.currentTimeMillis();
		EvaluationResults letterNGramResults = evaluateLetterNGrams(letterMarkovModel, solution, ciphertextKey);
		log.debug("Letter N-Grams took {}ms.", (System.currentTimeMillis() - startLetter));

		interpolatedProbability = letterNGramResults.getProbability();
		interpolatedLogProbability = letterNGramResults.getLogProbability();

		return new EvaluationResults(interpolatedProbability, interpolatedLogProbability);
	}

	public EvaluationResults evaluateLetterNGrams(TreeMarkovModel letterMarkovModel, CipherSolution solution, String ciphertextKey) {
		int order = letterMarkovModel.getOrder();
		List<WordProbability> words = transformToWordList(solution);

		if (words == null || words.isEmpty()) {
			throw new IllegalStateException(
					"Unable to evaluate n-grams because the list of words to concatenate is empty.");
		}

		BigDecimal probability = null;
		BigDecimal nGramProbability = BigDecimal.ONE;
		BigDecimal nGramLogProbability = BigDecimal.ZERO;
		TreeNGram match = null;

		StringBuilder sb = new StringBuilder();

		if (includeWordBoundaries) {
			sb.append(" ");
		}

		for (WordProbability word : words) {
			if (includeWordBoundaries) {
				sb.append(String.join(ModelConstants.CONNECTED_LETTERS_PLACEHOLDER_CHAR, word.getValue().split("\\.*")));
				sb.append(" ");
			} else {
				sb.append(word.getValue());
			}
		}

		if (ciphertextKey != null) {
			List<Integer> ciphertextIndices = new ArrayList<>();
			int nextIndice;
			for (int i = 0; i < solution.getCipher().getCiphertextCharacters().size(); i++) {
				nextIndice = i * (includeWordBoundaries ? 2 : 1);

				if (ciphertextKey.equals(solution.getCipher().getCiphertextCharacters().get(i).getValue())) {
					ciphertextIndices.add(nextIndice);
				}
			}

			Integer lastIndex = null;
			for (Integer ciphertextIndex : ciphertextIndices) {
				int start = Math.max(0, ciphertextIndex - (order - 1));
				int end = Math.min(sb.length() - order, ciphertextIndex + 1);

				if (lastIndex != null) {
					start = Math.max(start, lastIndex);
				}

				for (int i = start; i < end; i++) {
					match = letterMarkovModel.findExact(sb.substring(i, i + order));

					if (match != null) {
						probability = match.getProbability();
						log.debug("Letter N-Gram Match={}, Probability={}", match.getCumulativeString(), probability);
					} else {
						probability = letterMarkovModel.getUnknownLetterNGramProbability();
						log.debug("No Letter N-Gram Match");
					}

					nGramProbability = nGramProbability.multiply(probability, MathConstants.PREC_10_HALF_UP);
					nGramLogProbability = nGramLogProbability.add(bigDecimalFunctions.log(probability), MathConstants.PREC_10_HALF_UP);
				}

				lastIndex = end;
			}
		} else {
			for (int i = 0; i < sb.length() - order; i++) {
				match = letterMarkovModel.findExact(sb.substring(i, i + order));

				if (match != null) {
					probability = match.getProbability();
					log.debug("Letter N-Gram Match={}, Probability={}", match.getCumulativeString(), probability);
				} else {
					probability = letterMarkovModel.getUnknownLetterNGramProbability();
					log.debug("No Letter N-Gram Match");
				}

				nGramProbability = nGramProbability.multiply(probability, MathConstants.PREC_10_HALF_UP);
				nGramLogProbability = nGramLogProbability.add(bigDecimalFunctions.log(probability), MathConstants.PREC_10_HALF_UP);
			}
		}

		return new EvaluationResults(nGramProbability, nGramLogProbability);
	}

	protected List<WordProbability> transformToWordList(CipherSolution solution) {
		String currentSolutionString = solution.asSingleLineString().substring(0, solution.getCipher().getCiphertextCharacters().size());

		List<WordProbability> words = new ArrayList<>();
		Integer begin = null;

		for (int i = 0; i < currentSolutionString.length(); i++) {
			if (i < (currentSolutionString.length() - 1) && solution.getWordBoundaries().contains(i)) {
				words.add(new WordProbability(begin, i, currentSolutionString.substring((begin == null ? 0 : begin
						+ 1), i + 1)));

				begin = i;
			}
		}

		words.add(new WordProbability(begin, null, currentSolutionString.substring((begin == null ? 0 : begin
				+ 1), currentSolutionString.length())));

		return words;
	}
}