package com.krista.krista.extension.salsify.controller;

import app.krista.extension.executor.ExtensionResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.krista.krista.extension.salsify.connection.SalsifyHttpClient;
import com.krista.krista.extension.salsify.helpers.CredentialExtractor;
import com.krista.krista.extension.salsify.helpers.DynamicColumnSelector;
import com.krista.krista.extension.salsify.helpers.FilterProductsOrchestrator;
import com.krista.krista.extension.salsify.service.impl.ProductListData;
import com.krista.krista.extension.salsify.service.impl.SalsifyServiceImpl;
import com.krista.krista.extension.salsify.store.CredentialStore;
import com.krista.krista.extension.salsify.utils.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalsifyProductAreaTest {

    private static final String TEST_ORG_ID = "test-org-123";
    private static final String TEST_API_KEY = "test-api-key-456";

    @Mock private SalsifyServiceImpl salsifyService;
    @Mock private SalsifyHttpClient httpClient;
    @Mock private CredentialStore credentialStore;

    private SalsifyProductArea area;

    @BeforeEach
    void setUp() {
        CredentialExtractor credentialExtractor = new CredentialExtractor(credentialStore);
        DynamicColumnSelector dynamicColumnSelector = new DynamicColumnSelector();
        FilterProductsOrchestrator filterProductsOrchestrator =
                new FilterProductsOrchestrator(httpClient, dynamicColumnSelector);

        area = new SalsifyProductArea(salsifyService,
                credentialExtractor, dynamicColumnSelector, filterProductsOrchestrator);
    }

    /**
     * Extracts the product list from an ExtensionResponse.
     * Asserts that the response is successful.
     */
    private List<Map<String, Object>> extractProductList(ExtensionResponse response) {
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult(), "Response should be successful");
        assertNotNull(response.getResponseValue(), "Response value should not be null");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) response.getResponseValue()
                .get(Constants.RESPONSE_FIELD_PRODUCT_LIST);
        return result;
    }

    // ─── Dynamic Filter Tests ────────────────────────────────────────────────────

    @Test
    void filterProducts_singleDynamicFilter_buildsCorrectExpression() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);
        when(httpClient.buildFilterExpression("PISGRV", Constants.OPERATOR_EQUALS, "High Value"))
                .thenReturn("'PISGRV':'High Value'");
        when(httpClient.buildFilterString(List.of("'PISGRV':'High Value'")))
                .thenReturn("='PISGRV':'High Value'");

        JsonObject product = buildProduct("ID-1", "Item-001");
        when(salsifyService.getAllProducts(TEST_ORG_ID, TEST_API_KEY, "='PISGRV':'High Value'", null, null))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        Map<String, Object> filters = new HashMap<>();
        filters.put("PISGRV", "High Value");

        ExtensionResponse response = area.filterProducts(filters, "Item#", null, null);
        List<Map<String, Object>> result = extractProductList(response);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(httpClient).buildFilterExpression("PISGRV", Constants.OPERATOR_EQUALS, "High Value");
    }

    @Test
    void filterProducts_multipleFilters_ANDCombined() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);
        when(httpClient.buildFilterExpression("PISGRV", Constants.OPERATOR_EQUALS, "High Value"))
                .thenReturn("'PISGRV':'High Value'");
        when(httpClient.buildFilterExpression("PILICT", Constants.OPERATOR_EQUALS, "Y"))
                .thenReturn("'PILICT':'Y'");
        when(httpClient.buildFilterString(any()))
                .thenReturn("='PISGRV':'High Value','PILICT':'Y'");

        JsonObject product = buildProduct("ID-1", "Item-001");
        when(salsifyService.getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyString(), isNull(), isNull()))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("PISGRV", "High Value");
        filters.put("PILICT", "Y");

        ExtensionResponse response = area.filterProducts(filters, "Item#", null, null);
        List<Map<String, Object>> result = extractProductList(response);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(httpClient).buildFilterExpression("PISGRV", Constants.OPERATOR_EQUALS, "High Value");
        verify(httpClient).buildFilterExpression("PILICT", Constants.OPERATOR_EQUALS, "Y");
    }

    @Test
    void filterProducts_emptyFilters_noFilterApplied() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);

        JsonObject product = buildProduct("ID-1", "Item-001");
        when(salsifyService.getAllProducts(TEST_ORG_ID, TEST_API_KEY, null, null, null))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        ExtensionResponse response = area.filterProducts(
                new HashMap<>(),
                "Item#",
                null,
                null);
        List<Map<String, Object>> result = extractProductList(response);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(salsifyService).getAllProducts(TEST_ORG_ID, TEST_API_KEY, null, null, null);
    }

    @Test
    void filterProducts_nullFilters_noFilterApplied() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);

        JsonObject product = buildProduct("ID-1", "Item-001");
        when(salsifyService.getAllProducts(TEST_ORG_ID, TEST_API_KEY, null, null, null))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        ExtensionResponse response = area.filterProducts(null, "Item#", null, null);
        List<Map<String, Object>> result = extractProductList(response);

        assertNotNull(result);
        verify(salsifyService).getAllProducts(TEST_ORG_ID, TEST_API_KEY, null, null, null);
    }

    @Test
    void filterProducts_invalidValueInFilter_converted() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);
        when(httpClient.buildFilterExpression("PIUPCD", Constants.OPERATOR_EQUALS, "123456789"))
                .thenReturn("'PIUPCD':'123456789'");
        when(httpClient.buildFilterString(List.of("'PIUPCD':'123456789'")))
                .thenReturn("='PIUPCD':'123456789'");

        JsonObject product = buildProduct("ID-1", "Item-001");
        when(salsifyService.getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyString(), isNull(), isNull()))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        Map<String, Object> filters = new HashMap<>();
        filters.put("PIUPCD", 123456789); // Number value - should be converted to string

        ExtensionResponse response = area.filterProducts(filters, "Item#", null, null);
        List<Map<String, Object>> result = extractProductList(response);

        assertNotNull(result);
        verify(httpClient).buildFilterExpression("PIUPCD", Constants.OPERATOR_EQUALS, "123456789");
    }

    // ─── Column Selection Tests ───────────────────────────────────────────────────

    @Test
    void filterProducts_selectedColumns_onlyReturnedInResponse() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);

        JsonObject product = buildProduct("ID-1", "Item-001");
        product.addProperty("PIUPCD", "123456789");
        product.addProperty("PISIZE", "12 CT");

        when(salsifyService.getAllProducts(TEST_ORG_ID, TEST_API_KEY, null, null, null))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        ExtensionResponse response = area.filterProducts(
                new HashMap<>(),
                "Item#,PIUPCD",
                null,
                null);
        List<Map<String, Object>> result = extractProductList(response);

        assertEquals(1, result.size());
        Map<String, Object> firstProduct = result.get(0);
        assertTrue(firstProduct.containsKey("Item#"));
        assertTrue(firstProduct.containsKey("PIUPCD"));
        assertFalse(firstProduct.containsKey("PISIZE"));
    }

    @Test
    void filterProducts_columnsNotInResponse_omittedFromMap() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);

        JsonObject product = buildProduct("ID-1", "Item-001");
        // Product doesn't have PIUPCD

        when(salsifyService.getAllProducts(TEST_ORG_ID, TEST_API_KEY, null, null, null))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        ExtensionResponse response = area.filterProducts(
                new HashMap<>(),
                "Item#,PIUPCD",
                null,
                null);
        List<Map<String, Object>> result = extractProductList(response);

        assertEquals(1, result.size());
        Map<String, Object> firstProduct = result.get(0);
        assertTrue(firstProduct.containsKey("Item#"));
        assertFalse(firstProduct.containsKey("PIUPCD")); // Missing column should be omitted
    }

    @Test
    void filterProducts_columnNamesPreserveCase_exactMatch() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);

        JsonObject product = new JsonObject();
        product.addProperty("salsify:id", "ID-1");
        product.addProperty("Item#", "ITEM-001");
        product.addProperty("PIUPCD", "123456789");

        when(salsifyService.getAllProducts(TEST_ORG_ID, TEST_API_KEY, null, null, null))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        ExtensionResponse response = area.filterProducts(
                new HashMap<>(),
                "Item#,PIUPCD", // Case-sensitive
                null,
                null);
        List<Map<String, Object>> result = extractProductList(response);

        assertEquals(1, result.size());
        Map<String, Object> firstProduct = result.get(0);
        // Exact case should match
        assertTrue(firstProduct.containsKey("Item#"));
        assertTrue(firstProduct.containsKey("PIUPCD"));
        // Different case should NOT match
        assertFalse(firstProduct.containsKey("item#"));
        assertFalse(firstProduct.containsKey("piupcd"));
    }

    @Test
    void filterProducts_arrayColumnValue_returnedAsCommaSeparatedString() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);

        JsonObject product = buildProduct("ID-1", "Item-001");
        JsonArray alternateItems = new JsonArray();
        alternateItems.add("ALT-001");
        alternateItems.add("ALT-002");
        product.add("DecoPac Alternate Items", alternateItems);

        when(salsifyService.getAllProducts(TEST_ORG_ID, TEST_API_KEY, null, null, null))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        ExtensionResponse response = area.filterProducts(
                new HashMap<>(),
                "Item#,DecoPac Alternate Items",
                null,
                null);
        List<Map<String, Object>> result = extractProductList(response);

        assertEquals(1, result.size());
        Map<String, Object> firstProduct = result.get(0);
        assertTrue(firstProduct.containsKey("DecoPac Alternate Items"));

        // Array should be flattened to comma-separated string for Krista framework compatibility
        Object value = firstProduct.get("DecoPac Alternate Items");
        assertInstanceOf(String.class, value);
        String stringValue = (String) value;
        assertEquals("ALT-001,ALT-002", stringValue);
    }

    @Test
    void filterProducts_commaSeparatedColumns_parsed() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);

        JsonObject product = buildProduct("ID-1", "Item-001");
        product.addProperty("PIUPCD", "123456789");
        product.addProperty("PISIZE", "12 CT");

        when(salsifyService.getAllProducts(TEST_ORG_ID, TEST_API_KEY, null, null, null))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        // Test with spaces around commas
        ExtensionResponse response = area.filterProducts(
                new HashMap<>(),
                "Item#, PIUPCD , PISIZE",
                null,
                null);
        List<Map<String, Object>> result = extractProductList(response);

        assertEquals(1, result.size());
        Map<String, Object> firstProduct = result.get(0);
        // All columns should be found despite whitespace
        assertTrue(firstProduct.containsKey("Item#"));
        assertTrue(firstProduct.containsKey("PIUPCD"));
        assertTrue(firstProduct.containsKey("PISIZE"));
    }

    // ─── Combined Filters and Columns Tests ───────────────────────────────────────

    @Test
    void filterProducts_dynamicFiltersAndColumns_combined() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);
        when(httpClient.buildFilterExpression("PISGRV", Constants.OPERATOR_EQUALS, "HBD - BIRTHDAY"))
                .thenReturn("'PISGRV':'HBD - BIRTHDAY'");
        when(httpClient.buildFilterString(List.of("'PISGRV':'HBD - BIRTHDAY'")))
                .thenReturn("='PISGRV':'HBD - BIRTHDAY'");

        JsonObject product = buildProduct("ID-1", "Item-001");
        product.addProperty("PIUPCD", "123456789");
        product.addProperty("PISIZE", "12 CT");
        product.addProperty("PISGRV", "HBD - BIRTHDAY");

        when(salsifyService.getAllProducts(TEST_ORG_ID, TEST_API_KEY, "='PISGRV':'HBD - BIRTHDAY'", null, null))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        Map<String, Object> filters = new HashMap<>();
        filters.put("PISGRV", "HBD - BIRTHDAY");

        ExtensionResponse response = area.filterProducts(
                filters,
                "Item#,PIUPCD,PISGRV",
                null,
                null);
        List<Map<String, Object>> result = extractProductList(response);

        assertEquals(1, result.size());
        Map<String, Object> firstProduct = result.get(0);
        assertEquals("Item-001", firstProduct.get("Item#"));
        assertEquals("123456789", firstProduct.get("PIUPCD"));
        assertEquals("HBD - BIRTHDAY", firstProduct.get("PISGRV"));
        // PISIZE should not be included
        assertFalse(firstProduct.containsKey("PISIZE"));
    }

    @Test
    void filterProducts_serviceThrowsException_returnsEmptyList() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);
        when(salsifyService.getAllProducts(anyString(), anyString(), isNull(), isNull(), isNull()))
                .thenThrow(new IOException("API Error"));

        ExtensionResponse response = area.filterProducts(new HashMap<>(), "Item#", null, null);
        List<Map<String, Object>> result = extractProductList(response);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ─── Pagination Tests ────────────────────────────────────────────────────────

    @Test
    void filterProducts_pageSizeAndOffset_passedToService() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);

        JsonObject product = buildProduct("ID-1", "Item-001");
        when(salsifyService.getAllProducts(TEST_ORG_ID, TEST_API_KEY, null, 100, 50))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        area.filterProducts(new HashMap<>(), "Item#", 100.0, 50.0);

        verify(salsifyService).getAllProducts(TEST_ORG_ID, TEST_API_KEY, null, 100, 50);
    }

    @Test
    void filterProducts_offsetZero_passedAsZero() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);

        JsonObject product = buildProduct("ID-1", "Item-001");
        when(salsifyService.getAllProducts(TEST_ORG_ID, TEST_API_KEY, null, 50, 0))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        area.filterProducts(new HashMap<>(), "Item#", 50.0, 0.0);

        verify(salsifyService).getAllProducts(TEST_ORG_ID, TEST_API_KEY, null, 50, 0);
    }

    @Test
    void filterProducts_salsifyUrlMapping_returnsMainImageAssetUrl() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);

        JsonObject product = buildProduct("ID-1", "Item-001");
        product.addProperty("Main Image", "fo15h1pb7d7vhfmj72k678h10p");

        // Create digital assets array with matching asset
        JsonArray digitalAssets = new JsonArray();
        JsonObject mainAsset = new JsonObject();
        mainAsset.addProperty("salsify:id", "fo15h1pb7d7vhfmj72k678h10p");
        mainAsset.addProperty("salsify:url", "http://images.salsify.com/image/upload/s--HY-YQ4Ws--/7e149c2535612b04d80db108a3e54dd6b265272b.png");
        digitalAssets.add(mainAsset);

        JsonObject otherAsset = new JsonObject();
        otherAsset.addProperty("salsify:id", "nviom4vfdl2j7c24ip4rc74v3j");
        otherAsset.addProperty("salsify:url", "http://images.salsify.com/image/upload/s--IoFpsx3Q--/bplyb5ddiwmlobuxz8v3.png");
        digitalAssets.add(otherAsset);

        product.add("salsify:digital_assets", digitalAssets);

        when(salsifyService.getAllProducts(TEST_ORG_ID, TEST_API_KEY, null, null, null))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        ExtensionResponse response = area.filterProducts(
                new HashMap<>(),
                "Item#,salsify:url",
                null,
                null);
        List<Map<String, Object>> result = extractProductList(response);

        assertEquals(1, result.size());
        Map<String, Object> firstProduct = result.get(0);
        assertTrue(firstProduct.containsKey("salsify:url"));
        assertEquals("http://images.salsify.com/image/upload/s--HY-YQ4Ws--/7e149c2535612b04d80db108a3e54dd6b265272b.png",
                firstProduct.get("salsify:url"));
    }

    @Test
    void filterProducts_imageUrlMapping_returnsMainImageAssetUrl() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);

        JsonObject product = buildProduct("ID-1", "Item-001");
        product.addProperty("Main Image", "fo15h1pb7d7vhfmj72k678h10p");

        // Create digital assets array with matching asset
        JsonArray digitalAssets = new JsonArray();
        JsonObject mainAsset = new JsonObject();
        mainAsset.addProperty("salsify:id", "fo15h1pb7d7vhfmj72k678h10p");
        mainAsset.addProperty("salsify:url", "http://images.salsify.com/image/upload/s--HY-YQ4Ws--/7e149c2535612b04d80db108a3e54dd6b265272b.png");
        digitalAssets.add(mainAsset);

        product.add("salsify:digital_assets", digitalAssets);

        when(salsifyService.getAllProducts(TEST_ORG_ID, TEST_API_KEY, null, null, null))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        ExtensionResponse response = area.filterProducts(
                new HashMap<>(),
                "Item#,Image Url",
                null,
                null);
        List<Map<String, Object>> result = extractProductList(response);

        assertEquals(1, result.size());
        Map<String, Object> firstProduct = result.get(0);
        assertTrue(firstProduct.containsKey("Image Url"));
        assertEquals("http://images.salsify.com/image/upload/s--HY-YQ4Ws--/7e149c2535612b04d80db108a3e54dd6b265272b.png",
                firstProduct.get("Image Url"));
    }

    @Test
    void filterProducts_objectArrayColumn_returnedAsJsonString() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);

        JsonObject product = buildProduct("ID-1", "Item-001");

        // Create salsify:relations array (array of objects)
        JsonArray relations = new JsonArray();
        JsonObject relation1 = new JsonObject();
        relation1.addProperty("relation_type", "Related All");
        relation1.addProperty("salsify:target_product_id", "36446");
        relations.add(relation1);

        JsonObject relation2 = new JsonObject();
        relation2.addProperty("relation_type", "Alternate All");
        relation2.addProperty("salsify:target_product_id", "6667");
        relations.add(relation2);

        product.add("salsify:relations", relations);

        when(salsifyService.getAllProducts(TEST_ORG_ID, TEST_API_KEY, null, null, null))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        ExtensionResponse response = area.filterProducts(
                new HashMap<>(),
                "Item#,salsify:relations",
                null,
                null);
        List<Map<String, Object>> result = extractProductList(response);

        assertEquals(1, result.size());
        Map<String, Object> firstProduct = result.get(0);
        assertTrue(firstProduct.containsKey("salsify:relations"));

        // Object array should be returned as JSON string
        Object value = firstProduct.get("salsify:relations");
        assertInstanceOf(String.class, value);
        String jsonString = (String) value;
        assertTrue(jsonString.startsWith("["));
        assertTrue(jsonString.contains("Related All"));
        assertTrue(jsonString.contains("36446"));
    }

    // ─── Helper Methods ──────────────────────────────────────────────────────────

    private JsonObject buildProduct(String id, String itemNumber) {
        JsonObject product = new JsonObject();
        product.addProperty("salsify:id", id);
        product.addProperty("Item#", itemNumber);
        return product;
    }
}
