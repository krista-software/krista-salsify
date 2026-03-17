package com.krista.krista.extension.salsify.config;

/**
 * Attribute key constants for the Salsify extension.
 * These names map directly to the @Field.Text annotations on SalsifyExtension
 * and are used when reading from the Invoker attributes map.
 */
public final class AttributeKeys {

    private AttributeKeys() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Display name and attribute key for the Salsify Organisation ID. */
    public static final String ORG_ID = "Salsify Org ID";

    /** Display name and attribute key for the Salsify API Key (secured). */
    public static final String API_KEY = "Salsify API Key";
}
