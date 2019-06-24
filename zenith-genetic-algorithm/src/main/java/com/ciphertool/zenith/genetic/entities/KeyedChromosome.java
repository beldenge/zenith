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

package com.ciphertool.zenith.genetic.entities;

import java.util.Map;

public interface KeyedChromosome<T> extends Chromosome {
	/**
	 * @return an unmodifiable Map of this Chromosome's Genes
	 */
	Map<T, Gene> getGenes();

	/**
	 * Adds a Gene at the specified key.
	 */
	void putGene(T key, Gene gene);

	/**
	 * Removes a Gene at the specified key.
	 */
	Gene removeGene(T key);

	/**
	 * Replaces a Gene at the specified key.
	 */
	void replaceGene(T key, Gene newGene);
}
