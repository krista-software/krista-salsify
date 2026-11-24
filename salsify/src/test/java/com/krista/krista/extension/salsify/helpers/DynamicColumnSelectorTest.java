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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DynamicColumnSelectorTest {

    private DynamicColumnSelector selector;

    @BeforeEach
    void setUp() {
        selector = new DynamicColumnSelector();
    }

    // ─── parseColumnsString Tests ────────────────────────────────────────────────

    @Test
    void parseColumnsString_singleColumn_returnsOneColumn() {
        List<String> result = selector.parseColumnsString("Item#");

        assertEquals(1, result.size());
        assertEquals("Item#", result.get(0));
    }

    @Test
    void parseColumnsString_multipleColumns_returnsAllColumns() {
        List<String> result = selector.parseColumnsString("Item#,Item Description,PIUPCD");

        assertEquals(3, result.size());
        assertEquals("Item#", result.get(0));
        assertEquals("Item Description", result.get(1));
        assertEquals("PIUPCD", result.get(2));
    }

    @Test
    void parseColumnsString_columnsWithWhitespace_trimsEachColumn() {
        List<String> result = selector.parseColumnsString(" Item# , Item Description , PIUPCD ");

        assertEquals(3, result.size());
        assertEquals("Item#", result.get(0));
        assertEquals("Item Description", result.get(1));
        assertEquals("PIUPCD", result.get(2));
    }

    @Test
    void parseColumnsString_emptyString_returnsEmptyList() {
        List<String> result = selector.parseColumnsString("");

        assertTrue(result.isEmpty());
    }

    @Test
    void parseColumnsString_nullInput_returnsEmptyList() {
        List<String> result = selector.parseColumnsString(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void parseColumnsString_whitespaceOnly_returnsEmptyList() {
        List<String> result = selector.parseColumnsString("   ");

        assertTrue(result.isEmpty());
    }

    @Test
    void parseColumnsString_preservesColumnOrder() {
        List<String> result = selector.parseColumnsString("PIUPCD,Item#,Item Description");

        assertEquals(3, result.size());
        assertEquals("PIUPCD", result.get(0)); // First column
        assertEquals("Item#", result.get(1));  // Second column
        assertEquals("Item Description", result.get(2)); // Third column
    }

    // ─── extractColumnValue Tests ────────────────────────────────────────────────

    @Test
    void extractColumnValue_stringValue_returnsString() {
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");

        Object result = selector.extractColumnValue(product, "Item#");

        assertInstanceOf(String.class, result);
        assertEquals("ITEM-001", result);
    }

    @Test
    void extractColumnValue_numberValue_returnsNumber() {
        JsonObject product = new JsonObject();
        product.addProperty("PIUPCD", 123456789);

        Object result = selector.extractColumnValue(product, "PIUPCD");

        assertNotNull(result);
        // Can be Number or String depending on JSON parsing
        assertTrue(result instanceof Number || result instanceof String);
    }

    @Test
    void extractColumnValue_booleanValue_returnsBoolean() {
        JsonObject product = new JsonObject();
        product.addProperty("IsActive", true);

        Object result = selector.extractColumnValue(product, "IsActive");

        assertInstanceOf(Boolean.class, result);
        assertEquals(true, result);
    }

    @Test
    void extractColumnValue_arrayValue_returnsCommaSeparatedString() {
        JsonObject product = new JsonObject();
        JsonArray array = new JsonArray();
        array.add("ALT-001");
        array.add("ALT-002");
        product.add("DecoPac Alternate Items", array);

        Object result = selector.extractColumnValue(product, "DecoPac Alternate Items");

        // Arrays are flattened to comma-separated strings for Krista framework compatibility
        assertInstanceOf(String.class, result);
        String stringValue = (String) result;
        assertEquals("ALT-001,ALT-002", stringValue);
    }

    @Test
    void extractColumnValue_objectValue_returnsJsonString() {
        JsonObject product = new JsonObject();
        JsonObject nested = new JsonObject();
        nested.addProperty("width", "10");
        nested.addProperty("height", "5");
        product.add("Dimensions", nested);

        Object result = selector.extractColumnValue(product, "Dimensions");

        // Objects are returned as JSON strings
        assertInstanceOf(String.class, result);
        String jsonString = (String) result;
        assertTrue(jsonString.startsWith("{"));
        assertTrue(jsonString.contains("width"));
        assertTrue(jsonString.contains("10"));
        assertTrue(jsonString.contains("height"));
        assertTrue(jsonString.contains("5"));
    }

    @Test
    void extractColumnValue_nullValue_returnsNull() {
        JsonObject product = new JsonObject();
        product.add("EmptyField", com.google.gson.JsonNull.INSTANCE);

        Object result = selector.extractColumnValue(product, "EmptyField");

        assertNull(result);
    }

    @Test
    void extractColumnValue_missingColumn_returnsNull() {
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");

        Object result = selector.extractColumnValue(product, "NonExistentColumn");

        assertNull(result);
    }

    @Test
    void extractColumnValue_caseSensitiveMatch() {
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");
        product.addProperty("PIUPCD", "123456789");

        // Exact case match
        Object result1 = selector.extractColumnValue(product, "Item#");
        assertNotNull(result1);

        // Different case - should not match
        Object result2 = selector.extractColumnValue(product, "item#");
        assertNull(result2);
    }

    @Test
    void extractColumnValue_nullProduct_returnsNull() {
        Object result = selector.extractColumnValue(null, "Item#");

        assertNull(result);
    }

    @Test
    void extractColumnValue_nullColumnName_returnsNull() {
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");

        Object result = selector.extractColumnValue(product, null);

        assertNull(result);
    }

    // ─── selectColumns Tests ─────────────────────────────────────────────────────

    @Test
    void selectColumns_flatLayout_returnsOnlySelectedColumns() {
        JsonObject product = new JsonObject();
        product.addProperty("salsify:id", "ID-1");
        product.addProperty("Item#", "ITEM-001");
        product.addProperty("Item Description", "Test Product");
        product.addProperty("PIUPCD", "123456789");
        product.addProperty("PISIZE", "12 CT");

        List<String> columns = List.of("Item#", "PIUPCD");
        Map<String, Object> result = selector.selectColumns(product, columns);

        assertEquals(2, result.size());
        assertTrue(result.containsKey("Item#"));
        assertTrue(result.containsKey("PIUPCD"));
        assertFalse(result.containsKey("Item Description"));
        assertFalse(result.containsKey("PISIZE"));
        assertFalse(result.containsKey("salsify:id"));
    }

    @Test
    void selectColumns_nestedLayout_returnsOnlySelectedColumns() {
        JsonObject props = new JsonObject();
        props.addProperty("Item#", "ITEM-001");
        props.addProperty("PIUPCD", "123456789");
        props.addProperty("PISIZE", "12 CT");

        JsonObject product = new JsonObject();
        product.addProperty("salsify:id", "ID-1");
        product.add("properties", props);

        List<String> columns = List.of("Item#", "PIUPCD");
        Map<String, Object> result = selector.selectColumns(product, columns);

        assertEquals(2, result.size());
        assertTrue(result.containsKey("Item#"));
        assertTrue(result.containsKey("PIUPCD"));
        assertFalse(result.containsKey("PISIZE"));
    }

    @Test
    void selectColumns_requestedColumnNotFound_includedAsNull() {
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");
        product.addProperty("PIUPCD", "123456789");

        List<String> columns = List.of("Item#", "NonExistent");
        Map<String, Object> result = selector.selectColumns(product, columns);

        assertEquals(2, result.size());
        assertEquals("ITEM-001", result.get("Item#"));
        assertTrue(result.containsKey("NonExistent"));
        assertNull(result.get("NonExistent"));
    }

    @Test
    void selectColumns_emptyColumnsList_returnsEmptyMap() {
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");

        List<String> columns = List.of();
        Map<String, Object> result = selector.selectColumns(product, columns);

        assertTrue(result.isEmpty());
    }

    @Test
    void selectColumns_nullColumnsList_returnsEmptyMap() {
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");

        Map<String, Object> result = selector.selectColumns(product, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void selectColumns_nullProduct_returnsEmptyMap() {
        List<String> columns = List.of("Item#");

        Map<String, Object> result = selector.selectColumns(null, columns);

        assertTrue(result.isEmpty());
    }

    @Test
    void selectColumns_flattensArrayValuesToString() {
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");
        JsonArray array = new JsonArray();
        array.add("ALT-001");
        array.add("ALT-002");
        product.add("DecoPac Alternate Items", array);

        List<String> columns = List.of("Item#", "DecoPac Alternate Items");
        Map<String, Object> result = selector.selectColumns(product, columns);

        assertEquals(2, result.size());
        // Primitive arrays are flattened to comma-separated strings
        assertInstanceOf(String.class, result.get("DecoPac Alternate Items"));
        String stringValue = (String) result.get("DecoPac Alternate Items");
        assertEquals("ALT-001,ALT-002", stringValue);
    }

    @Test
    void selectColumns_objectArrayReturnsJsonString() {
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");

        // Create array of objects (e.g., salsify:relations)
        JsonArray relations = new JsonArray();
        JsonObject relation1 = new JsonObject();
        relation1.addProperty("relation_type", "Related All");
        relation1.addProperty("target_id", "36446");
        relations.add(relation1);

        JsonObject relation2 = new JsonObject();
        relation2.addProperty("relation_type", "Alternate All");
        relation2.addProperty("target_id", "6667");
        relations.add(relation2);

        product.add("salsify:relations", relations);

        List<String> columns = List.of("Item#", "salsify:relations");
        Map<String, Object> result = selector.selectColumns(product, columns);

        assertEquals(2, result.size());
        // Object arrays are returned as JSON strings
        assertInstanceOf(String.class, result.get("salsify:relations"));
        String jsonString = (String) result.get("salsify:relations");
        assertTrue(jsonString.startsWith("["));
        assertTrue(jsonString.contains("Related All"));
        assertTrue(jsonString.contains("36446"));
    }

    @Test
    void selectColumns_singleObjectReturnsJsonString() {
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");

        // Create nested object
        JsonObject dimensions = new JsonObject();
        dimensions.addProperty("width", "10");
        dimensions.addProperty("height", "5");
        product.add("ProductDimensions", dimensions);

        List<String> columns = List.of("Item#", "ProductDimensions");
        Map<String, Object> result = selector.selectColumns(product, columns);

        assertEquals(2, result.size());
        // Objects are returned as JSON strings
        assertInstanceOf(String.class, result.get("ProductDimensions"));
        String jsonString = (String) result.get("ProductDimensions");
        assertTrue(jsonString.startsWith("{"));
        assertTrue(jsonString.contains("width"));
        assertTrue(jsonString.contains("10"));
    }

    @Test
    void selectColumns_objectValuesReturnedAsJsonString() {
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");
        JsonObject nested = new JsonObject();
        nested.addProperty("width", "10");
        product.add("Dimensions", nested);

        List<String> columns = List.of("Item#", "Dimensions");
        Map<String, Object> result = selector.selectColumns(product, columns);

        assertEquals(2, result.size());
        // Objects are returned as JSON strings
        assertInstanceOf(String.class, result.get("Dimensions"));
        String jsonString = (String) result.get("Dimensions");
        assertTrue(jsonString.startsWith("{"));
        assertTrue(jsonString.contains("width"));
        assertTrue(jsonString.contains("10"));
    }

    @Test
    void selectColumns_caseSensitiveMatching() {
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");
        product.addProperty("PIUPCD", "123456789");

        // Request with exact case — value present
        List<String> columns1 = List.of("Item#");
        Map<String, Object> result1 = selector.selectColumns(product, columns1);
        assertEquals(1, result1.size());
        assertEquals("ITEM-001", result1.get("Item#"));

        // Request with different case — column key is included, but value is null
        List<String> columns2 = List.of("item#");
        Map<String, Object> result2 = selector.selectColumns(product, columns2);
        assertEquals(1, result2.size());
        assertTrue(result2.containsKey("item#"));
        assertNull(result2.get("item#"));
    }

    @Test
    void selectColumns_orderPreserved() {
        JsonObject product = new JsonObject();
        product.addProperty("PIUPCD", "123456789");
        product.addProperty("Item#", "ITEM-001");
        product.addProperty("PISIZE", "12 CT");

        // Request in specific order
        List<String> columns = List.of("PISIZE", "Item#", "PIUPCD");
        Map<String, Object> result = selector.selectColumns(product, columns);

        // LinkedHashMap should preserve insertion order
        List<String> keys = new ArrayList<>(result.keySet());
        assertEquals("PISIZE", keys.get(0));
        assertEquals("Item#", keys.get(1));
        assertEquals("PIUPCD", keys.get(2));
    }

    // ─── Integration Tests ───────────────────────────────────────────────────────

    @Test
    void parseColumnsString_thenSelectColumns_endToEnd() {
        String columnString = "Item#, PIUPCD , PISIZE";
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");
        product.addProperty("PIUPCD", "123456789");
        product.addProperty("PISIZE", "12 CT");
        product.addProperty("Item Description", "Should not be included");

        List<String> columns = selector.parseColumnsString(columnString);
        Map<String, Object> result = selector.selectColumns(product, columns);

        assertEquals(3, result.size());
        assertTrue(result.containsKey("Item#"));
        assertTrue(result.containsKey("PIUPCD"));
        assertTrue(result.containsKey("PISIZE"));
        assertFalse(result.containsKey("Item Description"));
    }

    @Test
    void selectColumns_withAllDataTypes() {
        JsonObject product = new JsonObject();
        product.addProperty("StringField", "value");
        product.addProperty("NumberField", 42);
        product.addProperty("BooleanField", true);

        JsonArray arrayField = new JsonArray();
        arrayField.add("item1");
        arrayField.add("item2");
        product.add("ArrayField", arrayField);

        JsonObject nestedObj = new JsonObject();
        nestedObj.addProperty("key", "val");
        product.add("ObjectField", nestedObj);

        List<String> columns = List.of(
                "StringField", "NumberField", "BooleanField", "ArrayField", "ObjectField");
        Map<String, Object> result = selector.selectColumns(product, columns);

        assertEquals(5, result.size());
        assertInstanceOf(String.class, result.get("StringField"));
        assertTrue(result.get("NumberField") instanceof Number || result.get("NumberField") instanceof String);
        assertInstanceOf(Boolean.class, result.get("BooleanField"));
        // Primitive arrays are flattened to comma-separated strings
        assertInstanceOf(String.class, result.get("ArrayField"));
        assertEquals("item1,item2", result.get("ArrayField"));
        // Objects are returned as JSON strings
        assertInstanceOf(String.class, result.get("ObjectField"));
        assertTrue(((String) result.get("ObjectField")).startsWith("{"));
    }

    @Test
    void selectColumns_imageUrlMapping_returnsMainImageAssetUrl() {
        // Create a product with Main Image asset ID and digital assets
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");
        product.addProperty("Main Image", "asset-id-123");

        // Create digital assets array
        JsonArray digitalAssets = new JsonArray();
        JsonObject asset1 = new JsonObject();
        asset1.addProperty("salsify:id", "asset-id-123");
        asset1.addProperty("salsify:url", "http://images.example.com/main-image.png");
        digitalAssets.add(asset1);

        JsonObject asset2 = new JsonObject();
        asset2.addProperty("salsify:id", "other-asset");
        asset2.addProperty("salsify:url", "http://images.example.com/other.png");
        digitalAssets.add(asset2);

        product.add("salsify:digital_assets", digitalAssets);

        // Request with custom column name Image Url
        List<String> columns = List.of("Item#", "Image Url");
        Map<String, Object> result = selector.selectColumns(product, columns);

        assertEquals(2, result.size());
        assertTrue(result.containsKey("Item#"));
        assertTrue(result.containsKey("Image Url"));
        assertEquals("http://images.example.com/main-image.png", result.get("Image Url"));
    }

    @Test
    void selectColumns_withRealSalsifyData_preservesAllDataTypes() {
        // Real Salsify product response with all field types
        String json = """
        {
            "salsify:id": "10165",
            "salsify:created_at": "2016-09-28T17:58:43.150Z",
            "salsify:updated_at": "2026-03-06T17:44:21.801Z",
            "salsify:version": 4231,
            "Item#": "10165",
            "Item Description": "POP TOPS-FELIZ CUMPLEANOS",
            "Main Image": "fo15h1pb7d7vhfmj72k678h10p",
            "PIDDTE": "2024-02-08",
            "PISIZE": "24/PKG",
            "PICCDE": "POPT - POP TOPS",
            "PICPR1": "6.37",
            "PICPRC": "6.69",
            "PICTRY": "CN - CHINA",
            "PIUPCD": "607772101654",
            "PIICLS": "12 - POP TOPS/LAYON",
            "PISGIG": "00 - NONE",
            "PIDISC": "Y",
            "PIREPL": "38138",
            "PINETC": "24/PKG",
            "PIMKDT": "0001-01-01",
            "PIATPD": "0001-01-01",
            "Item 1 Product Dimensions": "FELIZ CUMPLEANOS POP TOP: 4.25 x 1.3 x 0.1 inches",
            "Item 2 Product Dimensions": "SECONDARY DIMENSION",
            "Item 3 Product Dimensions": "TERTIARY DIMENSION",
            "DecoPac Alternate Items": ["27239", "6667"],
            "salsify:relations": [
                {
                    "relation_type": "Related All",
                    "salsify:target_product_id": "36446",
                    "salsify:created_at": "2019-02-21T19:25:40.979Z"
                },
                {
                    "relation_type": "Alternate All",
                    "salsify:target_product_id": "6667",
                    "salsify:created_at": "2022-03-22T03:29:42.069Z"
                }
            ],
            "salsify:digital_assets": [
                {
                    "salsify:id": "fo15h1pb7d7vhfmj72k678h10p",
                    "salsify:url": "http://images.salsify.com/image/upload/s--HY-YQ4Ws--/7e149c2535612b04d80db108a3e54dd6b265272b.png",
                    "salsify:name": "10165"
                }
            ]
        }
        """;

        JsonObject product = JsonParser.parseString(json).getAsJsonObject();

        // Request columns: exact column names provided by user
        List<String> columns = List.of(
            "Item#", "Item Description", "PISIZE", "PINETC", "PICPR1", "PICPRC", "PICTRY",
            "PIUPCD", "PICCDE", "PIICLS", "PISGIG", "PIDDTE", "PIDISC", "PIREPL",
            "DecoPac Alternate Items", "PIMKDT", "PIATPD", "Item 1 Product Dimensions",
            "Item 2 Product Dimensions", "Item 3 Product Dimensions", "Image Url"
        );

        Map<String, Object> result = selector.selectColumns(product, columns);

        // Verify all requested columns are present (21 total - PIREPL appears twice in request but map keeps it unique)
        assertEquals(21, result.size(), "Expected 21 columns but got " + result.size() + ": " + result.keySet());

        // Verify each column has correct value
        assertEquals("10165", result.get("Item#"));
        assertEquals("POP TOPS-FELIZ CUMPLEANOS", result.get("Item Description"));
        assertEquals("24/PKG", result.get("PISIZE"));
        assertEquals("24/PKG", result.get("PINETC"));
        assertEquals("6.37", result.get("PICPR1"));
        assertEquals("6.69", result.get("PICPRC"));
        assertEquals("CN - CHINA", result.get("PICTRY"));
        assertEquals("607772101654", result.get("PIUPCD"));
        assertEquals("POPT - POP TOPS", result.get("PICCDE"));
        assertEquals("12 - POP TOPS/LAYON", result.get("PIICLS"));
        assertEquals("00 - NONE", result.get("PISGIG"));
        assertEquals("2024-02-08", result.get("PIDDTE"));
        assertEquals("Y", result.get("PIDISC"));
        assertEquals("38138", result.get("PIREPL"));
        assertEquals("0001-01-01", result.get("PIMKDT"));
        assertEquals("0001-01-01", result.get("PIATPD"));
        assertEquals("FELIZ CUMPLEANOS POP TOP: 4.25 x 1.3 x 0.1 inches", result.get("Item 1 Product Dimensions"));
        assertEquals("SECONDARY DIMENSION", result.get("Item 2 Product Dimensions"));
        assertEquals("TERTIARY DIMENSION", result.get("Item 3 Product Dimensions"));

        // Array should be flattened to comma-separated string
        assertEquals("27239,6667", result.get("DecoPac Alternate Items"));

        // Image Url should map to salsify:url
        assertEquals("http://images.salsify.com/image/upload/s--HY-YQ4Ws--/7e149c2535612b04d80db108a3e54dd6b265272b.png", result.get("Image Url"));
    }

    // ─── Edge Case Tests for Coverage ────────────────────────────────────────────

    @Test
    void extractSalsifyUrl_mainImageNotFound_returnsNull() {
        JsonObject product = new JsonObject();
        // No Main Image field

        Object result = selector.extractColumnValue(product, "salsify:url");

        assertNull(result);
    }

    @Test
    void extractSalsifyUrl_digitalAssetsNotFound_returnsNull() {
        JsonObject product = new JsonObject();
        product.addProperty("Main Image", "asset-id");
        // No salsify:digital_assets field

        Object result = selector.extractColumnValue(product, "salsify:url");

        assertNull(result);
    }

    @Test
    void extractSalsifyUrl_matchingAssetNotFound_returnsNull() {
        JsonObject product = new JsonObject();
        product.addProperty("Main Image", "missing-id");

        JsonArray digitalAssets = new JsonArray();
        JsonObject asset = new JsonObject();
        asset.addProperty("salsify:id", "different-id");
        asset.addProperty("salsify:url", "http://example.com/image.png");
        digitalAssets.add(asset);

        product.add("salsify:digital_assets", digitalAssets);

        Object result = selector.extractColumnValue(product, "salsify:url");

        assertNull(result);
    }

    @Test
    void extractColumnValue_salsifyPrefixedField_returnsSalsifyValue() {
        JsonObject product = new JsonObject();
        product.addProperty("salsify:id", "test-id-123");

        Object result = selector.extractColumnValue(product, "salsify:id");

        assertEquals("test-id-123", result);
    }

    @Test
    void extractColumnValue_salsifyPrefixedFieldNotFound_returnsNull() {
        JsonObject product = new JsonObject();

        Object result = selector.extractColumnValue(product, "salsify:nonexistent");

        assertNull(result);
    }

    @Test
    void selectColumns_columnNameWithWhitespace_trimsAndMatches() {
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");

        List<String> columns = List.of("Item#   ");  // Trailing spaces
        Map<String, Object> result = selector.selectColumns(product, columns);

        // Should trim the column name and find the match
        assertTrue(result.containsKey("Item#"));
    }


    @Test
    void selectColumns_emptyColumnName_skipsIt() {
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");
        product.addProperty("PIUPCD", "123");

        List<String> columns = List.of("Item#", "", "PIUPCD");
        Map<String, Object> result = selector.selectColumns(product, columns);

        assertEquals(2, result.size());
        assertTrue(result.containsKey("Item#"));
        assertTrue(result.containsKey("PIUPCD"));
    }

    @Test
    void extractColumnValue_arrayWithMixedTypes_returnsJsonString() {
        JsonObject product = new JsonObject();
        JsonArray complexArray = new JsonArray();
        JsonObject obj = new JsonObject();
        obj.addProperty("id", "1");
        complexArray.add(obj);  // Complex element - should return as JSON string
        product.add("ComplexArray", complexArray);

        Object result = selector.extractColumnValue(product, "ComplexArray");

        // Should be returned as JSON string because array contains objects
        assertInstanceOf(String.class, result);
        String jsonString = (String) result;
        assertTrue(jsonString.startsWith("["));
    }

    @Test
    void extractColumnValue_nestedJsonObject_returnsAsJsonString() {
        JsonObject product = new JsonObject();
        JsonObject nested = new JsonObject();
        JsonObject deepNested = new JsonObject();
        deepNested.addProperty("value", "deep");
        nested.add("level2", deepNested);
        product.add("ComplexObject", nested);

        Object result = selector.extractColumnValue(product, "ComplexObject");

        // Objects should be returned as JSON string
        assertInstanceOf(String.class, result);
        String jsonString = (String) result;
        assertTrue(jsonString.startsWith("{"));
        assertTrue(jsonString.contains("level2"));
    }

    @Test
    void extractColumnValue_jsonNull_returnsNull() {
        JsonObject product = new JsonObject();
        product.add("NullField", com.google.gson.JsonNull.INSTANCE);

        Object result = selector.extractColumnValue(product, "NullField");

        assertNull(result);
    }

    @Test
    void selectColumns_withNullValues_includesNullableColumns() {
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");
        product.add("NullField", com.google.gson.JsonNull.INSTANCE);

        List<String> columns = List.of("Item#");
        Map<String, Object> result = selector.selectColumns(product, columns);

        // Only Item# should be included, NullField is null so excluded
        assertEquals(1, result.size());
        assertTrue(result.containsKey("Item#"));
    }

    @Test
    void parseColumnsString_singleColumnWithWhitespace_trimsCorrectly() {
        List<String> result = selector.parseColumnsString("  Item#  ");

        assertEquals(1, result.size());
        assertEquals("Item#", result.get(0));
    }

    @Test
    void parseColumnsString_multipleColumnsWithMixedWhitespace_trimsAll() {
        List<String> result = selector.parseColumnsString("  Col1  ,  Col2  ,  Col3  ");

        assertEquals(3, result.size());
        assertEquals("Col1", result.get(0));
        assertEquals("Col2", result.get(1));
        assertEquals("Col3", result.get(2));
    }

    @Test
    void extractColumnValue_emptyJsonObject_returnsAsJson() {
        JsonObject product = new JsonObject();
        JsonObject emptyNested = new JsonObject();
        product.add("EmptyObject", emptyNested);

        Object result = selector.extractColumnValue(product, "EmptyObject");

        assertInstanceOf(String.class, result);
        assertEquals("{}", result);
    }

    @Test
    void extractColumnValue_emptyJsonArray_returnsEmptyString() {
        JsonObject product = new JsonObject();
        JsonArray emptyArray = new JsonArray();
        product.add("EmptyArray", emptyArray);

        Object result = selector.extractColumnValue(product, "EmptyArray");

        assertInstanceOf(String.class, result);
        assertEquals("", result);
    }

    @Test
    void extractColumnValue_arrayWithNullElements_skipsNulls() {
        JsonObject product = new JsonObject();
        JsonArray arrayWithNull = new JsonArray();
        arrayWithNull.add("Value1");
        arrayWithNull.add(com.google.gson.JsonNull.INSTANCE);
        arrayWithNull.add("Value2");
        product.add("ArrayWithNull", arrayWithNull);

        Object result = selector.extractColumnValue(product, "ArrayWithNull");

        // Null elements should be skipped in array
        assertInstanceOf(String.class, result);
        String stringValue = (String) result;
        assertEquals("Value1,Value2", stringValue);
    }

    @Test
    void selectColumns_columnListWithNullElements_skipsNullColumn() {
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");
        product.addProperty("PIUPCD", "123456");

        List<String> columns = java.util.Arrays.asList("Item#", null, "PIUPCD");
        Map<String, Object> result = selector.selectColumns(product, columns);

        // Null column name should be skipped
        assertEquals(2, result.size());
        assertTrue(result.containsKey("Item#"));
        assertTrue(result.containsKey("PIUPCD"));
    }

    @Test
    void resolveAttrs_flatLayout_excludesSalsifyPrefixedKeys() {
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");
        product.addProperty("salsify:id", "id-123");
        product.addProperty("salsify:url", "http://example.com");
        product.addProperty("PIUPCD", "123456");

        List<String> columns = List.of("Item#", "PIUPCD");
        Map<String, Object> result = selector.selectColumns(product, columns);

        // Should only get non-salsify: properties
        assertEquals(2, result.size());
        assertFalse(result.containsKey("salsify:id"));
        assertFalse(result.containsKey("salsify:url"));
    }

    @Test
    void extractColumnValue_numberValue_returnsNumberType() {
        JsonObject product = new JsonObject();
        product.addProperty("Price", 19.99);

        Object result = selector.extractColumnValue(product, "Price");

        // Should return as Number type
        assertInstanceOf(Number.class, result);
    }

    @Test
    void extractColumnValue_booleanFalseValue_returnsFalse() {
        JsonObject product = new JsonObject();
        product.addProperty("IsActive", false);

        Object result = selector.extractColumnValue(product, "IsActive");

        assertInstanceOf(Boolean.class, result);
        assertEquals(false, result);
    }

    // ─── Resilience: Main Image as array, per-column extraction failures ───

    @Test
    void selectColumns_mainImageAsArray_returnsFirstMatchUrl() {
        // Production bug: some products carry Main Image as an array of asset IDs.
        // Calling getAsString() on that array used to throw "Array must have size 1, but has size 2".
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");

        JsonArray mainImage = new JsonArray();
        mainImage.add("missing-id");          // first ID has no matching asset
        mainImage.add("asset-id-123");        // second ID matches asset1 below
        product.add("Main Image", mainImage);

        JsonArray digitalAssets = new JsonArray();
        JsonObject asset1 = new JsonObject();
        asset1.addProperty("salsify:id", "asset-id-123");
        asset1.addProperty("salsify:url", "http://images.example.com/correct.png");
        digitalAssets.add(asset1);
        product.add("salsify:digital_assets", digitalAssets);

        Map<String, Object> result = selector.selectColumns(product, List.of("Item#", "Image Url"));

        assertEquals("http://images.example.com/correct.png", result.get("Image Url"));
    }

    @Test
    void selectColumns_mainImageAsArray_noMatchInAssets_returnsNull() {
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");

        JsonArray mainImage = new JsonArray();
        mainImage.add("orphan-1");
        mainImage.add("orphan-2");
        product.add("Main Image", mainImage);

        JsonArray digitalAssets = new JsonArray();
        JsonObject asset = new JsonObject();
        asset.addProperty("salsify:id", "unrelated");
        asset.addProperty("salsify:url", "http://images.example.com/unrelated.png");
        digitalAssets.add(asset);
        product.add("salsify:digital_assets", digitalAssets);

        Map<String, Object> result = selector.selectColumns(product, List.of("Item#", "Image Url"));

        assertTrue(result.containsKey("Image Url"));
        assertNull(result.get("Image Url"));
    }

    @Test
    void selectColumns_mainImageAsArrayWithNoDigitalAssets_returnsNull() {
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");

        JsonArray mainImage = new JsonArray();
        mainImage.add("id-1");
        mainImage.add("id-2");
        product.add("Main Image", mainImage);
        // no salsify:digital_assets at all

        Map<String, Object> result = selector.selectColumns(product, List.of("Item#", "Image Url"));

        assertTrue(result.containsKey("Image Url"));
        assertNull(result.get("Image Url"));
    }

    @Test
    void selectColumns_mainImageScalar_stillWorks() {
        // Backward compat: scalar Main Image must continue to behave the same way.
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ITEM-001");
        product.addProperty("Main Image", "asset-id-123");

        JsonArray digitalAssets = new JsonArray();
        JsonObject asset = new JsonObject();
        asset.addProperty("salsify:id", "asset-id-123");
        asset.addProperty("salsify:url", "http://images.example.com/scalar.png");
        digitalAssets.add(asset);
        product.add("salsify:digital_assets", digitalAssets);

        Map<String, Object> result = selector.selectColumns(product, List.of("Image Url"));

        assertEquals("http://images.example.com/scalar.png", result.get("Image Url"));
    }
}
