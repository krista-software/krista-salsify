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
