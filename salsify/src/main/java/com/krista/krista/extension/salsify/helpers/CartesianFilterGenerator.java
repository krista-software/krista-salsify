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
import org.jvnet.hk2.annotations.Service;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Expands multi-value primary filters into the cartesian product of single-value
 * combinations. Each combination becomes one Salsify API "shard" — a clean
 * AND-of-equals predicate that Salsify's filter grammar can express
 * unambiguously, sidestepping the {@code |} / {@code ,} precedence problem
 * documented in 1.0.4.
 *
 * <p>Pure cartesian logic — no Salsify filter syntax knowledge.
 * Callers (typically {@link FilterProductsOrchestrator}) format each combination
 * into a complete API filter string.
 */
@Service
@Singleton
public class CartesianFilterGenerator {

    /**
     * Generates every combination across the given fields' value lists.
     *
     * @param primaryFieldValues ordered map of field code → non-empty value list
     * @return ordered list of combinations; each combination is a map preserving
     *         the input field order with one value chosen per field. Empty list
     *         if input is null or empty.
     * @throws IllegalArgumentException if the cartesian size exceeds
     *         {@link Constants#MAX_CARTESIAN_COMBINATIONS}, with a breakdown
     *         showing which fields contribute how many values.
     */
    public List<Map<String, String>> generate(Map<String, List<String>> primaryFieldValues) {
        if (primaryFieldValues == null || primaryFieldValues.isEmpty()) {
            return Collections.emptyList();
        }

        int cartesianSize = computeSize(primaryFieldValues);
        if (cartesianSize > Constants.MAX_CARTESIAN_COMBINATIONS) {
            throw new IllegalArgumentException(formatExceededError(primaryFieldValues, cartesianSize));
        }

        List<Map<String, String>> combinations = new ArrayList<>();
        combinations.add(new LinkedHashMap<>());

        for (Map.Entry<String, List<String>> entry : primaryFieldValues.entrySet()) {
            String field = entry.getKey();
            List<String> values = entry.getValue();
            List<Map<String, String>> next = new ArrayList<>(combinations.size() * values.size());
            for (Map<String, String> partial : combinations) {
                for (String value : values) {
                    Map<String, String> extended = new LinkedHashMap<>(partial);
                    extended.put(field, value);
                    next.add(extended);
                }
            }
            combinations = next;
        }
        return combinations;
    }

    /**
     * Computes the cartesian product size without allocating combinations.
     * Returns 1 for an empty input (the conventional empty-product identity).
     */
    public int computeSize(Map<String, List<String>> primaryFieldValues) {
        if (primaryFieldValues == null || primaryFieldValues.isEmpty()) {
            return 1;
        }
        int size = 1;
        for (List<String> values : primaryFieldValues.values()) {
            int n = values != null ? values.size() : 0;
            if (n == 0) return 0;
            size *= n;
        }
        return size;
    }

    private String formatExceededError(Map<String, List<String>> primaryFieldValues, int cartesianSize) {
        StringBuilder breakdown = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, List<String>> entry : primaryFieldValues.entrySet()) {
            if (!first) breakdown.append(", ");
            breakdown.append(entry.getKey()).append("×").append(entry.getValue().size());
            first = false;
        }
        return String.format(Constants.PRIMARY_FILTERS_CARTESIAN_EXCEEDED_TEMPLATE,
                cartesianSize, Constants.MAX_CARTESIAN_COMBINATIONS, breakdown);
    }
}
