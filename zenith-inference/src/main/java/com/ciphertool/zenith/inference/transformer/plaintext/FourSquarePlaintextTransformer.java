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

package com.ciphertool.zenith.inference.transformer.plaintext;

import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@NoArgsConstructor
@Component
public class FourSquarePlaintextTransformer extends AbstractFourSquarePlaintextTransformer {
    public FourSquarePlaintextTransformer(Map<String, Object> data) {
        super(data);
    }

    @Override
    public String transform(String plaintext) {
        if (plaintext.length() % 2 != 0) {
            log.debug("Plaintext length of {} is not divisible by 2.  The last character '{}' will not be transformed.",
                    plaintext.length(), plaintext.charAt(plaintext.length() - 1));
        }

        StringBuilder sb = new StringBuilder();

        // Subtracting by one takes care of both even and odd length plaintexts
        for (int i = 0; i < plaintext.length() - 1; i += 2) {
            Coordinates topLeftCoordinates = keyTopLeftMap.get(ifJThenI(plaintext.charAt(i)));
            Coordinates bottomRightCoordinates = keyBottomRightMap.get(ifJThenI(plaintext.charAt(i + 1)));

            sb.append(getCharacterAtCoordinates(keyTopRight, topLeftCoordinates.row, bottomRightCoordinates.column));
            sb.append(getCharacterAtCoordinates(keyBottomLeft,  bottomRightCoordinates.row, topLeftCoordinates.column));
        }

        return sb.toString();
    }

    @Override
    public PlaintextTransformer getInstance(Map<String, Object> data) {
        return new FourSquarePlaintextTransformer(data);
    }

    @Override
    public int getOrder() {
        return 3;
    }

    @Override
    public String getHelpText() {
        return "Performs a standard four square transformation using the specified set of keys";
    }
}
