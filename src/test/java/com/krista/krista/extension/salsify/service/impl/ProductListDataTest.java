package com.krista.krista.extension.salsify.service.impl;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductListDataTest {

    @Test
    void constructor_setsAllFields() {
        JsonObject p = new JsonObject();
        p.addProperty("salsify:id", "123");
        List<JsonObject> products = List.of(p);
        List<String> headers = List.of("salsify:id", "Item#");

        ProductListData data = new ProductListData(products, headers, 42, true);

        assertEquals(1, data.products.size());
        assertEquals(2, data.columnHeaders.size());
        assertEquals(42, data.totalCount);
        assertTrue(data.exportRecommended);
    }

    @Test
    void constructor_exportNotRecommended() {
        ProductListData data = new ProductListData(List.of(), List.of(), 0, false);

        assertFalse(data.exportRecommended);
        assertEquals(0, data.totalCount);
        assertTrue(data.products.isEmpty());
        assertTrue(data.columnHeaders.isEmpty());
    }
}
