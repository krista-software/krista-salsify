package com.krista.krista.extension.salsify.config;

import app.krista.extension.executor.Invoker;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Map;

/**
 * Dynamic attribute wrapper for Salsify extension configuration.
 * Reads org_id and api_key from the Invoker attributes at runtime.
 *
 * <p>IMPORTANT: Never log or print the API Key from this class.
 * Use {@code getOrgId()} for safe logging; treat {@code getApiKey()} as a secret.
 */
@Service
public class SalsifyAttributes {

    private static final Logger LOGGER = LoggerFactory.getLogger(SalsifyAttributes.class);

    private Map<String, Object> attributes;

    /** Loads attributes from the current invoker at injection time. */
    @Inject
    public SalsifyAttributes(Invoker invoker) {
        this.attributes = invoker.getAttributes();
        LOGGER.debug("SalsifyAttributes loaded with {} attributes", attributes.size());
    }

    /**
     * Returns the Salsify Organisation ID.
     *
     * @throws IllegalStateException if the attribute is not configured
     */
    public String getOrgId() {
        return getRequiredString(AttributeKeys.ORG_ID);
    }

    /**
     * Returns the Salsify API Key.
     * Never log this value.
     *
     * @throws IllegalStateException if the attribute is not configured
     */
    public String getApiKey() {
        return getRequiredString(AttributeKeys.API_KEY);
    }

    /**
     * Refreshes the internal attributes map when the invoker is updated.
     */
    public void update(Map<String, Object> newAttributes) {
        this.attributes = newAttributes;
    }

    private String getRequiredString(String key) {
        Object value = attributes.getOrDefault(key, null);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException(
                    "Required attribute '" + key + "' is not configured. "
                    + "Please set it in the Salsify extension Setup tab.");
        }
        return value.toString();
    }
}
