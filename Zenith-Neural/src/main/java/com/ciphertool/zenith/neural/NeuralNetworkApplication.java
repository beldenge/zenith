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

package com.ciphertool.zenith.neural;

import java.math.BigDecimal;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.ciphertool.zenith.neural.generate.SampleGenerator;
import com.ciphertool.zenith.neural.model.DataSet;
import com.ciphertool.zenith.neural.predict.Predictor;
import com.ciphertool.zenith.neural.train.SupervisedTrainer;

@SpringBootApplication
public class NeuralNetworkApplication implements CommandLineRunner {
	private static Logger			log							= LoggerFactory.getLogger(NeuralNetworkApplication.class);

	private static final BigDecimal	ACCEPTABLE_MARGIN_OF_ERROR	= BigDecimal.valueOf(0.01);

	@Value("${taskExecutor.poolSize.override:#{T(java.lang.Runtime).getRuntime().availableProcessors()}}")
	private int						corePoolSize;

	@Value("${taskExecutor.poolSize.override:#{T(java.lang.Runtime).getRuntime().availableProcessors()}}")
	private int						maxPoolSize;

	@Value("${taskExecutor.queueCapacity}")
	private int						queueCapacity;

	@Value("${network.trainingSamples.count}")
	private int						numberOfSamples;

	@Value("${network.testSamples.count}")
	private int						numberOfTests;

	@Autowired
	private ThreadPoolTaskExecutor	taskExecutor;

	@Autowired
	private SampleGenerator			generator;

	@Autowired
	private SupervisedTrainer		trainer;

	@Autowired
	private Predictor				predictor;

	/**
	 * Main entry point for the application.
	 * 
	 * @param args
	 *            the optional, unused command-line arguments
	 */
	public static void main(String[] args) {
		SpringApplication.run(NeuralNetworkApplication.class, args);
	}

	@Override
	public void run(String... arg0) throws Exception {
		log.info("TaskExecutor core pool size: {}", taskExecutor.getCorePoolSize());
		log.info("TaskExecutor max pool size: {}", taskExecutor.getMaxPoolSize());

		log.info("Generating " + numberOfSamples + " training samples...");
		long start = System.currentTimeMillis();
		DataSet trainingData = generator.generate(numberOfSamples);
		log.info("Finished in " + (System.currentTimeMillis() - start) + "ms.");

		log.info("Training network...");
		start = System.currentTimeMillis();
		trainer.train(trainingData.getInputs(), trainingData.getOutputs());
		log.info("Finished in " + (System.currentTimeMillis() - start) + "ms.");

		log.info("Generating " + numberOfTests + " test samples...");
		start = System.currentTimeMillis();
		DataSet testData = generator.generate(numberOfTests);
		log.info("Finished in " + (System.currentTimeMillis() - start) + "ms.");

		log.info("Testing predictions...");
		start = System.currentTimeMillis();
		BigDecimal[][] predictions = predictor.predict(testData.getInputs());
		log.info("Finished in " + (System.currentTimeMillis() - start) + "ms.");

		int correctCount = 0;
		int incorrectCount = 0;
		boolean wasIncorrect;
		for (int i = 0; i < predictions.length; i++) {
			wasIncorrect = false;

			BigDecimal[] inputs = testData.getInputs()[i];

			for (int j = 0; j < predictions[i].length; j++) {
				BigDecimal prediction = predictions[i][j];
				BigDecimal expected = testData.getOutputs()[i][j];

				log.info("Inputs: " + Arrays.asList(inputs) + ", Expected: " + expected + ", Prediction: "
						+ prediction);

				// We can't test the exact values of 1 and 0 since the output from the network is a decimal value
				if (prediction.subtract(expected).abs().compareTo(ACCEPTABLE_MARGIN_OF_ERROR) > 0) {
					incorrectCount++;

					wasIncorrect = true;
					break;
				}
			}

			if (!wasIncorrect) {
				correctCount++;
			}
		}

		log.info("Neural network achieved " + correctCount + " correct out of " + numberOfTests + " total.");
		log.info("Percentage correct: " + (int) (((double) correctCount / (double) numberOfTests) * 100));
		log.info("Percentage incorrect: " + (int) (((double) incorrectCount / (double) numberOfTests) * 100));
	}

	@Bean
	public ThreadPoolTaskExecutor taskExecutor() {
		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();

		taskExecutor.setCorePoolSize(corePoolSize);
		taskExecutor.setMaxPoolSize(maxPoolSize);
		taskExecutor.setQueueCapacity(queueCapacity);
		taskExecutor.setKeepAliveSeconds(5);
		taskExecutor.setAllowCoreThreadTimeOut(true);

		return taskExecutor;
	}
}