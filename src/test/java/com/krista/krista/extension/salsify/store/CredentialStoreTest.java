package com.krista.krista.extension.salsify.store;

import app.krista.extension.executor.Invoker;
import app.krista.extensions.util.KeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CredentialStoreTest {

    @Mock
    private KeyValueStore keyValueStore;

    @Mock
    private Invoker invoker;

    private CredentialStore store;

    private static final String INVOKER_ID = "invoker-123";
    private static final String EXPECTED_KEY = INVOKER_ID + "_salsify_credentials";

    @BeforeEach
    void setUp() {
        when(invoker.getInvokerId()).thenReturn(INVOKER_ID);
        store = new CredentialStore(keyValueStore, invoker);
    }

    // ─── saveCredentials ─────────────────────────────────────────────────────

    @Test
    void saveCredentials_putsJsonToKvs() {
        store.saveCredentials("org1", "secret-key");

        verify(keyValueStore).put(eq(EXPECTED_KEY), argThat(json ->
                json.toString().contains("org1") && json.toString().contains("Salsify Org ID")));
    }

    // ─── getOrgId ────────────────────────────────────────────────────────────

    @Test
    void getOrgId_credentialsStored_returnsOrgId() {
        when(keyValueStore.get(EXPECTED_KEY))
                .thenReturn("{\"Salsify Org ID\":\"org1\",\"Salsify API Key\":\"key1\"}");

        assertEquals("org1", store.getOrgId());
    }

    @ParameterizedTest
    @MethodSource("provideNullCasesForGetOrgId")
    void getOrgId_nullCases_returnsNull(String kvsValue) {
        when(keyValueStore.get(EXPECTED_KEY)).thenReturn(kvsValue);

        assertNull(store.getOrgId());
    }

    // ─── getApiKey ───────────────────────────────────────────────────────────

    @Test
    void getApiKey_credentialsStored_returnsApiKey() {
        when(keyValueStore.get(EXPECTED_KEY))
                .thenReturn("{\"Salsify Org ID\":\"org1\",\"Salsify API Key\":\"secret\"}");

        assertEquals("secret", store.getApiKey());
    }

    @Test
    void getApiKey_kvsReturnsNull_returnsNull() {
        when(keyValueStore.get(EXPECTED_KEY)).thenReturn(null);

        assertNull(store.getApiKey());
    }

    @Test
    void getApiKey_jsonMissingApiKeyKey_returnsNull() {
        when(keyValueStore.get(EXPECTED_KEY))
                .thenReturn("{\"Salsify Org ID\":\"org1\"}");

        assertNull(store.getApiKey());
    }

    // ─── removeCredentials ───────────────────────────────────────────────────

    @Test
    void removeCredentials_callsKvsRemove() {
        store.removeCredentials();

        verify(keyValueStore).remove(EXPECTED_KEY);
    }

    // ─── Data provider for parameterized tests ───────────────────────────────

    private static Stream<String> provideNullCasesForGetOrgId() {
        return Stream.of(
            null,
            "{\"Salsify API Key\":\"key1\"}",
            "not-valid-json}}{{"
        );
    }
}
