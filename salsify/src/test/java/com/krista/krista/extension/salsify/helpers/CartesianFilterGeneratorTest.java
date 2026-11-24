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

import com.krista.krista.extension.salsify.utils.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CartesianFilterGeneratorTest {

    private CartesianFilterGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new CartesianFilterGenerator();
    }

    @Test
    void generate_nullInput_returnsEmptyList() {
        assertTrue(generator.generate(null).isEmpty());
    }

    @Test
    void generate_emptyInput_returnsEmptyList() {
        assertTrue(generator.generate(new LinkedHashMap<>()).isEmpty());
    }

    @Test
    void generate_singleField_singleValue_returnsOneCombination() {
        Map<String, List<String>> input = new LinkedHashMap<>();
        input.put("PISGRV", List.of("Baby"));

        List<Map<String, String>> result = generator.generate(input);

        assertEquals(1, result.size());
        assertEquals("Baby", result.get(0).get("PISGRV"));
    }

    @Test
    void generate_singleField_multiValue_returnsOnePerValue() {
        Map<String, List<String>> input = new LinkedHashMap<>();
        input.put("PISGRV", List.of("Baby", "Wed", "HighValue"));

        List<Map<String, String>> result = generator.generate(input);

        assertEquals(3, result.size());
        assertEquals("Baby", result.get(0).get("PISGRV"));
        assertEquals("Wed", result.get(1).get("PISGRV"));
        assertEquals("HighValue", result.get(2).get("PISGRV"));
    }

    @Test
    void generate_twoFields_multiValueEach_returnsCartesianProduct() {
        Map<String, List<String>> input = new LinkedHashMap<>();
        input.put("PISGRV", List.of("Baby", "Wed"));
        input.put("PIDISC", List.of("P", "S"));

        List<Map<String, String>> result = generator.generate(input);

        assertEquals(4, result.size());
        assertCombo(result.get(0), "PISGRV", "Baby", "PIDISC", "P");
        assertCombo(result.get(1), "PISGRV", "Baby", "PIDISC", "S");
        assertCombo(result.get(2), "PISGRV", "Wed",  "PIDISC", "P");
        assertCombo(result.get(3), "PISGRV", "Wed",  "PIDISC", "S");
    }

    @Test
    void generate_threeFields_mixedSizes_correctTotal() {
        Map<String, List<String>> input = new LinkedHashMap<>();
        input.put("F1", List.of("a", "b", "c"));   // 3
        input.put("F2", List.of("x", "y"));         // 2
        input.put("F3", List.of("1"));              // 1
        // Cartesian: 3 * 2 * 1 = 6

        List<Map<String, String>> result = generator.generate(input);

        assertEquals(6, result.size());
        // Each combination has exactly 3 fields
        for (Map<String, String> combo : result) {
            assertEquals(3, combo.size());
            assertTrue(combo.containsKey("F1"));
            assertTrue(combo.containsKey("F2"));
            assertTrue(combo.containsKey("F3"));
        }
    }

    @Test
    void generate_atCapBoundary_succeeds() {
        // 4 × 5 = 20 combinations, exactly at the cap
        Map<String, List<String>> input = new LinkedHashMap<>();
        input.put("A", List.of("1", "2", "3", "4"));
        input.put("B", List.of("v", "w", "x", "y", "z"));

        List<Map<String, String>> result = generator.generate(input);

        assertEquals(20, result.size());
    }

    @Test
    void generate_overCap_throwsWithBreakdown() {
        // 5 × 5 = 25 — over cap of 20
        Map<String, List<String>> input = new LinkedHashMap<>();
        input.put("PISGRV", List.of("a", "b", "c", "d", "e"));
        input.put("PIDISC", List.of("v", "w", "x", "y", "z"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> generator.generate(input));

        String message = ex.getMessage();
        assertTrue(message.contains("25"), "should report actual cartesian count");
        assertTrue(message.contains(String.valueOf(Constants.MAX_CARTESIAN_COMBINATIONS)),
                "should report the cap");
        assertTrue(message.contains("PISGRV×5"), "breakdown must show field × count");
        assertTrue(message.contains("PIDISC×5"), "breakdown must show all fields");
    }

    @Test
    void generate_largeCartesian_throws() {
        // 3 × 3 × 3 × 3 = 81 — well over cap
        Map<String, List<String>> input = new LinkedHashMap<>();
        input.put("F1", List.of("a", "b", "c"));
        input.put("F2", List.of("a", "b", "c"));
        input.put("F3", List.of("a", "b", "c"));
        input.put("F4", List.of("a", "b", "c"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> generator.generate(input));

        assertTrue(ex.getMessage().contains("81"));
    }

    @Test
    void computeSize_singleField() {
        Map<String, List<String>> input = new LinkedHashMap<>();
        input.put("F1", List.of("a", "b", "c"));
        assertEquals(3, generator.computeSize(input));
    }

    @Test
    void computeSize_multipleFields() {
        Map<String, List<String>> input = new LinkedHashMap<>();
        input.put("F1", List.of("a", "b"));
        input.put("F2", List.of("x", "y", "z"));
        input.put("F3", List.of("1"));
        assertEquals(6, generator.computeSize(input));
    }

    @Test
    void computeSize_emptyInput_returnsOne() {
        // Empty product convention: identity is 1
        assertEquals(1, generator.computeSize(null));
        assertEquals(1, generator.computeSize(Collections.emptyMap()));
    }

    @Test
    void generate_preservesFieldOrder() {
        // LinkedHashMap input order should be preserved in output combinations
        Map<String, List<String>> input = new LinkedHashMap<>();
        input.put("Z_FIELD", List.of("zval"));
        input.put("A_FIELD", List.of("aval"));

        List<Map<String, String>> result = generator.generate(input);

        assertEquals(1, result.size());
        // Iteration order of the inner map should be Z_FIELD first
        List<String> keys = new java.util.ArrayList<>(result.get(0).keySet());
        assertEquals("Z_FIELD", keys.get(0));
        assertEquals("A_FIELD", keys.get(1));
    }

    private static void assertCombo(Map<String, String> combo,
                                     String f1, String v1, String f2, String v2) {
        assertEquals(v1, combo.get(f1));
        assertEquals(v2, combo.get(f2));
    }
}
