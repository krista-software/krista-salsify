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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.krista.krista.extension.salsify.connection.SalsifyHttpClient;
import com.krista.krista.extension.salsify.service.SalsifyService;
import com.krista.krista.extension.salsify.utils.Constants;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of {@link SalsifyService}.
 *
 * <p>Pagination strategy:
 * <ol>
 *   <li>Build the first-page URL using {@link SalsifyHttpClient#buildProductsUrl}.</li>
 *   <li>Fetch each page via {@link SalsifyHttpClient#getPage}.</li>
 *   <li>Follow the {@code rel="next"} Link header until exhausted or the
 *       large-catalog threshold is reached.</li>
 *   <li>Collect union of property keys from all returned products for dynamic column headers.</li>
 * </ol>
 *
 * <p>Large-catalog handling:
 * When the accumulated product count reaches
 * {@link Constants#LARGE_CATALOG_THRESHOLD}, pagination stops and
 * {@link ProductListData#exportRecommended} is set to {@code true}.
 * The calling workflow should route to an Export Run step instead.
 */
@Service
public class SalsifyServiceImpl implements SalsifyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SalsifyServiceImpl.class);

    private final SalsifyHttpClient httpClient;

    @Inject
    public SalsifyServiceImpl(SalsifyHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public ProductListData getAllProducts(String orgId, String apiKey,
                                         List<String> filterShards, Integer pageSize, Integer offset) throws IOException {
        List<String> shards = filterShards != null ? filterShards : Collections.emptyList();
        boolean useOffset = offset != null && offset > 0;

        LOGGER.info("getAllProducts: org={}, shards={}, pageSize={}, offset={}", orgId,
                shards.isEmpty() ? "none (no API filter)" : shards.size() + " shard(s)",
                pageSize != null ? pageSize : "API default",
                useOffset ? offset : "none (full pagination)");

        if (useOffset && shards.size() > 1) {
            throw new IllegalArgumentException(Constants.OFFSET_NOT_SUPPORTED_WITH_MULTI_SHARD);
        }

        String singleShard = shards.isEmpty() ? null : shards.get(0);
        if (useOffset) {
            return getAllProductsWithOffset(orgId, apiKey, singleShard, pageSize, offset);
        }
        if (shards.size() <= 1) {
            return getAllProductsAutomatic(orgId, apiKey, singleShard, pageSize);
        }
        return aggregateShards(orgId, apiKey, shards, pageSize);
    }

    /**
     * Fans out across multiple Salsify API shards sequentially and aggregates results.
     * Each shard runs its own automatic-pagination loop, capped per-shard at
     * {@link Constants#LARGE_CATALOG_THRESHOLD}. Per the design contract
     * (primary fields are scalar, equality-only), shards are mutually disjoint —
     * no deduplication is performed.
     */
    private ProductListData aggregateShards(String orgId, String apiKey,
                                             List<String> shards, Integer pageSize) throws IOException {
        LOGGER.info("Fan-out across {} shards", shards.size());

        List<JsonObject> aggregated = new ArrayList<>();
        Set<String> headers = new LinkedHashSet<>();
        int totalCount = 0;
        boolean exportRecommended = false;

        for (int i = 0; i < shards.size(); i++) {
            LOGGER.info("Fan-out shard {}/{}: {}", i + 1, shards.size(), shards.get(i));
            ProductListData shardData = getAllProductsAutomatic(orgId, apiKey, shards.get(i), pageSize);
            aggregated.addAll(shardData.products);
            headers.addAll(shardData.columnHeaders);
            totalCount += shardData.totalCount;
            if (shardData.exportRecommended) exportRecommended = true;
        }

        LOGGER.info("Fan-out complete: {} total products from {} shards", aggregated.size(), shards.size());
        return new ProductListData(aggregated, new ArrayList<>(headers), totalCount, exportRecommended);
    }

    /**
     * Retrieves a single page of products using offset-based pagination.
     * This allows end-users to fetch specific result pages flexibly.
     */
    private ProductListData getAllProductsWithOffset(String orgId, String apiKey,
                                                    String filterString, Integer pageSize, Integer offset) throws IOException {
        LOGGER.info("Using offset-based pagination: offset={}", offset);

        List<JsonObject> products = new ArrayList<>();
        Set<String> columnHeaders = new LinkedHashSet<>();

        String url = httpClient.buildProductsUrlWithOffset(orgId, pageSize, filterString, offset);
        SalsifyHttpClient.PageResult page = httpClient.getPage(url, apiKey);

        processPageProducts(page, products, columnHeaders, 1);

        LOGGER.info("getAllProductsWithOffset complete: {} products returned, totalCount={}",
                products.size(), page.totalCount);
        return new ProductListData(products, new ArrayList<>(columnHeaders), page.totalCount, false);
    }

    /**
     * Retrieves all products using automatic pagination (Link headers or page-number pagination).
     * This is the original pagination strategy that continues until all products are fetched
     * or the large-catalog threshold is reached.
     */
    private ProductListData getAllProductsAutomatic(String orgId, String apiKey,
                                                   String filterString, Integer pageSize) throws IOException {
        LOGGER.info("Using automatic pagination (Link headers / page-number)");

        List<JsonObject> allProducts = new ArrayList<>();
        Set<String> columnHeaders = new LinkedHashSet<>();
        int totalCount = 0;
        int pageNum = 1;
        boolean exportRecommended = false;

        String url = httpClient.buildProductsUrl(orgId, pageSize, filterString);

        while (url != null) {
            SalsifyHttpClient.PageResult page = httpClient.getPage(url, apiKey);
            totalCount = Math.max(totalCount, page.totalCount);

            processPageProducts(page, allProducts, columnHeaders, pageNum);

            // Large-catalog threshold check
            if (isLargeCatalogThresholdReached(allProducts)) {
                exportRecommended = true;
                LOGGER.warn("Large-catalog threshold reached ({} products). Stopping pagination. "
                        + "Export Run recommended.", allProducts.size());
                url = null;
            } else if (page.nextUrl != null) {
                url = page.nextUrl;
                pageNum++;
            } else if (totalCount > 0 && allProducts.size() < totalCount) {
                pageNum++;
                url = httpClient.buildProductsUrl(orgId, pageSize, filterString, pageNum);
                LOGGER.info("No Link header, advancing to page {} via page-number pagination", pageNum);
            } else {
                url = null;
            }
        }

        if (totalCount == 0) {
            totalCount = allProducts.size();
        }

        LOGGER.info("getAllProductsAutomatic complete: {} products returned, exportRecommended={}",
                allProducts.size(), exportRecommended);
        return new ProductListData(allProducts, new ArrayList<>(columnHeaders), totalCount, exportRecommended);
    }

    private void processPageProducts(SalsifyHttpClient.PageResult page, List<JsonObject> allProducts,
                                     Set<String> columnHeaders, int pageNum) {
        LOGGER.info("Page {} returned {} products, totalCount={}", pageNum, page.products.size(), page.totalCount);
        for (int i = 0; i < page.products.size(); i++) {
            JsonElement element = page.products.get(i);
            if (!element.isJsonObject()) {
                LOGGER.warn("Skipping non-object element at index {}: type={}", i, element.getClass().getSimpleName());
                continue;
            }
            JsonObject product = element.getAsJsonObject();
            collectColumnHeaders(product, columnHeaders);
            allProducts.add(product);
        }
        LOGGER.info("Accumulated {} products so far", allProducts.size());
    }

    private boolean isLargeCatalogThresholdReached(List<JsonObject> allProducts) {
        return allProducts.size() >= Constants.LARGE_CATALOG_THRESHOLD;
    }

    /**
     * Collects property key names from a product JSON object into the header set.
     *
     * <p>Salsify supports two layouts:
     * <ul>
     *   <li>Nested: {@code {"salsify:id": "...", "properties": {"Status": "Active"}}}</li>
     *   <li>Flat:   {@code {"salsify:id": "...", "Status": "Active", ...}}</li>
     * </ul>
     * System keys prefixed with {@code salsify:} are excluded from the column headers.
     */
    private void collectColumnHeaders(JsonObject product, Set<String> headers) {
        // Always include salsify:id as the first column (product identifier)
        if (product.has(Constants.SALSIFY_ID_KEY)) {
            headers.add(Constants.SALSIFY_ID_KEY);
        }
        if (product.has(Constants.RESPONSE_KEY_PROPERTIES)
                && product.get(Constants.RESPONSE_KEY_PROPERTIES).isJsonObject()) {
            // Nested properties layout
            product.getAsJsonObject(Constants.RESPONSE_KEY_PROPERTIES)
                    .keySet()
                    .stream()
                    .filter(k -> !k.startsWith(Constants.SALSIFY_SYSTEM_PREFIX))
                    .forEach(headers::add);
        } else {
            // Flat layout — exclude salsify: system keys except salsify:id already added above
            product.keySet()
                    .stream()
                    .filter(k -> !k.startsWith(Constants.SALSIFY_SYSTEM_PREFIX))
                    .forEach(headers::add);
        }
    }
}
