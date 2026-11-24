/*
 * Salsify Extension for Krista
 * Copyright (C) 2025 Krista Software
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.krista.krista.extension.salsify.helpers;

import com.krista.krista.extension.salsify.store.CredentialStore;
import com.krista.krista.extension.salsify.utils.Constants;
import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;
import java.io.IOException;

/**
 * Helper service for extracting and validating credentials from the credential store.
 */
@Service
public class CredentialExtractor {

    private final CredentialStore credentialStore;

    @Inject
    public CredentialExtractor(CredentialStore credentialStore) {
        this.credentialStore = credentialStore;
    }

    /**
     * Extracts and validates the organization ID from the credential store.
     *
     * @return the organization ID
     * @throws IOException if organization ID is missing or blank
     */
    public String extractOrgId() throws IOException {
        String orgId = credentialStore.getOrgId();
        if (orgId == null || orgId.isBlank()) {
            throw new IOException(Constants.ERR_NO_CREDENTIALS);
        }
        return orgId;
    }

    /**
     * Extracts and validates the API key from the credential store.
     *
     * @return the API key
     * @throws IOException if API key is missing or blank
     */
    public String extractApiKey() throws IOException {
        String apiKey = credentialStore.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException(Constants.ERR_NO_CREDENTIALS);
        }
        return apiKey;
    }
}
