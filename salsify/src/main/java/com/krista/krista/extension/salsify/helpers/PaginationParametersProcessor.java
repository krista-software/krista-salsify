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
