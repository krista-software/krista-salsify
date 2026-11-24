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

package com.krista.krista.extension.salsify.service;

import com.krista.krista.extension.salsify.service.impl.ProductListData;
import org.jvnet.hk2.annotations.Contract;

import java.io.IOException;
import java.util.List;

/**
 * Service contract for Salsify PXM API operations.
 */
@Contract
public interface SalsifyService {

    /**
     * Retrieves products from Salsify, fanning out across one or more API shards.
     *
     * <p>The {@code filterShards} list determines the strategy:
     * <ul>
     *   <li>Empty list — no API filter, fetch the catalog (subject to the 10K cap).</li>
     *   <li>Single element — one API call (today's default path).</li>
     *   <li>Multiple elements — fan-out: one sequential API call per shard, results aggregated.
     *       Generated when Primary Filters produce a cartesian product greater than 1.</li>
     * </ul>
     *
     * <p>When offset is null or 0, each shard paginates through all its pages using Link
     * header cursors (or page-number fallback), capped per shard at
     * {@link com.krista.krista.extension.salsify.utils.Constants#LARGE_CATALOG_THRESHOLD}.
     *
     * <p>When offset is provided and positive, performs offset-based pagination to fetch a single
     * page starting at the given offset. This mode requires exactly one shard — multi-shard fan-out
     * with offset-based pagination is rejected with {@link IllegalArgumentException}.
     *
     * @param orgId        the Salsify organisation ID
     * @param apiKey       the Salsify API Key — never log
     * @param filterShards list of Salsify filter strings (each starts with {@code =}); empty for no filter
     * @param pageSize     products per page, or {@code null} to let the Salsify API use its own default
     * @param offset       starting offset for pagination (0 for first page), or null/0 for automatic pagination
     * @return aggregated {@link ProductListData} across all shards
     * @throws IOException on API communication failure
     * @throws IllegalArgumentException if offset is positive and filterShards has more than one element
     */
    ProductListData getAllProducts(String orgId, String apiKey,
                                  List<String> filterShards, Integer pageSize, Integer offset) throws IOException;
}
