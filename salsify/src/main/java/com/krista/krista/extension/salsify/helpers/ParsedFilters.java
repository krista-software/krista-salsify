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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds the result of splitting user-supplied filters into:
 * <ul>
 *   <li>Server-side API shards (one or more filter strings sent to Salsify; multiple
 *       only when Primary Filters are configured for cartesian fan-out)</li>
 *   <li>Client-side OR filters applied in Java after fetching</li>
 * </ul>
 * Also carries the set of fields for which null/empty/missing product values
 * should be treated as a match (opt-in via the "Include Empty For" parameter).
 */
public final class ParsedFilters {

    /**
     * Filter strings to send to the Salsify API, one per shard.
     * <ul>
     *   <li>Empty list — no API filter; fetch the whole catalog (subject to 10K cap).</li>
     *   <li>Single element — a single AND-of-equals query (today's default path).</li>
     *   <li>Multiple elements — cartesian fan-out: one API call per shard, results
     *       aggregated. Only produced when Primary Filters is configured with
     *       multi-value entries.</li>
     * </ul>
     */
    public final List<String> apiFilterShards;

    /**
     * Filters to apply client-side after fetching.
     * Key = Salsify property code, Value = set of acceptable values.
     * An empty set means no value is acceptable — only useful in combination with
     * {@link #includeEmptyFields}, in which case only null/empty values match.
     */
    public final Map<String, Set<String>> clientSideOrFilters;

    /**
     * Fields for which null, empty, or missing product values should be treated as a match
     * during client-side filtering.
     */
    public final Set<String> includeEmptyFields;

    public ParsedFilters(List<String> apiFilterShards,
                         Map<String, Set<String>> clientSideOrFilters,
                         Set<String> includeEmptyFields) {
        this.apiFilterShards = apiFilterShards != null ? apiFilterShards : Collections.emptyList();
        this.clientSideOrFilters = clientSideOrFilters != null ? clientSideOrFilters : Collections.emptyMap();
        this.includeEmptyFields = includeEmptyFields != null ? includeEmptyFields : Collections.emptySet();
    }

    public boolean hasClientSideFilters() {
        return !clientSideOrFilters.isEmpty();
    }

    /** True when fan-out produced more than one API shard. */
    public boolean isFanOut() {
        return apiFilterShards.size() > 1;
    }
}
