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

package com.ciphertool.zenith.inference.transformer.ciphertext;

import com.ciphertool.zenith.inference.entities.Cipher;
import com.ciphertool.zenith.inference.entities.Ciphertext;
import org.springframework.stereotype.Component;

@Component
public class RemoveSymbolCipherTransformer implements CipherTransformer {
    private String symbolToRemove;

    public RemoveSymbolCipherTransformer() {
    }

    public RemoveSymbolCipherTransformer(String symbolToRemove) {
        this.symbolToRemove = symbolToRemove;
    }

    @Override
    public Cipher transform(Cipher cipher) {
        Cipher transformed = cipher.clone();
        int length = cipher.length();

        // Remove the last row altogether
        for (int i = cipher.length() - 1; i >= 0; i--) {
            Ciphertext next = transformed.getCiphertextCharacters().get(i);

            if (symbolToRemove.equals(next.getValue())) {
                transformed.removeCiphertextCharacter(next);
                length --;
            }
        }

        // This transformer flattens the shape since we cannot be sure whether the resultant cipher length will be divisible by the original number of columns
        transformed.setRows(1);
        transformed.setColumns(length);

        return transformed;
    }
}