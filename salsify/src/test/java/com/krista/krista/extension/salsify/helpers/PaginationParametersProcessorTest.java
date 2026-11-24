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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class PaginationParametersProcessorTest {

    // ─── extractPageSize Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("extractPageSize: valid positive page size returns integer")
    void extractPageSize_validPositivePageSize_returnsInteger() {
        Integer result = PaginationParametersProcessor.extractPageSize(50.0);

        assertNotNull(result);
        assertEquals(50, result);
    }

    @Test
    @DisplayName("extractPageSize: decimal page size truncates to integer")
    void extractPageSize_decimalPageSize_truncatesToInteger() {
        Integer result = PaginationParametersProcessor.extractPageSize(50.7);

        assertNotNull(result);
        assertEquals(50, result);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -10.0})
    @DisplayName("extractPageSize: invalid page size returns null")
    void extractPageSize_invalidPageSize_returnsNull(Double invalidPageSize) {
        Integer result = PaginationParametersProcessor.extractPageSize(invalidPageSize);

        assertNull(result);
    }

    @Test
    @DisplayName("extractPageSize: null page size returns null")
    void extractPageSize_nullPageSize_returnsNull() {
        Integer result = PaginationParametersProcessor.extractPageSize(null);

        assertNull(result);
    }

    @Test
    @DisplayName("extractPageSize: large page size returns truncated integer")
    void extractPageSize_largePageSize_returnsTruncatedInteger() {
        Integer result = PaginationParametersProcessor.extractPageSize(10000.5);

        assertNotNull(result);
        assertEquals(10000, result);
    }

    // ─── extractOffset Tests ──────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
            "0.0, 0",
            "100.0, 100",
            "100.9, 100",
            "999999.0, 999999",
            "1000000.0, 1000000"
    })
    @DisplayName("extractOffset: valid offset returns integer")
    void extractOffset_validOffset_returnsInteger(Double offset, Integer expected) {
        Integer result = PaginationParametersProcessor.extractOffset(offset);

        assertNotNull(result);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("extractOffset: null offset returns null for automatic pagination")
    void extractOffset_nullOffset_returnsNull() {
        Integer result = PaginationParametersProcessor.extractOffset(null);

        assertNull(result);
    }

    @Test
    @DisplayName("extractOffset: negative offset throws IllegalArgumentException")
    void extractOffset_negativeOffset_throwsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PaginationParametersProcessor.extractOffset(-5.0)
        );
        assertTrue(exception.getMessage().contains("non-negative"));
    }

    @Test
    @DisplayName("extractOffset: offset exceeding max throws IllegalArgumentException")
    void extractOffset_offsetExceedingMax_throwsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PaginationParametersProcessor.extractOffset(1_000_001.0)
        );
        assertTrue(exception.getMessage().contains("exceeds maximum"));
    }

    @Test
    @DisplayName("extractOffset: very large offset above max throws exception")
    void extractOffset_veryLargeOffset_throwsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PaginationParametersProcessor.extractOffset(Double.MAX_VALUE)
        );
        assertTrue(exception.getMessage().contains("exceeds maximum"));
    }
}
