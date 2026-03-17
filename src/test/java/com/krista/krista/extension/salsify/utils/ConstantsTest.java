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
