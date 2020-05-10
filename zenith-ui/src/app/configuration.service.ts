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

import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from "rxjs";
import { SimulatedAnnealingConfiguration } from "./models/SimulatedAnnealingConfiguration";
import { GeneticAlgorithmConfiguration } from "./models/GeneticAlgorithmConfiguration";
import { SelectOption } from "./models/SelectOption";
import { ApplicationConfiguration } from "./models/ApplicationConfiguration";
import { ZenithTransformer } from "./models/ZenithTransformer";
import { DomSanitizer, SafeUrl } from "@angular/platform-browser";
import { FormGroup } from "@angular/forms";
import { LocalStorageKeys } from "./models/LocalStorageKeys";
import { ZenithFitnessFunction } from "./models/ZenithFitnessFunction";
import { FitnessFunctionService } from "./fitness-function.service";
import { PlaintextTransformerService } from "./plaintext-transformer.service";
import { CiphertextTransformerService } from "./ciphertext-transformer.service";

@Injectable({
  providedIn: 'root'
})
export class ConfigurationService {
  static readonly DEFAULT_SAMPLE_PLAINTEXT = 'thetomatoisaplantinthenightshadefamilyxxxx';

  static readonly OPTIMIZER_NAMES: SelectOption[] = [{
    name: 'SimulatedAnnealingSolutionOptimizer',
    displayName: 'Simulated Annealing'
  }, {
    name: 'GeneticAlgorithmSolutionOptimizer',
    displayName: 'Genetic Algorithm'
  }];

  static readonly POPULATION_NAMES: SelectOption[] = [{
    name: 'StandardPopulation',
    displayName: 'Standard'
  }, {
    name: 'LatticePopulation',
    displayName: 'Lattice'
  }];

  static readonly BREEDER_NAMES: SelectOption[] = [{
    name: 'RandomCipherKeyBreeder',
    displayName: 'Random'
  }, {
    name: 'ProbabilisticCipherKeyBreeder',
    displayName: 'Probabilistic'
  }];

  static readonly CROSSOVER_ALGORITHM_NAMES: SelectOption[] = [{
    name: 'GeneWiseCrossoverAlgorithm',
    displayName: 'Gene-Wise'
  }, {
    name: 'RandomSinglePointCrossoverAlgorithm',
    displayName: 'Random Single-Point'
  }];

  static readonly MUTATION_ALGORITHM_NAMES: SelectOption[] = [{
    name: 'StandardMutationAlgorithm',
    displayName: 'Standard'
  }, {
    name: 'MultipleMutationAlgorithm',
    displayName: 'Multiple'
  }, {
    name: 'MandatorySingleMutationAlgorithm',
    displayName: 'Mandatory Single'
  }];

  static readonly SELECTOR_NAMES: SelectOption[] = [{
    name: 'TournamentSelector',
    displayName: 'Tournament'
  }, {
    name: 'RouletteSelector',
    displayName: 'Roulette'
  }, {
    name: 'RandomSelector',
    displayName: 'Random'
  }];

  static readonly SIMULATED_ANNEALING_DEFAULTS: SimulatedAnnealingConfiguration = {
    samplerIterations: 5000,
    annealingTemperatureMin: 0.006,
    annealingTemperatureMax: 0.012
  };

  static readonly GENETIC_ALGORITHM_DEFAULTS: GeneticAlgorithmConfiguration = {
    populationSize: 10000,
    numberOfGenerations: 1000,
    elitism: 1,
    populationName: ConfigurationService.POPULATION_NAMES[0].name,
    latticeRows: 100,
    latticeColumns: 100,
    latticeWrapAround: true,
    latticeRadius: 1,
    breederName: ConfigurationService.BREEDER_NAMES[0].name,
    crossoverAlgorithmName: ConfigurationService.CROSSOVER_ALGORITHM_NAMES[0].name,
    mutationAlgorithmName: ConfigurationService.MUTATION_ALGORITHM_NAMES[0].name,
    mutationRate: 0.05,
    maxMutationsPerIndividual: 5,
    selectorName: ConfigurationService.SELECTOR_NAMES[0].name,
    tournamentSelectorAccuracy: 0.75,
    tournamentSize: 5
  };

  private enableTracking$ = new BehaviorSubject<boolean>(true);
  private enablePageTransitions$ = new BehaviorSubject<boolean>(true);
  private epochs$ = new BehaviorSubject<number>(1);
  private availableCiphertextTransformers$ = new BehaviorSubject<ZenithTransformer[]>([]);
  private appliedCiphertextTransformers$ = new BehaviorSubject<ZenithTransformer[]>([]);
  private availablePlaintextTransformers$ = new BehaviorSubject<ZenithTransformer[]>([]);
  private appliedPlaintextTransformers$ = new BehaviorSubject<ZenithTransformer[]>([]);
  private samplePlaintext$ = new BehaviorSubject<string>(ConfigurationService.DEFAULT_SAMPLE_PLAINTEXT);
  private selectedOptimizer$ = new BehaviorSubject<SelectOption>(ConfigurationService.OPTIMIZER_NAMES[0]);
  private availableFitnessFunctions$ = new BehaviorSubject<ZenithFitnessFunction[]>([]);
  private selectedFitnessFunction$ = new BehaviorSubject<ZenithFitnessFunction>(null);
  private simulatedAnnealingConfiguration$ = new BehaviorSubject<SimulatedAnnealingConfiguration>(ConfigurationService.SIMULATED_ANNEALING_DEFAULTS);
  private geneticAlgorithmConfiguration$ = new BehaviorSubject<GeneticAlgorithmConfiguration>(ConfigurationService.GENETIC_ALGORITHM_DEFAULTS);

  constructor(private sanitizer: DomSanitizer, private fitnessFunctionService: FitnessFunctionService, private plaintextTransformerService: PlaintextTransformerService, private ciphertextTransformerService: CiphertextTransformerService) {
    fitnessFunctionService.getFitnessFunctions().subscribe(fitnessFunctionResponse => {
      let availableFitnessFunctions = fitnessFunctionResponse.fitnessFunctions.sort((t1, t2) => {
        return t1.order - t2.order;
      });

      for (let i = 0; i < availableFitnessFunctions.length; i ++) {
        if (availableFitnessFunctions[i].form) {
          availableFitnessFunctions[i].form.form = new FormGroup({});
        }
      }

      this.updateAvailableFitnessFunctions(availableFitnessFunctions);
      this.updateSelectedFitnessFunction(availableFitnessFunctions[0]);
    });

    this.plaintextTransformerService.getTransformers().subscribe(transformerResponse => {
      let availablePlaintextTransformers = transformerResponse.transformers.sort((t1, t2) => {
        return t1.order - t2.order;
      });

      this.updateAvailablePlaintextTransformers(availablePlaintextTransformers);
    });

    this.ciphertextTransformerService.getTransformers().subscribe(transformerResponse => {
      let availableCiphertextTransformers = transformerResponse.transformers.sort((t1, t2) => {
        return t1.order - t2.order;
      });

      this.updateAvailableCiphertextTransformers(availableCiphertextTransformers);
    });
  }

  getEnableTrackingAsObservable(): Observable<boolean> {
    return this.enableTracking$.asObservable();
  }

  updateEnableTracking(enabled: boolean) {
    localStorage.setItem(LocalStorageKeys.ENABLE_TRACKING, enabled.toString());
    this.enableTracking$.next(enabled);
  }

  getEnablePageTransitionsAsObservable(): Observable<boolean> {
    return this.enablePageTransitions$.asObservable();
  }

  updateEnablePageTransitions(enabled: boolean) {
    localStorage.setItem(LocalStorageKeys.ENABLE_PAGE_TRANSITIONS, enabled.toString());
    this.enablePageTransitions$.next(enabled);
  }

  getEpochsAsObservable(): Observable<number> {
    return this.epochs$.asObservable();
  }

  updateEpochs(epochs: number) {
    this.epochs$.next(epochs);
  }

  getAvailableCiphertextTransformersAsObservable(): Observable<ZenithTransformer[]> {
    return this.availableCiphertextTransformers$.asObservable();
  }

  updateAvailableCiphertextTransformers(appliedTransformers: ZenithTransformer[]): void {
    this.availableCiphertextTransformers$.next(appliedTransformers);
  }

  getAppliedCiphertextTransformersAsObservable(): Observable<ZenithTransformer[]> {
    return this.appliedCiphertextTransformers$.asObservable();
  }

  updateAppliedCiphertextTransformers(appliedTransformers: ZenithTransformer[]): void {
    this.appliedCiphertextTransformers$.next(appliedTransformers);
  }

  getAvailablePlaintextTransformersAsObservable(): Observable<ZenithTransformer[]> {
    return this.availablePlaintextTransformers$.asObservable();
  }

  updateAvailablePlaintextTransformers(appliedTransformers: ZenithTransformer[]): void {
    this.availablePlaintextTransformers$.next(appliedTransformers);
  }

  getAppliedPlaintextTransformersAsObservable(): Observable<ZenithTransformer[]> {
    return this.appliedPlaintextTransformers$.asObservable();
  }

  updateAppliedPlaintextTransformers(appliedTransformers: ZenithTransformer[]): void {
    this.appliedPlaintextTransformers$.next(appliedTransformers);
  }

  getSamplePlaintextAsObservable(): Observable<string> {
    return this.samplePlaintext$.asObservable();
  }

  updateSamplePlaintext(appliedTransformers: string): void {
    this.samplePlaintext$.next(appliedTransformers);
  }

  getSelectedOptimizerAsObservable(): Observable<SelectOption> {
    return this.selectedOptimizer$.asObservable();
  }

  updateSelectedOptimizer(optimizer: SelectOption) {
    this.selectedOptimizer$.next(optimizer);
  }

  getAvailableFitnessFunctionsAsObservable(): Observable<ZenithFitnessFunction[]> {
    return this.availableFitnessFunctions$.asObservable();
  }

  updateAvailableFitnessFunctions(fitnessFunctions: ZenithFitnessFunction[]) {
    this.availableFitnessFunctions$.next(fitnessFunctions);
  }

  getSelectedFitnessFunctionAsObservable(): Observable<ZenithFitnessFunction> {
    return this.selectedFitnessFunction$.asObservable();
  }

  updateSelectedFitnessFunction(fitnessFunction: ZenithFitnessFunction) {
    this.selectedFitnessFunction$.next(fitnessFunction);
  }

  getSimulatedAnnealingConfigurationAsObservable(): Observable<SimulatedAnnealingConfiguration> {
    return this.simulatedAnnealingConfiguration$.asObservable();
  }

  updateSimulatedAnnealingConfiguration(configuration: SimulatedAnnealingConfiguration) {
    this.simulatedAnnealingConfiguration$.next(configuration);
  }

  getGeneticAlgorithmConfigurationAsObservable(): Observable<GeneticAlgorithmConfiguration> {
    return this.geneticAlgorithmConfiguration$.asObservable();
  }

  updateGeneticAlgorithmConfiguration(configuration: GeneticAlgorithmConfiguration) {
    this.geneticAlgorithmConfiguration$.next(configuration);
  }

  restoreGeneralSettings() {
    this.updateSelectedOptimizer(ConfigurationService.OPTIMIZER_NAMES[0]);
    this.updateSelectedFitnessFunction(this.availableFitnessFunctions$.getValue()[0]);
    this.updateSimulatedAnnealingConfiguration(ConfigurationService.SIMULATED_ANNEALING_DEFAULTS);
    this.updateGeneticAlgorithmConfiguration(ConfigurationService.GENETIC_ALGORITHM_DEFAULTS);
  }

  import(configuration: ApplicationConfiguration) {
    this.updateEpochs(configuration.epochs);

    if (configuration.appliedCiphertextTransformers) {
      configuration.appliedCiphertextTransformers.forEach(transformer => {
        if (transformer.form) {
          transformer.form.form = new FormGroup({});
        }
      });
    }

    this.updateAppliedCiphertextTransformers(configuration.appliedCiphertextTransformers);

    if (configuration.appliedPlaintextTransformers) {
      configuration.appliedPlaintextTransformers.forEach(transformer => {
        if (transformer.form) {
          transformer.form.form = new FormGroup({});
        }
      });
    }

    this.updateAppliedPlaintextTransformers(configuration.appliedPlaintextTransformers);
    this.updateSelectedOptimizer(configuration.selectedOptimizer);

    let selectedFitnessFunction = this.availableFitnessFunctions$.getValue().find(fitnessFunction =>
      fitnessFunction.name === configuration.selectedFitnessFunction.name
    );

    if (selectedFitnessFunction.form) {
      selectedFitnessFunction.form.model = configuration.selectedFitnessFunction.form.model;
      selectedFitnessFunction.form.fields = configuration.selectedFitnessFunction.form.fields;
    }

    this.updateSelectedFitnessFunction(selectedFitnessFunction);
    this.updateSimulatedAnnealingConfiguration(configuration.simulatedAnnealingConfiguration);
    this.updateGeneticAlgorithmConfiguration(configuration.geneticAlgorithmConfiguration);
  }

  getExportUri(): SafeUrl {
    let configuration = new ApplicationConfiguration();

    configuration.epochs = this.epochs$.getValue();

    if (this.appliedCiphertextTransformers$.getValue()) {
      // The FormGroup attribute is causing cyclic references when serializing to JSON, so we have to manually instantiate the transformers to skip copying the FormGroup
      configuration.appliedCiphertextTransformers = [];

      this.appliedCiphertextTransformers$.getValue().forEach(transformer => {
        configuration.appliedCiphertextTransformers.push({
          name: transformer.name,
          displayName: transformer.displayName,
          form: transformer.form ? {
            model: transformer.form.model,
            fields: transformer.form.fields
          } : null,
          order: transformer.order,
          helpText: transformer.helpText
        });
      });
    }

    if (this.appliedPlaintextTransformers$.getValue()) {
      // The FormGroup attribute is causing cyclic references when serializing to JSON, so we have to manually instantiate the transformers to skip copying the FormGroup
      configuration.appliedPlaintextTransformers = [];

      this.appliedPlaintextTransformers$.getValue().forEach(transformer => {
        configuration.appliedPlaintextTransformers.push({
          name: transformer.name,
          displayName: transformer.displayName,
          form: transformer.form ? {
            model: transformer.form.model,
            fields: transformer.form.fields
          } : null,
          order: transformer.order,
          helpText: transformer.helpText
        });
      });
    }

    let fitnessFunction = this.selectedFitnessFunction$.getValue();

    configuration.selectedFitnessFunction = {
      name: fitnessFunction.name,
      displayName: fitnessFunction.displayName,
      form: fitnessFunction.form ? {
        model: fitnessFunction.form.model,
        fields: fitnessFunction.form.fields
      } : null,
      order: fitnessFunction.order,
      helpText: fitnessFunction.helpText
    };

    configuration.selectedOptimizer = this.selectedOptimizer$.getValue();
    configuration.simulatedAnnealingConfiguration = this.simulatedAnnealingConfiguration$.getValue();
    configuration.geneticAlgorithmConfiguration = this.geneticAlgorithmConfiguration$.getValue();

    let configAsPrettyString = JSON.stringify(configuration, null, 2);
    return this.sanitizer.bypassSecurityTrustUrl('data:text/json;charset=UTF-8,' + encodeURIComponent(configAsPrettyString));
  }
}
