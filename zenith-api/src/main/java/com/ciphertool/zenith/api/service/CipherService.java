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

package com.ciphertool.zenith.api.service;

import com.ciphertool.zenith.api.model.*;
import com.ciphertool.zenith.inference.dao.CipherDao;
import com.ciphertool.zenith.inference.entities.Cipher;
import com.ciphertool.zenith.inference.transformer.ciphertext.CiphertextTransformationManager;
import com.ciphertool.zenith.inference.transformer.ciphertext.CiphertextTransformationStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
@RequestMapping(value = "/api/ciphers", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class CipherService {
    @Autowired
    private CipherDao cipherDao;

    @Autowired
    private CiphertextTransformationManager ciphertextTransformationManager;

    @GetMapping
    @ResponseBody
    @Cacheable("ciphers")
    public CipherResponse findCiphers() {
        CipherResponse cipherResponse = new CipherResponse();

        List<Cipher> ciphers = cipherDao.findAll();

        for (Cipher cipher : ciphers) {
            CipherResponseItem cipherResponseItem = new CipherResponseItem(cipher.getName(), cipher.getRows(), cipher.getColumns(), cipher.asSingleLineString(), cipher.isReadOnly());

            cipherResponse.getCiphers().add(cipherResponseItem);
        }

        return cipherResponse;
    }

    @PostMapping("/{cipherName}")
    @ResponseBody
    public CipherResponse transformCipher(@PathVariable String cipherName, @Validated @RequestBody CiphertextTransformationRequest transformationRequest) {
        CipherResponse cipherResponse = new CipherResponse();

        Cipher cipher = cipherDao.findByCipherName(cipherName);

        if (cipher == null) {
            throw new IllegalArgumentException("No cipher found for name " + cipherName + ".");
        }

        List<CiphertextTransformationStep> steps = transformationRequest.getSteps().stream()
                .map(CiphertextTransformationRequestStep::asStep)
                .collect(Collectors.toList());

        cipher = ciphertextTransformationManager.transform(cipher, steps);

        CipherResponseItem cipherResponseItem = new CipherResponseItem(cipher.getName(), cipher.getRows(), cipher.getColumns(), cipher.asSingleLineString(), cipher.isReadOnly());

        cipherResponse.getCiphers().add(cipherResponseItem);

        return cipherResponse;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public void createCipher(@Validated @RequestBody CipherRequest cipherRequest) throws IOException {
        Cipher cipher = cipherDao.findByCipherName(cipherRequest.getName());

        if (cipher != null) {
            throw new IllegalArgumentException("Cipher " + cipher.getName() + " already exists.");
        }

        cipherDao.writeToFile(cipherRequest.asCipher());
    }

    @PutMapping("/{cipherName}")
    @ResponseStatus(HttpStatus.OK)
    public void updateCipher(@PathVariable String cipherName, @Validated @RequestBody CipherRequest cipherRequest) throws IOException {
        Cipher cipher = cipherDao.findByCipherName(cipherName);

        if (cipher == null) {
            throw new IllegalArgumentException("Cipher " + cipher.getName() + " does not exist.");
        }

        if (!cipherName.equals(cipherRequest.getName())) {
            throw new IllegalArgumentException("The cipherName parameter '" + cipherName + "' does not match the name '" + cipherRequest.getName() + "' in the CipherRequest.");
        }

        cipherDao.writeToFile(cipherRequest.asCipher());
    }

    @DeleteMapping("/{cipherName}")
    public void deleteCipher(@PathVariable String cipherName) throws IOException {
        Cipher cipher = cipherDao.findByCipherName(cipherName);

        if (cipher == null) {
            throw new IllegalArgumentException("Cipher " + cipher.getName() + " does not exist.");
        }

        cipherDao.delete(cipherName);
    }
}
