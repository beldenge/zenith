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

package com.ciphertool.zenith.genetic.statistics;

import lombok.*;

import java.math.BigDecimal;

@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class GenerationStatistics {
    private int generation;
    private Double bestFitness;
    private Double averageFitness;
    private BigDecimal entropy = BigDecimal.ZERO;
    private int numberOfCrossovers;
    private int numberOfMutations;
    private int numberOfEvaluations;
    private PerformanceStatistics performanceStatistics = new PerformanceStatistics();

    public GenerationStatistics(int generation) {
        this.generation = generation;
    }
}
