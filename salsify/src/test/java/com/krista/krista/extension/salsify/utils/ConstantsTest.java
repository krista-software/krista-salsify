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

package com.krista.krista.extension.salsify.utils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

class ConstantsTest {

    @Test
    void constructor_isPrivate() throws Exception {
        Constructor<Constants> c = Constants.class.getDeclaredConstructor();
        c.setAccessible(true);
        assertThrows(InvocationTargetException.class, c::newInstance);
    }

    @Test
    void productsPath_containsOrgIdPlaceholder() {
        assertTrue(Constants.PRODUCTS_PATH.contains("%s"));
    }

    @Test
    void bearerPrefix_endsWithSpace() {
        assertTrue(Constants.BEARER_PREFIX.endsWith(" "));
    }

    @Test
    void rateLimitBackoff_hasThreeEntries() {
        assertEquals(3, Constants.RATE_LIMIT_BACKOFF_MS.length);
    }

    @Test
    void largeCatalogThreshold_isTenThousand() {
        assertEquals(10_000, Constants.LARGE_CATALOG_THRESHOLD);
    }

    @Test
    void statusDiscontinued_isY() {
        assertEquals("Y", Constants.STATUS_DISCONTINUED);
    }

    @Test
    void statusActive_isN() {
        assertEquals("N", Constants.STATUS_ACTIVE);
    }
}
