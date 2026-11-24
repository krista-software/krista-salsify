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

package com.krista.krista.extension.salsify.catalog;

import app.krista.extension.executor.ExtensionResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.krista.krista.extension.salsify.connection.SalsifyHttpClient;
import com.krista.krista.extension.salsify.helpers.CartesianFilterGenerator;
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
import org.mockito.ArgumentCaptor;
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

    /** Stand-in primary filter for tests that don't exercise filter logic itself. */
    private static final Map<String, Object> PLACEHOLDER_PRIMARY = Map.of("salsify:id", "any");

    @Mock private SalsifyServiceImpl salsifyService;
    @Mock private SalsifyHttpClient httpClient;
    @Mock private CredentialStore credentialStore;

    private SalsifyProductArea area;

    @BeforeEach
    void setUp() {
        CredentialExtractor credentialExtractor = new CredentialExtractor(credentialStore);
        DynamicColumnSelector dynamicColumnSelector = new DynamicColumnSelector();
        CartesianFilterGenerator cartesianFilterGenerator = new CartesianFilterGenerator();
        FilterProductsOrchestrator filterProductsOrchestrator =
                new FilterProductsOrchestrator(httpClient, dynamicColumnSelector, cartesianFilterGenerator);

        area = new SalsifyProductArea(salsifyService,
                credentialExtractor, dynamicColumnSelector, filterProductsOrchestrator);

        // Default filter-string building so tests with PLACEHOLDER_PRIMARY don't require per-test stubs.
        // Tests that specifically test filter content override these with more specific stubs.
        lenient().when(httpClient.buildFilterExpression(any(), any(), any()))
                .thenAnswer(inv -> "'" + inv.getArgument(0) + "':'" + inv.getArgument(2) + "'");
        lenient().when(httpClient.buildFilterString(anyList()))
                .thenAnswer(inv -> "=" + String.join(",", (List<String>) inv.getArgument(0)));
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
        when(salsifyService.getAllProducts(TEST_ORG_ID, TEST_API_KEY, List.of("='PISGRV':'High Value'"), null, null))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        Map<String, Object> filters = new HashMap<>();
        filters.put("PISGRV", "High Value");

        ExtensionResponse response = area.filterProducts(filters, null, "Item#", null, null, null);
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
        when(salsifyService.getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyList(), isNull(), isNull()))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("PISGRV", "High Value");
        filters.put("PILICT", "Y");

        ExtensionResponse response = area.filterProducts(filters, null, "Item#", null, null, null);
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
        when(salsifyService.getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyList(), isNull(), isNull()))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        ExtensionResponse response = area.filterProducts(
                PLACEHOLDER_PRIMARY,
                null,
                "Item#",
                null,
                null,
                null);
        List<Map<String, Object>> result = extractProductList(response);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(salsifyService).getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyList(), isNull(), isNull());
    }

    @Test
    void filterProducts_nullPrimaryFilters_returnsErrorResponse() {
        // Primary Filters is required — null/empty produces an error response without
        // hitting the credential store or service.
        ExtensionResponse response = area.filterProducts(null, null, "Item#", null, null, null);

        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult(),
                "Validation errors come back as a populated error response, not a failure");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> products = (List<Map<String, Object>>) response.getResponseValue()
                .get(Constants.RESPONSE_FIELD_PRODUCT_LIST);
        assertTrue(products.isEmpty());
        verifyNoInteractions(salsifyService);
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
        when(salsifyService.getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyList(), isNull(), isNull()))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        Map<String, Object> filters = new HashMap<>();
        filters.put("PIUPCD", 123456789); // Number value - should be converted to string

        ExtensionResponse response = area.filterProducts(filters, null, "Item#", null, null, null);
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

        when(salsifyService.getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyList(), isNull(), isNull()))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        ExtensionResponse response = area.filterProducts(
                PLACEHOLDER_PRIMARY,
                null,
                "Item#,PIUPCD",
                null,
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
    void filterProducts_columnsNotInResponse_includedAsNull() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);

        JsonObject product = buildProduct("ID-1", "Item-001");
        // Product doesn't have PIUPCD

        when(salsifyService.getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyList(), isNull(), isNull()))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        ExtensionResponse response = area.filterProducts(
                PLACEHOLDER_PRIMARY,
                null,
                "Item#,PIUPCD",
                null,
                null,
                null);
        List<Map<String, Object>> result = extractProductList(response);

        assertEquals(1, result.size());
        Map<String, Object> firstProduct = result.get(0);
        assertEquals("Item-001", firstProduct.get("Item#"));
        // Missing column is now included as null so the table renderer keeps the column header
        assertTrue(firstProduct.containsKey("PIUPCD"));
        assertNull(firstProduct.get("PIUPCD"));
    }

    @Test
    void filterProducts_columnNamesPreserveCase_exactMatch() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);

        JsonObject product = new JsonObject();
        product.addProperty("salsify:id", "ID-1");
        product.addProperty("Item#", "ITEM-001");
        product.addProperty("PIUPCD", "123456789");

        when(salsifyService.getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyList(), isNull(), isNull()))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        ExtensionResponse response = area.filterProducts(
                PLACEHOLDER_PRIMARY,
                null,
                "Item#,PIUPCD", // Case-sensitive
                null,
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

        when(salsifyService.getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyList(), isNull(), isNull()))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        ExtensionResponse response = area.filterProducts(
                PLACEHOLDER_PRIMARY,
                null,
                "Item#,DecoPac Alternate Items",
                null,
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

        when(salsifyService.getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyList(), isNull(), isNull()))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        // Test with spaces around commas
        ExtensionResponse response = area.filterProducts(
                PLACEHOLDER_PRIMARY,
                null,
                "Item#, PIUPCD , PISIZE",
                null,
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

        when(salsifyService.getAllProducts(TEST_ORG_ID, TEST_API_KEY, List.of("='PISGRV':'HBD - BIRTHDAY'"), null, null))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        Map<String, Object> filters = new HashMap<>();
        filters.put("PISGRV", "HBD - BIRTHDAY");

        ExtensionResponse response = area.filterProducts(
                filters,
                null,
                "Item#,PIUPCD,PISGRV",
                null,
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
        when(salsifyService.getAllProducts(anyString(), anyString(), anyList(), isNull(), isNull()))
                .thenThrow(new IOException("API Error"));

        ExtensionResponse response = area.filterProducts(PLACEHOLDER_PRIMARY, null, "Item#", null, null, null);
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
        when(salsifyService.getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyList(), eq(100), eq(50)))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        area.filterProducts(PLACEHOLDER_PRIMARY, null, "Item#", null, 100.0, 50.0);

        verify(salsifyService).getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyList(), eq(100), eq(50));
    }

    @Test
    void filterProducts_offsetZero_passedAsZero() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);

        JsonObject product = buildProduct("ID-1", "Item-001");
        when(salsifyService.getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyList(), eq(50), eq(0)))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        area.filterProducts(PLACEHOLDER_PRIMARY, null, "Item#", null, 50.0, 0.0);

        verify(salsifyService).getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyList(), eq(50), eq(0));
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

        when(salsifyService.getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyList(), isNull(), isNull()))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        ExtensionResponse response = area.filterProducts(
                PLACEHOLDER_PRIMARY,
                null,
                "Item#,salsify:url",
                null,
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

        when(salsifyService.getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyList(), isNull(), isNull()))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        ExtensionResponse response = area.filterProducts(
                PLACEHOLDER_PRIMARY,
                null,
                "Item#,Image Url",
                null,
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

        when(salsifyService.getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyList(), isNull(), isNull()))
                .thenReturn(new ProductListData(List.of(product), List.of(), 1, false));

        ExtensionResponse response = area.filterProducts(
                PLACEHOLDER_PRIMARY,
                null,
                "Item#,salsify:relations",
                null,
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

    // ─── Client-Side OR Filter Tests ────────────────────────────────────────────

    @Test
    void filterProducts_primaryAndSecondaryMixed_secondaryAppliedClientSide() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);

        // API returns 3 products matching primary; secondary narrows to P/S only
        JsonObject p1 = buildProduct("1", "Item-001");
        p1.addProperty("PIDISC", "P");
        JsonObject p2 = buildProduct("2", "Item-002");
        p2.addProperty("PIDISC", "S");
        JsonObject p3 = buildProduct("3", "Item-003");
        p3.addProperty("PIDISC", "N");

        when(salsifyService.getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyList(), isNull(), isNull()))
                .thenReturn(new ProductListData(List.of(p1, p2, p3), List.of(), 3, false));

        Map<String, Object> primary = new LinkedHashMap<>();
        primary.put("PISGRV", "BBY - BABY");
        Map<String, Object> secondary = new LinkedHashMap<>();
        secondary.put("PIDISC", "P,S");

        ExtensionResponse response = area.filterProducts(primary, secondary, "Item#,PIDISC", null, null, null);
        List<Map<String, Object>> result = extractProductList(response);

        // Only P and S survive client-side filtering
        assertEquals(2, result.size());
        assertEquals("P", result.get(0).get("PIDISC"));
        assertEquals("S", result.get(1).get("PIDISC"));
    }

    @Test
    void filterProducts_secondaryOrFilter_narrowsResultsClientSide() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);

        JsonObject p1 = buildProduct("1", "Item-001");
        p1.addProperty("PIDISC", "P");
        JsonObject p2 = buildProduct("2", "Item-002");
        p2.addProperty("PIDISC", "N");

        when(salsifyService.getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyList(), isNull(), isNull()))
                .thenReturn(new ProductListData(List.of(p1, p2), List.of(), 2, false));

        Map<String, Object> secondary = new HashMap<>();
        secondary.put("PIDISC", "P,S");

        ExtensionResponse response = area.filterProducts(
                PLACEHOLDER_PRIMARY, secondary, "Item#,PIDISC", null, null, null);
        List<Map<String, Object>> result = extractProductList(response);

        // Only P survives — the multi-value PIDISC went client-side
        assertEquals(1, result.size());
        assertEquals("P", result.get(0).get("PIDISC"));
    }

    @Test
    void filterProducts_secondaryOrFilter_totalRecordsReflectsClientFiltered() throws IOException {
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);

        JsonObject p1 = buildProduct("1", "Item-001");
        p1.addProperty("PIDISC", "P");
        JsonObject p2 = buildProduct("2", "Item-002");
        p2.addProperty("PIDISC", "N");
        JsonObject p3 = buildProduct("3", "Item-003");
        p3.addProperty("PIDISC", "S");

        when(salsifyService.getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyList(), isNull(), isNull()))
                .thenReturn(new ProductListData(List.of(p1, p2, p3), List.of(), 3, false));

        Map<String, Object> secondary = new HashMap<>();
        secondary.put("PIDISC", "P,S");

        ExtensionResponse response = area.filterProducts(
                PLACEHOLDER_PRIMARY, secondary, "Item#,PIDISC", null, null, null);

        Double totalRecords = (Double) response.getResponseValue().get(Constants.RESPONSE_FIELD_TOTAL_RECORDS);
        assertEquals(2.0, totalRecords);
    }

    // ─── Primary Filters / Production-Bug Regression ────────────────────────────

    @Test
    void filterProducts_primaryFilters_fanOutAcrossShards() throws IOException {
        // Production bug: PISGRV ∈ {Baby,Wed} × PIDISC ∈ {P,S} with both as Primary →
        // 4 separate API calls, results aggregated. No 10K-cap-without-filter trap.
        when(credentialStore.getOrgId()).thenReturn(TEST_ORG_ID);
        when(credentialStore.getApiKey()).thenReturn(TEST_API_KEY);
        when(httpClient.buildFilterExpression(eq("PISGRV"), eq(Constants.OPERATOR_EQUALS), anyString()))
                .thenAnswer(inv -> "'PISGRV':'" + inv.getArgument(2) + "'");
        when(httpClient.buildFilterExpression(eq("PIDISC"), eq(Constants.OPERATOR_EQUALS), anyString()))
                .thenAnswer(inv -> "'PIDISC':'" + inv.getArgument(2) + "'");
        when(httpClient.buildFilterString(anyList()))
                .thenAnswer(inv -> "=" + String.join(",", (List<String>) inv.getArgument(0)));

        JsonObject p1 = buildProduct("ID-1", "Item-001");
        JsonObject p2 = buildProduct("ID-2", "Item-002");
        when(salsifyService.getAllProducts(eq(TEST_ORG_ID), eq(TEST_API_KEY), anyList(), isNull(), isNull()))
                .thenReturn(new ProductListData(List.of(p1, p2), List.of(), 2, false));

        Map<String, Object> primary = new LinkedHashMap<>();
        primary.put("PISGRV", "Baby,Wed");
        primary.put("PIDISC", "P,S");

        ExtensionResponse response = area.filterProducts(
                primary, null, "Item#", null, null, null);

        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());
        // Verify the service was called with a 4-element shard list
        ArgumentCaptor<List<String>> shardsCaptor = ArgumentCaptor.forClass(List.class);
        verify(salsifyService).getAllProducts(
                eq(TEST_ORG_ID), eq(TEST_API_KEY), shardsCaptor.capture(), isNull(), isNull());
        assertEquals(4, shardsCaptor.getValue().size());
    }

    @Test
    void filterProducts_fieldInBothPrimaryAndSecondary_returnsErrorResponse() {
        // Validation rejects a field appearing in both maps before any service call.
        Map<String, Object> primary = new HashMap<>();
        primary.put("PISGRV", "Baby");
        Map<String, Object> secondary = new HashMap<>();
        secondary.put("PISGRV", "Wed");

        ExtensionResponse response = area.filterProducts(
                primary, secondary, "Item#", null, null, null);

        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult(),
                "Validation errors come back as a populated error response, not a failure");
        Map<String, Object> responseFields = response.getResponseValue();
        assertNotNull(responseFields.get(Constants.RESPONSE_FIELD_RESPONSE_INFO));
        verifyNoInteractions(salsifyService);
    }

    @Test
    void filterProducts_primaryCartesianOverCap_returnsErrorResponse() {
        // 5 × 5 = 25, over cap of 20.
        Map<String, Object> primary = new LinkedHashMap<>();
        primary.put("PISGRV", "a,b,c,d,e");
        primary.put("PIDISC", "v,w,x,y,z");

        ExtensionResponse response = area.filterProducts(
                primary, null, "Item#", null, null, null);

        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> products = (List<Map<String, Object>>) response.getResponseValue()
                .get(Constants.RESPONSE_FIELD_PRODUCT_LIST);
        assertTrue(products.isEmpty());
        verifyNoInteractions(salsifyService);
    }

    // ─── Helper Methods ──────────────────────────────────────────────────────────

    private JsonObject buildProduct(String id, String itemNumber) {
        JsonObject product = new JsonObject();
        product.addProperty("salsify:id", id);
        product.addProperty("Item#", itemNumber);
        return product;
    }
}
