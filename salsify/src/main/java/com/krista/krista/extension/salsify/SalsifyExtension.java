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

package com.krista.krista.extension.salsify;

import app.krista.extension.executor.Invoker;
import app.krista.extension.impl.anno.*;
import com.krista.krista.extension.salsify.config.AttributeKeys;
import com.krista.krista.extension.salsify.config.SalsifyAttributes;
import com.krista.krista.extension.salsify.connection.SalsifyHttpClient;
import com.krista.krista.extension.salsify.store.CredentialStore;
import com.krista.krista.extension.salsify.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Main extension class for the Salsify PXM integration.
 *
 * <p>Authentication: Salsify uses an {@code Authorization: Bearer <TOKEN>} header.
 * The {@code Salsify Org ID} and {@code Salsify API Key} are collected as extension
 * attributes and persisted to the Krista KeyValueStore (encrypted at rest).
 *
 * <p><b>Security notice</b>: Never log the API Key or the full config/attributes map.
 * The {@code @Field.Text(isSecured = true)} annotation tells the Krista UI to mask
 * the value in the configuration screen.
 */
@Java(version = Java.Version.JAVA_21)
@Extension(
        version = "1.0.8",
        name = "Salsify",
        description = "Integrates with the Salsify PXM platform to synchronize product data, enabling import and management of product information from Salsify into the Krista catalog.",
        implementingDomainIds = "catEntryDomain_a0243554-f813-4b27-ab5e-132da84be905"
)
@ChangeLog(file = "docs/pages/release-notes.md")
@Field.Text(name = AttributeKeys.ORG_ID, required = true)
@Field.Text(name = AttributeKeys.API_KEY, required = true, isSecured = true)
@StaticResource(path = "docs", file = "docs")
public class SalsifyExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(SalsifyExtension.class);

    private final Invoker invoker;
    private final SalsifyAttributes decopcAttributes;
    private final SalsifyHttpClient httpClient;
    private final CredentialStore credentialStore;

    @Inject
    public SalsifyExtension(Invoker invoker,
                           SalsifyAttributes decopcAttributes,
                           SalsifyHttpClient httpClient,
                           CredentialStore credentialStore) {
        this.invoker = invoker;
        this.decopcAttributes = decopcAttributes;
        this.httpClient = httpClient;
        this.credentialStore = credentialStore;
        LOGGER.info("SalsifyExtension initialised for invoker: {}", invoker.getInvokerId());
    }

    // ─── Lifecycle Hooks ─────────────────────────────────────────────────────────
    /**
     * Validates the extension attributes before saving.
     *
     * <p>Rules:
     * <ul>
     *   <li>Both {@code Salsify Org ID} and {@code Salsify API Key} must be non-blank.</li>
     *   <li>A live GET to the products endpoint is attempted with the supplied credentials
     *       to confirm they are valid before persisting.</li>
     * </ul>
     *
     * <p><b>Security</b>: The API Key is read from the attributes map but never logged.
     * Validation errors are mapped to human-readable messages before propagation.
     */
    @InvokerRequest(InvokerRequest.Type.VALIDATE_ATTRIBUTES)
    public void validateAttributes(Map<String, Object> attributes) throws IOException {
        LOGGER.info("=== VALIDATE ATTRIBUTES ===");
        List<String> errors = new ArrayList<>();

        String orgId = (String) attributes.get(AttributeKeys.ORG_ID);
        String apiKey = (String) attributes.get(AttributeKeys.API_KEY);

        if (orgId == null || orgId.isBlank()) {
            errors.add("Salsify Org ID is required. Please provide your Salsify organisation ID.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            errors.add("Salsify API Key is required. Please provide your Salsify API Key.");
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Validation failed: " + String.join(", ", errors));
        }

        // Probe the products endpoint with a page size of 1 to confirm credentials
        validateCredentials(orgId, apiKey);
        LOGGER.info("Salsify attribute validation passed");
    }

    /**
     * Persists updated credentials to the CredentialStore whenever the invoker attributes change.
     * The attributes map is never logged.
     */
    @InvokerRequest(InvokerRequest.Type.INVOKER_UPDATED)
    public void attributesUpdated(Map<String, Object> oldAttributes,
                                   Map<String, Object> newAttributes) {
        LOGGER.info("=== INVOKER UPDATED ===");
        decopcAttributes.update(newAttributes);
        String orgId = (String) newAttributes.get(AttributeKeys.ORG_ID);
        String apiKey = (String) newAttributes.get(AttributeKeys.API_KEY);
        if (orgId != null && !orgId.isBlank() && apiKey != null && !apiKey.isBlank()) {
            credentialStore.saveCredentials(orgId, apiKey);
            LOGGER.info("Salsify credentials persisted to KeyValueStore for invoker: {}",
                    invoker.getInvokerId());
        }
    }

    /**
     * Tests the Salsify API connection using the stored credentials.
     *
     * @throws IOException if the connection test fails, with a human-readable message
     */
    @InvokerRequest(InvokerRequest.Type.TEST_CONNECTION)
    public void testConnection() throws IOException {
        LOGGER.info("=== TEST CONNECTION TO SALSIFY API ===");
        String orgId = credentialStore.getOrgId();
        String apiKey = credentialStore.getApiKey();
        if (orgId == null || orgId.isBlank() || apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(Constants.ERR_NO_CREDENTIALS);
        }
        validateCredentials(orgId, apiKey);
        LOGGER.info("=== SALSIFY CONNECTION TEST PASSED ===");
    }

    /**
     * Cleanup when the invoker is removed — deletes credentials from KeyValueStore.
     */
    @InvokerRequest(InvokerRequest.Type.INVOKER_REMOVED)
    public void invokerRemoved() {
        LOGGER.info("=== INVOKER REMOVED ===");
        credentialStore.removeCredentials();
        LOGGER.info("Salsify cleanup completed for invoker: {}", invoker.getInvokerId());
    }

    /**
     * Registers a Documentation tab pointing at the bundled static docs.
     */
    @InvokerRequest(value = InvokerRequest.Type.CUSTOM_TABS)
    public Map<String, String> customTabs() {
        return Map.of("Documentation", "static/docs");
    }

    // ─── Internal ────────────────────────────────────────────────────────────────

    /**
     * Performs a minimal API probe (1 product, no filters) to confirm the given credentials
     * are accepted by Salsify. Maps HTTP error codes to user-friendly messages.
     *
     * @param orgId  the Salsify org ID
     * @param apiKey the Salsify API Key — never log
     * @throws IOException with a human-readable message if validation fails
     */
    private void validateCredentials(String orgId, String apiKey) throws IOException {
        try {
            String probeUrl = httpClient.buildProductsUrl(orgId, 1, null);
            httpClient.getPage(probeUrl, apiKey);
            LOGGER.info("Salsify credentials validated successfully");
        } catch (SalsifyHttpClient.ClientErrorException e) {
            String userMessage = mapApiError(e.getMessage());
            LOGGER.error("Credential validation failed: {}", userMessage);
            throw new IOException(userMessage, e);
        } catch (IOException e) {
            throw new IOException(Constants.ERR_UNKNOWN + " Details: " + e.getMessage(), e);
        }
    }

    private static String mapApiError(String raw) {
        if (raw == null) return Constants.ERR_UNKNOWN;
        if (raw.contains("401") || raw.contains(Constants.ERR_INVALID_TOKEN)) return Constants.ERR_INVALID_TOKEN;
        if (raw.contains("403") || raw.contains(Constants.ERR_FORBIDDEN)) return Constants.ERR_FORBIDDEN;
        if (raw.contains("404") || raw.contains(Constants.ERR_NOT_FOUND)) return Constants.ERR_NOT_FOUND;
        return Constants.ERR_UNKNOWN + " Details: " + raw;
    }
}
