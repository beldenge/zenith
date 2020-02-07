/**
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

package com.ciphertool.zenith.inference.transformer;

import com.ciphertool.zenith.inference.entities.Cipher;
import com.ciphertool.zenith.inference.transformer.ciphertext.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TransformationManager {
    private Logger log = LoggerFactory.getLogger(getClass());

    public final static String CIPHER_TRANSFORMER_SUFFIX = CipherTransformer.class.getSimpleName();

    @Autowired
    private List<CipherTransformer> cipherTransformers;

    public Cipher transform(Cipher cipher, List<TransformationStep> steps) {
        List<CipherTransformer> toUse = new ArrayList<>(steps.size());
        List<String> existentCipherTransformers = cipherTransformers.stream()
                .map(transformer -> transformer.getClass().getSimpleName().replace(CIPHER_TRANSFORMER_SUFFIX, ""))
                .collect(Collectors.toList());

        for (TransformationStep step : steps) {
            String transformerName = step.getTransformerName();
            String argument = step.getArgument();

            if (!existentCipherTransformers.contains(transformerName)) {
                log.error("The CipherTransformer with name {} does not exist.  Please use a name from the following: {}", transformerName, existentCipherTransformers);
                throw new IllegalArgumentException("The CipherTransformer with name " + transformerName + " does not exist.");
            }

            for (CipherTransformer cipherTransformer : cipherTransformers) {
                if (cipherTransformer.getClass().getSimpleName().replace(CIPHER_TRANSFORMER_SUFFIX, "").equals(transformerName)) {
                    if (argument != null && !argument.isEmpty()) {
                        if (cipherTransformer instanceof TranspositionCipherTransformer) {
                            TranspositionCipherTransformer nextTransformer = new TranspositionCipherTransformer(argument);
                            nextTransformer.init();
                            toUse.add(nextTransformer);
                        } else if (cipherTransformer instanceof UnwrapTranspositionCipherTransformer) {
                            UnwrapTranspositionCipherTransformer nextTransformer = new UnwrapTranspositionCipherTransformer(argument);
                            nextTransformer.init();
                            toUse.add(nextTransformer);
                        } else if (cipherTransformer instanceof PeriodCipherTransformer) {
                            int period = Integer.parseInt(argument);
                            toUse.add(new PeriodCipherTransformer(period));
                        } else if (cipherTransformer instanceof UnwrapPeriodCipherTransformer) {
                            int period = Integer.parseInt(argument);
                            toUse.add(new UnwrapPeriodCipherTransformer(period));
                        } else if (cipherTransformer instanceof RemoveSymbolCipherTransformer) {
                            toUse.add(new RemoveSymbolCipherTransformer(argument));
                        } else {
                            throw new IllegalArgumentException("The CipherTransformer with name " + transformerName + " does not accept parameters.");
                        }
                    } else {
                        toUse.add(cipherTransformer);
                    }

                    break;
                }
            }
        }

        for (CipherTransformer cipherTransformer : toUse) {
            cipher = cipherTransformer.transform(cipher);
        }

        return cipher;
    }
}
