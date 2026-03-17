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
