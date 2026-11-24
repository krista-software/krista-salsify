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
