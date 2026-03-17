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
     * @param product the raw Salsify product JSON object
     * @param columnNames ordered list of column names to extract (case-sensitive)
     * @return map containing only the requested columns with native types preserved
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
            Object value = extractColumnValue(product, columnName);

            // Only include the column if a value was found (skip null/missing columns)
            if (value != null) {
                result.put(columnName, value);
            }
        }

        LOGGER.debug("Selected {} columns from product", result.size());
        return result;
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
     * 1. Get the Main Image field (contains asset ID)
     * 2. Find matching asset in salsify:digital_assets array
     * 3. Return the salsify:url from that asset
     *
     * @param product the raw Salsify product JSON object
     * @return the salsify:url string, or null if not found
     */
    private Object extractSalsifyUrl(JsonObject product) {
        if (!product.has(MAIN_IMAGE_FIELD)) {
            LOGGER.debug("Main Image field not found");
            return null;
        }

        String mainImageId = product.get(MAIN_IMAGE_FIELD).getAsString();

        if (!product.has(SALSIFY_DIGITAL_ASSETS)) {
            LOGGER.debug("salsify:digital_assets not found in product");
            return null;
        }

        JsonArray digitalAssets = product.getAsJsonArray(SALSIFY_DIGITAL_ASSETS);

        // Find the asset with matching salsify:id
        for (JsonElement element : digitalAssets) {
            if (element.isJsonObject()) {
                JsonObject asset = element.getAsJsonObject();
                if (asset.has(SALSIFY_ID) && asset.has(SALSIFY_URL)) {
                    String assetId = asset.get(SALSIFY_ID).getAsString();
                    if (assetId.equals(mainImageId)) {
                        String url = asset.get(SALSIFY_URL).getAsString();
                        LOGGER.debug("Found salsify:url for Main Image: {}", url);
                        return url;
                    }
                }
            }
        }

        LOGGER.debug("No matching asset found for Main Image ID: {}", mainImageId);
        return null;
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
