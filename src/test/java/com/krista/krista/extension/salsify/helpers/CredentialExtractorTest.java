package com.krista.krista.extension.salsify.helpers;

import com.krista.krista.extension.salsify.store.CredentialStore;
import com.krista.krista.extension.salsify.utils.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CredentialExtractorTest {

    @Mock private CredentialStore credentialStore;

    private CredentialExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new CredentialExtractor(credentialStore);
    }

    // ─── extractOrgId Tests ──────────────────────────────────────────────────

    @Test
    void extractOrgId_validOrgId_returnsOrgId() throws IOException {
        when(credentialStore.getOrgId()).thenReturn("test-org-123");

        String result = extractor.extractOrgId();

        assertEquals("test-org-123", result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void extractOrgId_invalidOrgId_throwsIOException(String invalidOrgId) {
        when(credentialStore.getOrgId()).thenReturn(invalidOrgId);

        IOException exception = assertThrows(IOException.class, () -> extractor.extractOrgId());
        assertEquals(Constants.ERR_NO_CREDENTIALS, exception.getMessage());
    }

    @Test
    void extractOrgId_nullOrgId_throwsIOException() {
        when(credentialStore.getOrgId()).thenReturn(null);

        IOException exception = assertThrows(IOException.class, () -> extractor.extractOrgId());
        assertEquals(Constants.ERR_NO_CREDENTIALS, exception.getMessage());
    }

    // ─── extractApiKey Tests ─────────────────────────────────────────────────

    @Test
    void extractApiKey_validApiKey_returnsApiKey() throws IOException {
        when(credentialStore.getApiKey()).thenReturn("test-api-key-456");

        String result = extractor.extractApiKey();

        assertEquals("test-api-key-456", result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void extractApiKey_invalidApiKey_throwsIOException(String invalidApiKey) {
        when(credentialStore.getApiKey()).thenReturn(invalidApiKey);

        IOException exception = assertThrows(IOException.class, () -> extractor.extractApiKey());
        assertEquals(Constants.ERR_NO_CREDENTIALS, exception.getMessage());
    }

    @Test
    void extractApiKey_nullApiKey_throwsIOException() {
        when(credentialStore.getApiKey()).thenReturn(null);

        IOException exception = assertThrows(IOException.class, () -> extractor.extractApiKey());
        assertEquals(Constants.ERR_NO_CREDENTIALS, exception.getMessage());
    }
}
