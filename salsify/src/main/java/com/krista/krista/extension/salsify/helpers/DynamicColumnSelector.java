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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.krista.krista.extension.salsify.utils.Constants;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Service for selecting and extracting dynamic columns from raw Salsify product JSON.
 *
 * <p>Supports both flat and nested Salsify response layouts.
 * Preserves native types: primitives, arrays (as Lists), and objects (as Maps).
 * Performs case-sensitive column name matching.
 */
@Service
public class DynamicColumnSelector {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicColumnSelector.class);
    private static final Gson GSON = new Gson();
    private static final String SALSIFY_URL = "salsify:url";
    private static final String SALSIFY_ID = "salsify:id";
    private static final String SALSIFY_DIGITAL_ASSETS = "salsify:digital_assets";
    private static final String IMAGE_URL_COLUMN = "Image Url";
    private static final String MAIN_IMAGE_FIELD = "Main Image";

    /**
     * Selects specified columns from a Salsify product JSON object.
     *
     * <p>Every requested column appears in the returned map. Columns that are missing
     * or null on the product are recorded as {@code null} entries so that downstream
     * consumers (e.g. Krista's table renderer) see a consistent column set across all
     * products. This matters when "Include Empty For" is in use — null-valued products
     * are part of the result set and must contribute their column key.
     *
     * @param product the raw Salsify product JSON object
     * @param columnNames ordered list of column names to extract (case-sensitive)
     * @return map with one entry per requested column; missing values appear as {@code null}
     */
    public Map<String, Object> selectColumns(JsonObject product, List<String> columnNames) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (product == null || columnNames == null || columnNames.isEmpty()) {
            return result;
        }

        for (String columnName : columnNames) {
            if (columnName == null || columnName.trim().isEmpty()) {
                continue;
            }
            columnName = columnName.trim();
            Object value;
            try {
                value = extractColumnValue(product, columnName);
            } catch (Exception e) {
                // A single malformed column on a single product shouldn't fail the whole query.
                // Surface enough context (product id + column + cause) so the operator can find the
                // offending product in Salsify, then continue with null for this cell.
                LOGGER.warn("Failed to extract column '{}' for product (salsify:id={}): {}",
                        columnName, productIdFor(product), e.getMessage());
                value = null;
            }
            result.put(columnName, value);
        }

        LOGGER.debug("Selected {} columns from product", result.size());
        return result;
    }

    /**
     * Returns the product's salsify:id if available, or "<unknown>" if not.
     * Never throws — used only for logging context.
     */
    private String productIdFor(JsonObject product) {
        try {
            if (product != null && product.has(SALSIFY_ID)) {
                JsonElement el = product.get(SALSIFY_ID);
                if (el.isJsonPrimitive()) return el.getAsString();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "<unknown>";
    }

    /**
     * Parses a comma-separated string of column names.
     *
     * @param columnsInput comma-separated column names (e.g., "Item#, Item Description, PIUPCD")
     * @return ordered list of trimmed column names, or empty list if input is null/empty
     */
    public List<String> parseColumnsString(String columnsInput) {
        List<String> columns = new ArrayList<>();

        if (columnsInput == null || columnsInput.trim().isEmpty()) {
            return columns;
        }

        String[] parts = columnsInput.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                columns.add(trimmed);
            }
        }

        LOGGER.debug("Parsed {} columns from input: {}", columns.size(), columnsInput);
        return columns;
    }

    /**
     * Extracts a column value from a Salsify product by exact column name match.
     *
     * <p>Returns native Java types:
     * <ul>
     *   <li>JsonPrimitive → String, Number, or Boolean</li>
     *   <li>JsonArray → Comma-separated String (flattened for Krista compatibility)</li>
     *   <li>JsonObject → Map&lt;String, Object&gt;</li>
     *   <li>JsonNull or missing → null</li>
     * </ul>
     *
     * Special handling for "salsify:url":
     * <ul>
     *   <li>Looks up the Main Image asset ID</li>
     *   <li>Finds matching asset in salsify:digital_assets</li>
     *   <li>Returns the salsify:url from that asset</li>
     * </ul>
     *
     * Supports both flat layout (direct properties) and nested layout (under "properties" key).
     *
     * @param product the raw Salsify product JSON object
     * @param columnName exact column name to extract (case-sensitive)
     * @return the column value with arrays flattened to comma-separated strings, or null if not found
     */
    public Object extractColumnValue(JsonObject product, String columnName) {
        if (product == null || columnName == null || columnName.isEmpty()) {
            return null;
        }

        // Special handling for salsify:url - map to Main Image asset URL
        if (SALSIFY_URL.equals(columnName)) {
            return extractSalsifyUrl(product);
        }

        // Custom column name mapping (e.g., Image Url -> salsify:url)
        if (IMAGE_URL_COLUMN.equals(columnName)) {
            return extractSalsifyUrl(product);
        }

        // For other salsify: prefixed columns (salsify:relations, salsify:digital_assets, etc.),
        // check the original product first (not filtered)
        if (columnName.startsWith(Constants.SALSIFY_SYSTEM_PREFIX)) {
            if (product.has(columnName)) {
                JsonElement element = product.get(columnName);
                return elementToObject(element);
            }
            LOGGER.debug("Column not found in product: {}", columnName);
            return null;
        }

        // Resolve attributes from product (handles both flat and nested layouts)
        JsonObject attrs = resolveAttrs(product);

        // Check if column exists
        if (!attrs.has(columnName)) {
            LOGGER.debug("Column not found in product attributes: {}", columnName);
            return null;
        }

        JsonElement element = attrs.get(columnName);
        return elementToObject(element);
    }

    /**
     * Extracts the salsify:url for the Main Image asset.
     *
     * <p>Process:
     * 1. Get the Main Image field — accepts a scalar asset ID or an array of asset IDs
     *    (some products carry multiple Main Image references).
     * 2. For each candidate ID, look up the matching asset in salsify:digital_assets.
     * 3. Return the salsify:url of the first match.
     *
     * @param product the raw Salsify product JSON object
     * @return the salsify:url string, or null if no candidate matches an asset
     */
    private Object extractSalsifyUrl(JsonObject product) {
        List<String> candidateIds = extractMainImageIds(product);
        if (candidateIds.isEmpty()) {
            return null;
        }

        if (!product.has(SALSIFY_DIGITAL_ASSETS)) {
            LOGGER.debug("salsify:digital_assets not found in product");
            return null;
        }
        JsonArray digitalAssets = product.getAsJsonArray(SALSIFY_DIGITAL_ASSETS);

        for (String candidateId : candidateIds) {
            for (JsonElement element : digitalAssets) {
                if (!element.isJsonObject()) continue;
                JsonObject asset = element.getAsJsonObject();
                if (!asset.has(SALSIFY_ID) || !asset.has(SALSIFY_URL)) continue;
                JsonElement idElement = asset.get(SALSIFY_ID);
                JsonElement urlElement = asset.get(SALSIFY_URL);
                if (!idElement.isJsonPrimitive() || !urlElement.isJsonPrimitive()) continue;
                if (candidateId.equals(idElement.getAsString())) {
                    String url = urlElement.getAsString();
                    LOGGER.debug("Found salsify:url for Main Image (id={}): {}", candidateId, url);
                    return url;
                }
            }
        }

        LOGGER.debug("No matching asset found for Main Image ID(s): {}", candidateIds);
        return null;
    }

    /**
     * Returns the Main Image asset IDs as a list. Tolerates a scalar string,
     * a JSON array of strings, or a missing/null/non-primitive value (returns empty).
     */
    private List<String> extractMainImageIds(JsonObject product) {
        if (!product.has(MAIN_IMAGE_FIELD)) {
            LOGGER.debug("Main Image field not found");
            return Collections.emptyList();
        }
        JsonElement element = product.get(MAIN_IMAGE_FIELD);
        if (element == null || element.isJsonNull()) {
            return Collections.emptyList();
        }
        if (element.isJsonPrimitive()) {
            return Collections.singletonList(element.getAsString());
        }
        if (element.isJsonArray()) {
            List<String> ids = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                if (item != null && item.isJsonPrimitive()) {
                    ids.add(item.getAsString());
                }
            }
            return ids;
        }
        LOGGER.warn("Main Image field is neither primitive nor array; skipping image lookup");
        return Collections.emptyList();
    }

    /**
     * Resolves the attribute map from a product JSON object.
     *
     * <p>Returns the nested "properties" object if present and is a JsonObject,
     * otherwise returns the product object itself (flat layout), excluding salsify: keys.
     *
     * @param product the raw product JSON object
     * @return JSON object containing the product's property key-value pairs
     */
    private JsonObject resolveAttrs(JsonObject product) {
        // Check if nested "properties" structure exists
        if (product.has(Constants.RESPONSE_KEY_PROPERTIES)
                && product.get(Constants.RESPONSE_KEY_PROPERTIES).isJsonObject()) {
            return product.getAsJsonObject(Constants.RESPONSE_KEY_PROPERTIES);
        }

        // Flat layout: build a new object excluding salsify: system keys
        JsonObject flat = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : product.entrySet()) {
            if (!entry.getKey().startsWith(Constants.SALSIFY_SYSTEM_PREFIX)) {
                flat.add(entry.getKey(), entry.getValue());
            }
        }
        return flat;
    }

    /**
     * Converts a JsonElement to a native Java object.
     *
     * <p>Conversion rules:
     * <ul>
     *   <li>JsonPrimitive → String, Number, or Boolean</li>
     *   <li>JsonArray of primitives → Comma-separated string (e.g., "27239,6667")</li>
     *   <li>JsonArray of objects → JSON string (e.g., "[{...},{...}]")</li>
     *   <li>JsonObject → JSON string (e.g., "{...}")</li>
     *   <li>JsonNull → null</li>
     * </ul>
     *
     * @param element the JSON element to convert
     * @return native Java object (String, Number, Boolean, or null)
     */
    private Object elementToObject(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        if (element.isJsonPrimitive()) {
            return primitiveToObject(element);
        }

        if (element.isJsonArray()) {
            // Check if array contains objects/nested structures
            JsonArray jsonArray = element.getAsJsonArray();
            if (containsComplexElements(jsonArray)) {
                // Return as JSON string for object arrays (e.g., salsify:relations, salsify:digital_assets)
                return jsonArray.toString();
            } else {
                // Flatten primitive arrays to comma-separated string
                return jsonArrayToString(jsonArray);
            }
        }

        if (element.isJsonObject()) {
            // Return objects as JSON string (e.g., salsify:relations, custom properties)
            return element.toString();
        }

        return null;
    }

    /**
     * Checks if a JsonArray contains complex elements (objects or arrays).
     *
     * @param jsonArray the JSON array to check
     * @return true if array contains JsonObject or JsonArray elements
     */
    private boolean containsComplexElements(JsonArray jsonArray) {
        for (JsonElement element : jsonArray) {
            if (element.isJsonObject() || element.isJsonArray()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts a JsonArray of primitives to a comma-separated string.
     *
     * <p>This is used for simple primitive arrays (e.g., "DecoPac Alternate Items": ["27239","6667"]).
     * Complex arrays (containing objects) are returned as JSON strings instead.
     *
     * @param jsonArray the JSON array of primitives
     * @return comma-separated string of array elements (e.g., "27239,6667")
     */
    private String jsonArrayToString(JsonArray jsonArray) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (JsonElement element : jsonArray) {
            if (element.isJsonNull()) {
                continue;
            }
            if (!first) {
                sb.append(",");
            }
            if (element.isJsonPrimitive()) {
                sb.append(element.getAsString());
            } else {
                // Should not reach here if containsComplexElements() is working correctly
                sb.append(element.toString());
            }
            first = false;
        }
        return sb.toString();
    }

    /**
     * Converts a JsonPrimitive to a native Java object.
     *
     * @param primitive the JSON primitive
     * @return String, Number, or Boolean
     */
    private Object primitiveToObject(JsonElement primitive) {
        if (primitive.getAsJsonPrimitive().isString()) {
            return primitive.getAsString();
        }
        if (primitive.getAsJsonPrimitive().isNumber()) {
            // Return as Number (could be int, long, float, double)
            // Let JSON parser determine the best type
            return primitive.getAsNumber();
        }
        if (primitive.getAsJsonPrimitive().isBoolean()) {
            return primitive.getAsBoolean();
        }
        return primitive.getAsString();
    }

}
