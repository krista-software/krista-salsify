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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.krista.krista.extension.salsify.connection.SalsifyHttpClient;
import com.krista.krista.extension.salsify.utils.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalsifyServiceImplTest {

    @Mock
    private SalsifyHttpClient httpClient;

    private SalsifyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SalsifyServiceImpl(httpClient);
    }

    // ─── Single page ─────────────────────────────────────────────────────────

    @Test
    void getAllProducts_singlePage_noNextUrl_returnsProducts() throws IOException {
        JsonArray products = buildProducts(3);
        when(httpClient.buildProductsUrl(anyString(), any(), any())).thenReturn("http://fake/page1");
        when(httpClient.getPage("http://fake/page1", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(products, null, 3));

        ProductListData result = service.getAllProducts("org1", "key", null, null, null);

        assertEquals(3, result.products.size());
        assertEquals(3, result.totalCount);
        assertFalse(result.exportRecommended);
    }

    // ─── Link header pagination ──────────────────────────────────────────────

    @Test
    void getAllProducts_twoPagesPagination_followsNextUrl() throws IOException {
        JsonArray page1Products = buildProducts(2);
        JsonArray page2Products = buildProducts(2);

        when(httpClient.buildProductsUrl(anyString(), any(), any())).thenReturn("http://fake/page1");
        when(httpClient.getPage("http://fake/page1", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(page1Products, "http://fake/page2", 4));
        when(httpClient.getPage("http://fake/page2", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(page2Products, null, 4));

        ProductListData result = service.getAllProducts("org1", "key", null, null, null);

        assertEquals(4, result.products.size());
        assertEquals(4, result.totalCount);
    }

    // ─── Page-number pagination (no Link header) ─────────────────────────────

    @Test
    void getAllProducts_pageNumberPagination_advancesManually() throws IOException {
        JsonArray page1Products = buildProducts(2);
        JsonArray page2Products = buildProducts(1);

        when(httpClient.buildProductsUrl("org1", 10, null)).thenReturn("http://fake/page1");
        when(httpClient.buildProductsUrl("org1", 10, null, 2)).thenReturn("http://fake/page2");

        when(httpClient.getPage("http://fake/page1", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(page1Products, null, 3));
        when(httpClient.getPage("http://fake/page2", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(page2Products, null, 3));

        ProductListData result = service.getAllProducts("org1", "key", null, 10, null);

        assertEquals(3, result.products.size());
    }

    // ─── totalCount fallback ─────────────────────────────────────────────────

    @Test
    void getAllProducts_noMetaTotalCount_usesAccumulatedCount() throws IOException {
        JsonArray products = buildProducts(5);
        when(httpClient.buildProductsUrl(anyString(), any(), any())).thenReturn("http://fake/p1");
        when(httpClient.getPage("http://fake/p1", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(products, null, 0));

        ProductListData result = service.getAllProducts("org1", "key", null, null, null);

        assertEquals(5, result.totalCount);
    }

    // ─── Non-JsonObject elements ─────────────────────────────────────────────

    @Test
    void getAllProducts_nonObjectElementInPage_isSkipped() throws IOException {
        JsonArray mixed = new JsonArray();
        mixed.add(new JsonPrimitive("not-an-object"));
        JsonObject validProduct = new JsonObject();
        validProduct.addProperty("salsify:id", "id-1");
        mixed.add(validProduct);

        when(httpClient.buildProductsUrl(anyString(), any(), any())).thenReturn("http://fake/p1");
        when(httpClient.getPage("http://fake/p1", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(mixed, null, 2));

        ProductListData result = service.getAllProducts("org1", "key", null, null, null);

        assertEquals(1, result.products.size());
    }

    // ─── Column header collection ────────────────────────────────────────────

    @Test
    void getAllProducts_flatLayout_collectsNonSalsifyKeys() throws IOException {
        JsonObject product = new JsonObject();
        product.addProperty("salsify:id", "id-1");
        product.addProperty("salsify:other", "skip");
        product.addProperty("Item#", "ABC");
        product.addProperty("Status", "Active");

        JsonArray page = new JsonArray();
        page.add(product);

        when(httpClient.buildProductsUrl(anyString(), any(), any())).thenReturn("http://fake/p1");
        when(httpClient.getPage("http://fake/p1", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(page, null, 1));

        ProductListData result = service.getAllProducts("org1", "key", null, null, null);

        assertTrue(result.columnHeaders.contains("salsify:id"));
        assertTrue(result.columnHeaders.contains("Item#"));
        assertTrue(result.columnHeaders.contains("Status"));
        assertFalse(result.columnHeaders.contains("salsify:other"));
    }

    @Test
    void getAllProducts_nestedLayout_collectsPropertiesKeys() throws IOException {
        JsonObject props = new JsonObject();
        props.addProperty("Item#", "ABC");
        props.addProperty("salsify:hidden", "skip");

        JsonObject product = new JsonObject();
        product.addProperty("salsify:id", "id-1");
        product.add("properties", props);

        JsonArray page = new JsonArray();
        page.add(product);

        when(httpClient.buildProductsUrl(anyString(), any(), any())).thenReturn("http://fake/p1");
        when(httpClient.getPage("http://fake/p1", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(page, null, 1));

        ProductListData result = service.getAllProducts("org1", "key", null, null, null);

        assertTrue(result.columnHeaders.contains("salsify:id"));
        assertTrue(result.columnHeaders.contains("Item#"));
        assertFalse(result.columnHeaders.contains("salsify:hidden"));
    }

    @Test
    void getAllProducts_propertiesIsNotJsonObject_treatedAsFlatLayout() throws IOException {
        JsonObject product = new JsonObject();
        product.addProperty("salsify:id", "id-1");
        product.add("properties", new JsonArray()); // not a JsonObject — should use flat layout
        product.addProperty("Item#", "ABC");

        JsonArray page = new JsonArray();
        page.add(product);

        when(httpClient.buildProductsUrl(anyString(), any(), any())).thenReturn("http://fake/p1");
        when(httpClient.getPage("http://fake/p1", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(page, null, 1));

        ProductListData result = service.getAllProducts("org1", "key", null, null, null);

        assertTrue(result.columnHeaders.contains("Item#"));
        assertTrue(result.columnHeaders.contains("salsify:id"));
    }

    @Test
    void getAllProducts_productWithNoSalsifyId_noIdInHeaders() throws IOException {
        JsonObject product = new JsonObject();
        product.addProperty("Item#", "ABC");

        JsonArray page = new JsonArray();
        page.add(product);

        when(httpClient.buildProductsUrl(anyString(), any(), any())).thenReturn("http://fake/p1");
        when(httpClient.getPage("http://fake/p1", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(page, null, 1));

        ProductListData result = service.getAllProducts("org1", "key", null, null, null);

        assertFalse(result.columnHeaders.contains("salsify:id"));
    }

    // ─── Large catalog threshold ─────────────────────────────────────────────

    @Test
    void getAllProducts_exceedsLargeCatalogThreshold_setsExportRecommended() throws IOException {
        JsonArray bigPage = new JsonArray();
        for (int i = 0; i < Constants.LARGE_CATALOG_THRESHOLD; i++) {
            JsonObject p = new JsonObject();
            p.addProperty("salsify:id", "id-" + i);
            bigPage.add(p);
        }

        when(httpClient.buildProductsUrl(anyString(), any(), any())).thenReturn("http://fake/p1");
        when(httpClient.getPage("http://fake/p1", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(bigPage, null, Constants.LARGE_CATALOG_THRESHOLD));

        ProductListData result = service.getAllProducts("org1", "key", null, null, null);

        assertTrue(result.exportRecommended);
        assertEquals(Constants.LARGE_CATALOG_THRESHOLD, result.products.size());
    }

    // ─── With filter string ──────────────────────────────────────────────────

    @Test
    void getAllProducts_withFilterString_passedToUrl() throws IOException {
        when(httpClient.buildProductsUrl("org1", 5, "='PICCDE':'POPT'")).thenReturn("http://fake/filtered");
        when(httpClient.getPage("http://fake/filtered", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(buildProducts(1), null, 1));

        ProductListData result = service.getAllProducts("org1", "key", List.of("='PICCDE':'POPT'"), 5, null);

        assertEquals(1, result.products.size());
        verify(httpClient).buildProductsUrl("org1", 5, "='PICCDE':'POPT'");
    }

    // ─── Offset-based pagination ─────────────────────────────────────────────

    @Test
    void getAllProducts_withOffsetPositive_fetchesSinglePage() throws IOException {
        JsonArray products = buildProducts(2);
        when(httpClient.buildProductsUrlWithOffset("org1", 50, null, 100))
                .thenReturn("http://fake/offset100");
        when(httpClient.getPage("http://fake/offset100", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(products, null, 500));

        ProductListData result = service.getAllProducts("org1", "key", null, 50, 100);

        assertEquals(2, result.products.size());
        assertEquals(500, result.totalCount);
        assertFalse(result.exportRecommended);
    }

    @Test
    void getAllProducts_withOffsetZero_usesAutomaticPagination() throws IOException {
        JsonArray products = buildProducts(3);
        when(httpClient.buildProductsUrl(anyString(), any(), any())).thenReturn("http://fake/page1");
        when(httpClient.getPage("http://fake/page1", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(products, null, 3));

        ProductListData result = service.getAllProducts("org1", "key", null, null, 0);

        assertEquals(3, result.products.size());
        verify(httpClient, never()).buildProductsUrlWithOffset(anyString(), any(), any(), any());
    }

    @Test
    void getAllProducts_withOffsetNull_usesAutomaticPagination() throws IOException {
        JsonArray products = buildProducts(3);
        when(httpClient.buildProductsUrl(anyString(), any(), any())).thenReturn("http://fake/page1");
        when(httpClient.getPage("http://fake/page1", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(products, null, 3));

        ProductListData result = service.getAllProducts("org1", "key", null, null, null);

        assertEquals(3, result.products.size());
        verify(httpClient, never()).buildProductsUrlWithOffset(anyString(), any(), any(), any());
    }

    @Test
    void getAllProducts_offsetBasedPagination_doesNotFollowNextUrl() throws IOException {
        JsonArray products = buildProducts(2);
        when(httpClient.buildProductsUrlWithOffset("org1", 50, null, 200))
                .thenReturn("http://fake/offset200");
        when(httpClient.getPage("http://fake/offset200", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(products, "http://fake/offset250", 1000));

        ProductListData result = service.getAllProducts("org1", "key", null, 50, 200);

        assertEquals(2, result.products.size());
        verify(httpClient).getPage("http://fake/offset200", "key");
        verify(httpClient, never()).getPage("http://fake/offset250", "key");
    }

    @Test
    void getAllProducts_offsetWithFilter_passesFilterToUrl() throws IOException {
        JsonArray products = buildProducts(1);
        when(httpClient.buildProductsUrlWithOffset("org1", 25, "='PICCDE':'POPT'", 50))
                .thenReturn("http://fake/filtered-offset");
        when(httpClient.getPage("http://fake/filtered-offset", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(products, null, 100));

        ProductListData result = service.getAllProducts("org1", "key", List.of("='PICCDE':'POPT'"), 25, 50);

        assertEquals(1, result.products.size());
        verify(httpClient).buildProductsUrlWithOffset("org1", 25, "='PICCDE':'POPT'", 50);
    }

    @Test
    void getAllProducts_offsetBasedPagination_collectsColumnHeaders() throws IOException {
        JsonObject product = new JsonObject();
        product.addProperty("salsify:id", "id-1");
        product.addProperty("Item#", "ABC");
        product.addProperty("Status", "Active");

        JsonArray page = new JsonArray();
        page.add(product);

        when(httpClient.buildProductsUrlWithOffset("org1", null, null, 50))
                .thenReturn("http://fake/offset50");
        when(httpClient.getPage("http://fake/offset50", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(page, null, 100));

        ProductListData result = service.getAllProducts("org1", "key", null, null, 50);

        assertTrue(result.columnHeaders.contains("salsify:id"));
        assertTrue(result.columnHeaders.contains("Item#"));
        assertTrue(result.columnHeaders.contains("Status"));
    }

    // ─── Automatic pagination (no offset) ─────────────────────────────────────

    @Test
    void getAllProducts_automaticPagination_usesOriginalLogic() throws IOException {
        JsonArray page1 = buildProducts(2);
        JsonArray page2 = buildProducts(2);

        when(httpClient.buildProductsUrl("org1", 10, null)).thenReturn("http://fake/page1");
        when(httpClient.buildProductsUrl("org1", 10, null, 2)).thenReturn("http://fake/page2");

        when(httpClient.getPage("http://fake/page1", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(page1, null, 4));
        when(httpClient.getPage("http://fake/page2", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(page2, null, 4));

        ProductListData result = service.getAllProducts("org1", "key", null, 10, null);

        assertEquals(4, result.products.size());
        assertFalse(result.exportRecommended);
        verify(httpClient, never()).buildProductsUrlWithOffset(anyString(), any(), any(), any());
    }

    // ─── Fan-out (multi-shard) ───────────────────────────────────────────────

    @Test
    void getAllProducts_emptyShardList_fetchesWithNoFilter() throws IOException {
        JsonArray products = buildProducts(2);
        when(httpClient.buildProductsUrl(anyString(), any(), eq(null))).thenReturn("http://fake/all");
        when(httpClient.getPage("http://fake/all", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(products, null, 2));

        ProductListData result = service.getAllProducts("org1", "key", Collections.emptyList(), null, null);

        assertEquals(2, result.products.size());
        verify(httpClient).buildProductsUrl("org1", null, null);
    }

    @Test
    void getAllProducts_singleShardList_behavesLikeSingleFilter() throws IOException {
        JsonArray products = buildProducts(3);
        when(httpClient.buildProductsUrl(anyString(), any(), eq("='PISGRV':'Baby'"))).thenReturn("http://fake/baby");
        when(httpClient.getPage("http://fake/baby", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(products, null, 3));

        ProductListData result = service.getAllProducts(
                "org1", "key", List.of("='PISGRV':'Baby'"), null, null);

        assertEquals(3, result.products.size());
    }

    @Test
    void getAllProducts_multiShard_aggregatesAcrossShards() throws IOException {
        JsonArray shard1Products = buildProducts(2);
        JsonArray shard2Products = buildProducts(3);
        when(httpClient.buildProductsUrl(anyString(), any(), eq("='PISGRV':'Baby','PIDISC':'P'")))
                .thenReturn("http://fake/baby-p");
        when(httpClient.buildProductsUrl(anyString(), any(), eq("='PISGRV':'Wed','PIDISC':'P'")))
                .thenReturn("http://fake/wed-p");
        when(httpClient.getPage("http://fake/baby-p", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(shard1Products, null, 2));
        when(httpClient.getPage("http://fake/wed-p", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(shard2Products, null, 3));

        ProductListData result = service.getAllProducts(
                "org1", "key",
                List.of("='PISGRV':'Baby','PIDISC':'P'", "='PISGRV':'Wed','PIDISC':'P'"),
                null, null);

        assertEquals(5, result.products.size());
        assertEquals(5, result.totalCount);  // sum of disjoint shard totals
        verify(httpClient).getPage("http://fake/baby-p", "key");
        verify(httpClient).getPage("http://fake/wed-p", "key");
    }

    @Test
    void getAllProducts_multiShard_oneShardFails_propagatesException() throws IOException {
        when(httpClient.buildProductsUrl(anyString(), any(), eq("='PISGRV':'Baby'")))
                .thenReturn("http://fake/baby");
        when(httpClient.buildProductsUrl(anyString(), any(), eq("='PISGRV':'Wed'")))
                .thenReturn("http://fake/wed");
        when(httpClient.getPage("http://fake/baby", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(buildProducts(1), null, 1));
        when(httpClient.getPage("http://fake/wed", "key"))
                .thenThrow(new IOException("shard failed"));

        IOException ex = assertThrows(IOException.class,
                () -> service.getAllProducts("org1", "key",
                        List.of("='PISGRV':'Baby'", "='PISGRV':'Wed'"), null, null));

        assertTrue(ex.getMessage().contains("shard failed"));
    }

    @Test
    void getAllProducts_multiShardWithOffset_throwsValidationError() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.getAllProducts("org1", "key",
                        List.of("='PISGRV':'Baby'", "='PISGRV':'Wed'"), null, 100));

        assertTrue(ex.getMessage().contains("Offset"));
        assertTrue(ex.getMessage().contains("multi"));
    }

    @Test
    void getAllProducts_singleShardWithOffset_usesOffsetPagination() throws IOException {
        when(httpClient.buildProductsUrlWithOffset("org1", 50, "='PISGRV':'Baby'", 100))
                .thenReturn("http://fake/baby-offset");
        when(httpClient.getPage("http://fake/baby-offset", "key"))
                .thenReturn(new SalsifyHttpClient.PageResult(buildProducts(2), null, 2));

        ProductListData result = service.getAllProducts(
                "org1", "key", List.of("='PISGRV':'Baby'"), 50, 100);

        assertEquals(2, result.products.size());
        verify(httpClient).buildProductsUrlWithOffset("org1", 50, "='PISGRV':'Baby'", 100);
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private JsonArray buildProducts(int count) {
        JsonArray arr = new JsonArray();
        for (int i = 0; i < count; i++) {
            JsonObject p = new JsonObject();
            p.addProperty("salsify:id", "id-" + i);
            p.addProperty("Item#", "ITEM-" + i);
            arr.add(p);
        }
        return arr;
    }
}
