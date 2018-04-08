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

package com.ciphertool.zenith.neural.generate;

import com.ciphertool.zenith.neural.model.DataSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Validated
@ConfigurationProperties
@Profile("xor")
public class XorSampleGenerator implements SampleGenerator {
	@Min(1)
	@Value("${network.layers.input}")
	private int	inputLayerNeurons;

	@Min(1)
	@Value("${network.layers.output}")
	private int	outputLayerNeurons;

	@Override
	public DataSet generateTrainingSamples(int count) {
		return generate(count);
	}

	@Override
	public DataSet generateTrainingSample() {
		return generateOne();
	}

	@Override
	public DataSet generateTestSamples(int count) {
		return generate(count);
	}

	@Override
	public DataSet generateTestSample() {
		return generateOne();
	}

	@Override
	public void resetSamples(){
		// Nothing to do
	}

	protected DataSet generate(int count) {
		int inputLayerSize = inputLayerNeurons;
		int outputLayerSize = outputLayerNeurons;

		Float[][] inputs = new Float[count][inputLayerSize];
		Float[][] outputs = new Float[count][outputLayerSize];

		for (int i = 0; i < count; i++) {
			DataSet next = generateOne();

			inputs[i] = next.getInputs()[0];
			outputs[i] = next.getOutputs()[0];
		}

		return new DataSet(inputs, outputs);
	}

	public DataSet generateOne() {
		int inputLayerSize = inputLayerNeurons;
		int outputLayerSize = outputLayerNeurons;

		Float[][] inputs = new Float[1][inputLayerSize];
		Float[][] outputs = new Float[1][outputLayerSize];

		for (int j = 0; j < inputLayerSize; j++) {
			inputs[0][j] = (float) ThreadLocalRandom.current().nextInt(2);
		}

		outputs[0] = new Float[] { xor(inputs[0]) };

		return new DataSet(inputs, outputs);
	}

	protected Float xor(Float[] values) {
		if (values.length != 2) {
			throw new IllegalArgumentException("Exclusive or expects only two values, but found " + values.length
					+ ".  Unable to continue.");
		}

		return values[0] == values[1] ? 0.0f : 1.0f;
	}
}
