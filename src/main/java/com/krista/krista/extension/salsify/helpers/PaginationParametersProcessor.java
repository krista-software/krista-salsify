package com.krista.krista.extension.salsify.helpers;

/**
 * Helper for processing and validating pagination parameters (page size and offset).
 * This is a utility class with only static methods.
 */
public class PaginationParametersProcessor {

    private static final int MAX_OFFSET = 1_000_000;

    private PaginationParametersProcessor() {
        // Utility class should not be instantiated
    }

    /**
     * Extracts the page size from the provided value.
     * Returns null if page size is null, zero, or negative.
     *
     * @param pageSize the page size value from user input
     * @return the integer page size, or null if not applicable
     */
    public static Integer extractPageSize(Double pageSize) {
        return (pageSize != null && pageSize > 0) ? pageSize.intValue() : null;
    }

    /**
     * Extracts and validates the offset parameter.
     * Ensures offset is non-negative and within reasonable bounds.
     *
     * @param offset the offset value from user input
     * @return the integer offset, or null if offset is null (triggers automatic pagination)
     * @throws IllegalArgumentException if offset is negative or exceeds maximum allowed value
     */
    public static Integer extractOffset(Double offset) {
        if (offset == null) {
            return null;
        }

        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be non-negative");
        }

        if (offset > MAX_OFFSET) {
            throw new IllegalArgumentException("Offset exceeds maximum allowed value (" + MAX_OFFSET + ")");
        }

        return offset.intValue();
    }
}
