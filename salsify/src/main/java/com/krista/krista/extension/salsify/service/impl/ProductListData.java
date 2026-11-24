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

package com.krista.krista.extension.salsify.service.impl;

import com.google.gson.JsonObject;

import java.util.List;

/**
 * Internal data transfer object carrying aggregated product list results.
 * Not a Krista entity — used only between the service and the area controller.
 */
public final class ProductListData {

    /** All products accumulated across pages. */
    public final List<JsonObject> products;

    /**
     * Ordered, deduplicated list of all property key names found across the returned products.
     * Used by the caller to generate dynamic column headers.
     */
    public final List<String> columnHeaders;

    /** The Salsify-reported total entry count from the first page meta, or the accumulated count. */
    public final int totalCount;

    /**
     * {@code true} when the catalog exceeds the large-catalog threshold and an Export Run
     * is recommended for full retrieval. When {@code true}, the products list may be truncated.
     */
    public final boolean exportRecommended;

    /**
     * @param products          aggregated product objects across all pages
     * @param columnHeaders     ordered, deduplicated property key names
     * @param totalCount        Salsify-reported total or accumulated count
     * @param exportRecommended {@code true} when the large-catalog threshold was reached
     */
    public ProductListData(List<JsonObject> products, List<String> columnHeaders,
                           int totalCount, boolean exportRecommended) {
        this.products = products;
        this.columnHeaders = columnHeaders;
        this.totalCount = totalCount;
        this.exportRecommended = exportRecommended;
    }
}
