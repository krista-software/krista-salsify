package com.krista.krista.extension.salsify.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

class AttributeKeysTest {

    @Test
    void constructor_isPrivate() throws Exception {
        Constructor<AttributeKeys> c = AttributeKeys.class.getDeclaredConstructor();
        c.setAccessible(true);
        assertThrows(InvocationTargetException.class, c::newInstance);
    }

    @Test
    void orgId_hasExpectedValue() {
        assertEquals("Salsify Org ID", AttributeKeys.ORG_ID);
    }

    @Test
    void apiKey_hasExpectedValue() {
        assertEquals("Salsify API Key", AttributeKeys.API_KEY);
    }
}
