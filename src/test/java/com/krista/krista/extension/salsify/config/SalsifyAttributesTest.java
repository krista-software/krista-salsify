package com.krista.krista.extension.salsify.config;

import app.krista.extension.executor.Invoker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalsifyAttributesTest {

    @Mock
    private Invoker invoker;

    // ─── getOrgId ────────────────────────────────────────────────────────────

    @Test
    void getOrgId_present_returnsValue() {
        when(invoker.getAttributes()).thenReturn(Map.of(
                AttributeKeys.ORG_ID, "org1",
                AttributeKeys.API_KEY, "key1"
        ));
        SalsifyAttributes attrs = new SalsifyAttributes(invoker);

        assertEquals("org1", attrs.getOrgId());
    }

    @Test
    void getOrgId_missing_throwsIllegalStateException() {
        when(invoker.getAttributes()).thenReturn(Map.of(AttributeKeys.API_KEY, "key1"));
        SalsifyAttributes attrs = new SalsifyAttributes(invoker);

        assertThrows(IllegalStateException.class, attrs::getOrgId);
    }

    @Test
    void getOrgId_blank_throwsIllegalStateException() {
        when(invoker.getAttributes()).thenReturn(Map.of(
                AttributeKeys.ORG_ID, "   ",
                AttributeKeys.API_KEY, "key1"
        ));
        SalsifyAttributes attrs = new SalsifyAttributes(invoker);

        assertThrows(IllegalStateException.class, attrs::getOrgId);
    }

    // ─── getApiKey ───────────────────────────────────────────────────────────

    @Test
    void getApiKey_present_returnsValue() {
        when(invoker.getAttributes()).thenReturn(Map.of(
                AttributeKeys.ORG_ID, "org1",
                AttributeKeys.API_KEY, "secret"
        ));
        SalsifyAttributes attrs = new SalsifyAttributes(invoker);

        assertEquals("secret", attrs.getApiKey());
    }

    @Test
    void getApiKey_missing_throwsIllegalStateException() {
        when(invoker.getAttributes()).thenReturn(Map.of(AttributeKeys.ORG_ID, "org1"));
        SalsifyAttributes attrs = new SalsifyAttributes(invoker);

        assertThrows(IllegalStateException.class, attrs::getApiKey);
    }

    // ─── update ──────────────────────────────────────────────────────────────

    @Test
    void update_replacesAttributes() {
        when(invoker.getAttributes()).thenReturn(Map.of(
                AttributeKeys.ORG_ID, "org-old",
                AttributeKeys.API_KEY, "key-old"
        ));
        SalsifyAttributes attrs = new SalsifyAttributes(invoker);

        attrs.update(Map.of(
                AttributeKeys.ORG_ID, "org-new",
                AttributeKeys.API_KEY, "key-new"
        ));

        assertEquals("org-new", attrs.getOrgId());
        assertEquals("key-new", attrs.getApiKey());
    }

    // ─── null value in map ───────────────────────────────────────────────────

    @Test
    void getOrgId_nullValue_throwsIllegalStateException() {
        Map<String, Object> map = new HashMap<>();
        map.put(AttributeKeys.ORG_ID, null);
        map.put(AttributeKeys.API_KEY, "key1");
        when(invoker.getAttributes()).thenReturn(map);
        SalsifyAttributes attrs = new SalsifyAttributes(invoker);

        assertThrows(IllegalStateException.class, attrs::getOrgId);
    }
}
