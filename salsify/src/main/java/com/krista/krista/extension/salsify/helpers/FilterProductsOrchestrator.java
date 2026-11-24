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

import app.krista.model.base.FreeForm;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.krista.krista.extension.salsify.connection.SalsifyHttpClient;
import com.krista.krista.extension.salsify.utils.Constants;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

/**
 * Orchestrates the filtering and response building for the filterProducts catalog request.
 * Handles all business logic while maintaining pure controller orchestration.
 */
@Service
@Singleton
public class FilterProductsOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilterProductsOrchestrator.class);

    private final SalsifyHttpClient httpClient;
    private final DynamicColumnSelector dynamicColumnSelector;
    private final CartesianFilterGenerator cartesianFilterGenerator;

    @Inject
    public FilterProductsOrchestrator(SalsifyHttpClient httpClient,
                                      DynamicColumnSelector dynamicColumnSelector,
                                      CartesianFilterGenerator cartesianFilterGenerator) {
        this.httpClient = httpClient;
        this.dynamicColumnSelector = dynamicColumnSelector;
        this.cartesianFilterGenerator = cartesianFilterGenerator;
    }

    /**
     * Parses primary and secondary filters into Salsify API shards and client-side OR filters.
     *
     * <p>The two filter maps describe a two-stage query:
     * <ul>
     *   <li><b>Primary Filters</b> — pushed to Salsify server-side via cartesian fan-out.
     *       Each value combination across the primary fields becomes one API shard.</li>
     *   <li><b>Secondary Filters</b> — applied to the aggregated primary result set:
     *       <ul>
     *         <li>Single-value, not in include-empty → AND-combined into the base API conditions
     *             (joins every primary shard).</li>
     *         <li>Multi-value, OR any field in include-empty → client-side OR filter.</li>
     *       </ul></li>
     *   <li>Field in include-empty but absent from secondary filters → client-side filter
     *       with empty allowed-set, matching only products where the property is null/empty.</li>
     * </ul>
     *
     * @param primaryFilters     required composite map of fields to push to Salsify; cannot be empty
     * @param secondaryFilters   optional composite map of fields applied after primary fetch
     * @param includeEmptyFields fields for which null/empty product values should also match
     * @return ParsedFilters with one or more API shards, client-side filter map, and include-empty set
     * @throws IllegalArgumentException if Primary Filters is empty, a field appears in both filter
     *         maps, a primary conflicts with include-empty, or the cartesian product exceeds the cap
     */
    public ParsedFilters parseFilters(Map<String, Object> primaryFilters,
                                       Map<String, Object> secondaryFilters,
                                       Set<String> includeEmptyFields) {
        Set<String> emptyFields = includeEmptyFields != null ? includeEmptyFields : Collections.emptySet();

        Map<String, List<String>> primaryFieldValues = extractPrimaryValues(primaryFilters, emptyFields);
        if (primaryFieldValues.isEmpty()) {
            throw new IllegalArgumentException(Constants.PRIMARY_FILTERS_REQUIRED);
        }

        validateNoOverlap(primaryFieldValues.keySet(), secondaryFilters);

        List<String> baseApiConditions = new ArrayList<>();
        Map<String, Set<String>> clientSideOrFilters = new LinkedHashMap<>();

        if (secondaryFilters != null) {
            for (Map.Entry<String, Object> entry : secondaryFilters.entrySet()) {
                processSecondaryFilterEntry(entry, emptyFields, baseApiConditions, clientSideOrFilters);
            }
        }

        // Empty-only fields: listed in includeEmptyFields but absent from secondary filters
        for (String field : emptyFields) {
            if (field != null && !field.isEmpty() && !clientSideOrFilters.containsKey(field)) {
                clientSideOrFilters.put(field, Collections.emptySet());
                LOGGER.info("Added empty-only client-side filter: field='{}'", field);
            }
        }

        List<String> apiFilterShards = buildApiFilterShards(baseApiConditions, primaryFieldValues);
        return new ParsedFilters(apiFilterShards, clientSideOrFilters, emptyFields);
    }

    private Map<String, List<String>> extractPrimaryValues(Map<String, Object> primaryFilters,
                                                            Set<String> includeEmptyFields) {
        Map<String, List<String>> primaryFieldValues = new LinkedHashMap<>();
        if (primaryFilters == null || primaryFilters.isEmpty()) {
            return primaryFieldValues;
        }
        for (Map.Entry<String, Object> entry : primaryFilters.entrySet()) {
            String field = entry.getKey();
            Object rawValue = entry.getValue();
            if (field == null || field.isEmpty() || rawValue == null) {
                LOGGER.warn("Skipping invalid primary filter: field='{}', value='{}'", field, rawValue);
                continue;
            }
            if (includeEmptyFields.contains(field)) {
                throw new IllegalArgumentException(
                        String.format(Constants.PRIMARY_AND_INCLUDE_EMPTY_CONFLICT_TEMPLATE, field));
            }
            List<String> valueParts = extractValueParts(field, rawValue);
            if (valueParts.isEmpty()) {
                LOGGER.warn("Skipping primary filter with no valid values: field='{}'", field);
                continue;
            }
            primaryFieldValues.put(field, valueParts);
            LOGGER.info("Added primary filter: field='{}', values={}", field, valueParts);
        }
        return primaryFieldValues;
    }

    private void validateNoOverlap(Set<String> primaryFields, Map<String, Object> secondaryFilters) {
        if (secondaryFilters == null || secondaryFilters.isEmpty()) return;
        for (String field : primaryFields) {
            if (secondaryFilters.containsKey(field)) {
                throw new IllegalArgumentException(
                        String.format(Constants.FIELD_IN_BOTH_FILTER_MAPS_TEMPLATE, field));
            }
        }
    }

    private void processSecondaryFilterEntry(Map.Entry<String, Object> entry,
                                               Set<String> includeEmptyFields,
                                               List<String> baseApiConditions,
                                               Map<String, Set<String>> clientSideOrFilters) {
        String field = entry.getKey();
        Object rawValue = entry.getValue();

        if (field == null || field.isEmpty() || rawValue == null) {
            LOGGER.warn("Skipping invalid secondary filter: field='{}', value='{}'", field, rawValue);
            return;
        }

        List<String> valueParts = extractValueParts(field, rawValue);
        if (valueParts.isEmpty()) {
            LOGGER.warn("Skipping secondary filter with no valid values: field='{}'", field);
            return;
        }

        boolean allowEmpty = includeEmptyFields.contains(field);
        if (valueParts.size() == 1 && !allowEmpty) {
            baseApiConditions.add(httpClient.buildFilterExpression(
                    field, Constants.OPERATOR_EQUALS, valueParts.get(0)));
            LOGGER.info("Added base API filter: field='{}', value='{}'", field, valueParts.get(0));
        } else {
            clientSideOrFilters.put(field, new LinkedHashSet<>(valueParts));
            LOGGER.info("Added client-side OR filter: field='{}', values={}, allowEmpty={}",
                    field, valueParts, allowEmpty);
        }
    }

    private List<String> buildApiFilterShards(List<String> baseApiConditions,
                                                Map<String, List<String>> primaryFieldValues) {
        if (primaryFieldValues.isEmpty()) {
            if (baseApiConditions.isEmpty()) return Collections.emptyList();
            return Collections.singletonList(httpClient.buildFilterString(baseApiConditions));
        }

        List<Map<String, String>> combinations = cartesianFilterGenerator.generate(primaryFieldValues);
        List<String> shards = new ArrayList<>(combinations.size());
        for (Map<String, String> combo : combinations) {
            List<String> conditions = new ArrayList<>(baseApiConditions);
            for (Map.Entry<String, String> e : combo.entrySet()) {
                conditions.add(httpClient.buildFilterExpression(
                        e.getKey(), Constants.OPERATOR_EQUALS, e.getValue()));
            }
            shards.add(httpClient.buildFilterString(conditions));
        }
        LOGGER.info("Generated {} API filter shard(s) from {} primary field(s)",
                shards.size(), primaryFieldValues.size());
        return shards;
    }

    private List<String> extractValueParts(String field, Object rawValue) {
        List<String> valueParts = new ArrayList<>();
        if (rawValue instanceof Collection<?>) {
            for (Object item : (Collection<?>) rawValue) {
                if (item != null) {
                    String trimmed = item.toString().trim();
                    if (!trimmed.isEmpty()) {
                        valueParts.add(trimmed);
                    }
                }
            }
        } else {
            String value = rawValue.toString();
            if (value.isEmpty()) {
                LOGGER.warn("Skipping invalid filter: field='{}', value=''", field);
                return valueParts;
            }
            for (String part : value.split(Constants.FILTER_VALUE_OR_SEPARATOR, -1)) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    valueParts.add(trimmed);
                }
            }
        }
        return valueParts;
    }

    /**
     * Applies client-side OR filters to products fetched from the API.
     *
     * <p>For each filter field, a product matches if its property value is in the allowed set,
     * or if the field is listed in {@code includeEmptyFields} and the product's value is
     * null/empty/missing. All filter fields must match (AND across fields, OR within each field).
     *
     * @param products raw products from the Salsify API
     * @param parsedFilters the parsed filter spec carrying both allowed values and include-empty fields
     * @return filtered list of products
     */
    public List<JsonObject> applyClientSideFilters(List<JsonObject> products, ParsedFilters parsedFilters) {
        if (parsedFilters == null || !parsedFilters.hasClientSideFilters()) {
            return products;
        }
        if (products == null || products.isEmpty()) {
            return products != null ? products : new ArrayList<>();
        }

        List<JsonObject> filtered = new ArrayList<>();
        for (JsonObject product : products) {
            if (matchesAllClientFilters(product, parsedFilters)) {
                filtered.add(product);
            }
        }
        LOGGER.info("Client-side filtering: {} -> {} products", products.size(), filtered.size());
        return filtered;
    }

    private boolean matchesAllClientFilters(JsonObject product, ParsedFilters parsedFilters) {
        for (Map.Entry<String, Set<String>> entry : parsedFilters.clientSideOrFilters.entrySet()) {
            String field = entry.getKey();
            String productValue = extractStringValue(product, field);
            boolean allowEmpty = parsedFilters.includeEmptyFields.contains(field);
            if (productValue == null || productValue.isEmpty()) {
                if (!allowEmpty) return false;
            } else if (!entry.getValue().contains(productValue)) {
                return false;
            }
        }
        return true;
    }

    private String extractStringValue(JsonObject product, String field) {
        if (product.has(field)) {
            JsonElement el = product.get(field);
            if (el.isJsonNull()) return null;
            if (el.isJsonPrimitive()) return el.getAsString();
            return el.toString();
        }
        return null;
    }

    /**
     * Filters products by selecting only requested columns.
     *
     * <p>Returns a list of maps where each map contains only the columns specified by the user.
     * Column names are matched exactly (case-sensitive).
     *
     * <p>Resilience contract: a malformed product (e.g. a value shape Salsify returns that the
     * extractor doesn't expect) does not fail the whole query. Per-column failures are caught
     * inside {@link DynamicColumnSelector#selectColumns}; any remaining exception that escapes
     * that boundary is caught here and the product is emitted as an all-null row, so the column
     * set stays consistent across rows.
     *
     * <p>Include-empty rendering contract: for any column listed in {@code includeEmptyFields},
     * a {@code null} cell value is substituted with the empty string {@code ""}. This is a
     * workaround for Krista's table widget dropping column headers when an include-empty query
     * pulls in a high proportion of null cells. Pre-existing empty strings are not modified.
     *
     * @param products raw Salsify product JSON objects from API
     * @param requestedColumns ordered list of column names to include in response
     * @param includeEmptyFields fields whose null cells should be substituted with "";
     *                           pass an empty set when no substitution is needed
     * @return list of maps with only requested columns; never throws on a single bad product
     */
    public List<Map<String, Object>> filterProductsByColumns(
            List<JsonObject> products,
            List<String> requestedColumns,
            Set<String> includeEmptyFields) {

        List<Map<String, Object>> result = new ArrayList<>();

        if (products == null || products.isEmpty()) {
            LOGGER.debug("No products to filter");
            return result;
        }

        if (requestedColumns == null || requestedColumns.isEmpty()) {
            LOGGER.warn("No columns requested");
            return result;
        }

        Set<String> emptyFields = includeEmptyFields != null ? includeEmptyFields : Collections.emptySet();

        for (JsonObject product : products) {
            Map<String, Object> row;
            try {
                row = dynamicColumnSelector.selectColumns(product, requestedColumns);
            } catch (Exception e) {
                LOGGER.warn("Failed to extract columns for product (salsify:id={}); returning nulls: {}",
                        productIdForLogging(product), e.getMessage());
                row = emptyColumnMap(requestedColumns);
            }
            substituteIncludeEmptyNulls(row, emptyFields);
            result.add(row);
        }

        LOGGER.info("Filtered {} products to {} columns", result.size(), requestedColumns.size());
        return result;
    }

    /**
     * For each include-empty column key in the row whose value is {@code null}, substitute
     * the empty string. Existing non-null values (including pre-existing empty strings) are
     * untouched. Column keys not in {@code includeEmptyFields} are untouched.
     *
     * <p>This sidesteps a Krista table-widget behavior that drops column headers when an
     * include-empty query pulls in a high proportion of null cells.
     */
    private void substituteIncludeEmptyNulls(Map<String, Object> row, Set<String> includeEmptyFields) {
        if (includeEmptyFields.isEmpty()) return;
        for (String field : includeEmptyFields) {
            if (field != null && row.containsKey(field) && row.get(field) == null) {
                row.put(field, "");
            }
        }
    }

    private String productIdForLogging(JsonObject product) {
        try {
            if (product != null && product.has(Constants.SALSIFY_ID_KEY)) {
                return product.get(Constants.SALSIFY_ID_KEY).getAsString();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "<unknown>";
    }

    private Map<String, Object> emptyColumnMap(List<String> requestedColumns) {
        Map<String, Object> empty = new LinkedHashMap<>();
        for (String column : requestedColumns) {
            if (column != null && !column.trim().isEmpty()) {
                empty.put(column.trim(), null);
            }
        }
        return empty;
    }

    /**
     * Builds a successful response FreeForm with status and message information.
     *
     * @param productCount number of products retrieved
     * @return FreeForm object with status and success message
     */
    public FreeForm buildSuccessResponseInfo(int productCount) {
        FreeForm responseInfo = new FreeForm();
        responseInfo.put(Constants.RESPONSE_STATUS_KEY, Constants.FREFORM_TYPE_TEXT, Constants.RESPONSE_STATUS_SUCCESS);
        responseInfo.put(Constants.RESPONSE_MESSAGE_KEY, Constants.FREFORM_TYPE_TEXT,
                String.format(Constants.RESPONSE_MESSAGE_SUCCESS_TEMPLATE.replace("{}", "%d"), productCount));
        return responseInfo;
    }

    /**
     * Builds an error response FreeForm with error status and error message.
     *
     * @param errorMessage the error message to include
     * @return FreeForm object with error status and message
     */
    public FreeForm buildErrorResponseInfo(String errorMessage) {
        FreeForm errorInfo = new FreeForm();
        errorInfo.put(Constants.RESPONSE_STATUS_KEY, Constants.FREFORM_TYPE_TEXT, Constants.RESPONSE_STATUS_ERROR);
        errorInfo.put(Constants.RESPONSE_MESSAGE_KEY, Constants.FREFORM_TYPE_TEXT, errorMessage);
        return errorInfo;
    }

    /**
     * Builds the complete response field map for successful responses.
     *
     * @param filteredProducts list of filtered products (map format)
     * @param totalRecords total number of records from API
     * @return Map with Product List, Response Info, and Total Records
     */
    public Map<String, Object> buildSuccessResponseFields(
            List<Map<String, Object>> filteredProducts,
            long totalRecords) {
        Map<String, Object> responseFields = new HashMap<>();
        responseFields.put(Constants.RESPONSE_FIELD_PRODUCT_LIST, filteredProducts);
        responseFields.put(Constants.RESPONSE_FIELD_RESPONSE_INFO, buildSuccessResponseInfo(filteredProducts.size()));
        responseFields.put(Constants.RESPONSE_FIELD_TOTAL_RECORDS, (double) totalRecords);
        return responseFields;
    }

    /**
     * Builds the complete response field map for error responses.
     *
     * @param errorMessage the error message
     * @return Map with empty Product List, Error Info, and zero Total Records
     */
    public Map<String, Object> buildErrorResponseFields(String errorMessage) {
        Map<String, Object> errorFields = new HashMap<>();
        errorFields.put(Constants.RESPONSE_FIELD_PRODUCT_LIST, new ArrayList<>());
        errorFields.put(Constants.RESPONSE_FIELD_RESPONSE_INFO, buildErrorResponseInfo(errorMessage));
        errorFields.put(Constants.RESPONSE_FIELD_TOTAL_RECORDS, 0.0);
        return errorFields;
    }
}
