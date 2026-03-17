package com.krista.krista.extension.salsify.helpers;

import app.krista.extension.executor.ExtensionResponse;

import java.util.Map;

/**
 * Factory for creating ExtensionResponse instances.
 * Provides convenient methods to construct ExtensionResponse with proper result handling.
 */
public class ExtensionResponseFactory {

    private ExtensionResponseFactory() {
        // Utility class
    }

    /**
     * Creates a successful ExtensionResponse with the given response values.
     *
     * @param values the response field values (e.g., "Product List", "Response Info", "Total Records")
     * @return ExtensionResponse with SUCCESS result
     */
    public static ExtensionResponse create(Map<String, Object> values) {
        return new ExtensionResponse(ExtensionResponse.Result.SUCCESS, values, null, null, null);
    }
}
