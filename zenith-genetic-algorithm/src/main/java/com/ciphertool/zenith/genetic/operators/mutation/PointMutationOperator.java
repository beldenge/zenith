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

package com.ciphertool.zenith.genetic.operators.mutation;

import com.ciphertool.zenith.genetic.GeneticAlgorithmStrategy;
import com.ciphertool.zenith.genetic.dao.GeneDao;
import com.ciphertool.zenith.genetic.entities.Chromosome;
import com.ciphertool.zenith.genetic.entities.Gene;
import com.ciphertool.zenith.genetic.entities.Genome;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class PointMutationOperator implements MutationOperator {
    @Autowired
    private GeneDao geneDao;

    @Override
    public boolean mutateChromosomes(Genome genome, GeneticAlgorithmStrategy strategy) {
        double mutationRate = strategy.getMutationRate();
        boolean mutated = false;

        for (Chromosome<Object> chromosome : genome.getChromosomes()) {
            Set<Object> keys = chromosome.getGenes().keySet();

            for (Object key : keys) {
                if (ThreadLocalRandom.current().nextDouble() <= mutationRate) {
                    Gene next = geneDao.findRandomGene(chromosome);

                    if (!next.equals(chromosome.getGenes().get(key))) {
                        mutated = true;

                        // Replace that map value with a randomly generated Gene
                        chromosome.replaceGene(key, next);
                    }
                }
            }
        }

        return mutated;
    }
}
