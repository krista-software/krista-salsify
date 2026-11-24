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

package com.krista.krista.extension.salsify.store;

import app.krista.extension.executor.Invoker;
import app.krista.extensions.util.KeyValueStore;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.krista.krista.extension.salsify.config.AttributeKeys;
import com.krista.krista.extension.salsify.utils.Constants;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralised read/write access for Salsify credentials stored in the Krista KeyValueStore.
 *
 * <p>Credentials are persisted under the key {@code {invokerId}_salsify_credentials}.
 * The API Key is never written to logs.
 */
@Service
public class CredentialStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialStore.class);
    private static final Gson GSON = new Gson();

    private final KeyValueStore keyValueStore;
    private final Invoker invoker;

    /**
     * @param keyValueStore the Krista platform KVS for persisting credentials
     * @param invoker       used to derive the per-invoker storage key
     */
    @Inject
    public CredentialStore(KeyValueStore keyValueStore, Invoker invoker) {
        this.keyValueStore = keyValueStore;
        this.invoker = invoker;
    }

    /**
     * Persists the Salsify Org ID and API Key to the KeyValueStore.
     * The API Key is stored as-is (KVS is encrypted at rest by the Krista platform).
     *
     * @param orgId  the Salsify organisation ID
     * @param apiKey the Salsify API Key — never log this value
     */
    public void saveCredentials(String orgId, String apiKey) {
        Map<String, Object> data = new HashMap<>();
        data.put(AttributeKeys.ORG_ID, orgId);
        data.put(AttributeKeys.API_KEY, apiKey);
        String key = buildKey();
        keyValueStore.put(key, GSON.toJson(data));
        LOGGER.info("Salsify credentials saved to KeyValueStore (key: {})", key);
    }

    /**
     * Retrieves the stored Salsify Organisation ID.
     *
     * @return the org ID string, or {@code null} if not stored
     */
    public String getOrgId() {
        JsonObject json = loadJson();
        if (json == null || !json.has(AttributeKeys.ORG_ID)) return null;
        return json.get(AttributeKeys.ORG_ID).getAsString();
    }

    /**
     * Retrieves the stored Salsify API Key.
     * Never log the returned value.
     *
     * @return the API Key string, or {@code null} if not stored
     */
    public String getApiKey() {
        JsonObject json = loadJson();
        if (json == null || !json.has(AttributeKeys.API_KEY)) return null;
        return json.get(AttributeKeys.API_KEY).getAsString();
    }

    /**
     * Removes the stored credentials from KeyValueStore.
     */
    public void removeCredentials() {
        String key = buildKey();
        keyValueStore.remove(key);
        LOGGER.info("Salsify credentials removed from KeyValueStore (key: {})", key);
    }

    /**
     * Loads and parses the stored credentials JSON from KeyValueStore.
     *
     * @return parsed {@link com.google.gson.JsonObject}, or {@code null} if absent or unparseable
     */
    private JsonObject loadJson() {
        String key = buildKey();
        Object obj = keyValueStore.get(key);
        if (obj == null) return null;
        try {
            return GSON.fromJson(obj.toString(), JsonObject.class);
        } catch (com.google.gson.JsonSyntaxException | IllegalStateException e) {
            LOGGER.warn("Failed to parse Salsify credentials from KeyValueStore: {}", e.getMessage());
            return null;
        }
    }

    /** Returns the KVS key for this invoker's credentials. */
    private String buildKey() {
        return invoker.getInvokerId() + Constants.CREDENTIALS_KEY_SUFFIX;
    }
}
