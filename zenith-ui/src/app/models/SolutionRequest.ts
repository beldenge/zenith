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

import { SolutionRequestTransformer } from "./SolutionRequestTransformer";
import { SimulatedAnnealingConfiguration } from "./SimulatedAnnealingConfiguration";
import { GeneticAlgorithmConfiguration } from "./GeneticAlgorithmConfiguration";
import { SolutionRequestFitnessFunction } from "./SolutionRequestFitnessFunction";

export class SolutionRequest {
  rows: number;
  columns: number;
  ciphertext: string;
  epochs: number;
  plaintextTransformers: SolutionRequestTransformer[] = [];
  fitnessFunction: SolutionRequestFitnessFunction;
  simulatedAnnealingConfiguration: SimulatedAnnealingConfiguration;
  geneticAlgorithmConfiguration: GeneticAlgorithmConfiguration;

  constructor(rows: number, columns: number, ciphertext: string, epochs: number) {
    this.rows = rows;
    this.columns = columns;
    this.ciphertext = ciphertext;
    this.epochs = epochs;
  }
}
