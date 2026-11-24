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

package com.krista.krista.extension.salsify;

import app.krista.extension.executor.Invoker;
import com.krista.krista.extension.salsify.config.AttributeKeys;
import com.krista.krista.extension.salsify.config.SalsifyAttributes;
import com.krista.krista.extension.salsify.connection.SalsifyHttpClient;
import com.krista.krista.extension.salsify.store.CredentialStore;
import com.krista.krista.extension.salsify.utils.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalsifyExtensionTest {

    @Mock private Invoker invoker;
    @Mock private SalsifyAttributes decopcAttributes;
    @Mock private SalsifyHttpClient httpClient;
    @Mock private CredentialStore credentialStore;

    private SalsifyExtension extension;

    @BeforeEach
    void setUp() {
        when(invoker.getInvokerId()).thenReturn("invoker-1");
        extension = new SalsifyExtension(invoker, decopcAttributes, httpClient, credentialStore);
    }

    // ─── validateAttributes ──────────────────────────────────────────────────

    @Test
    void validateAttributes_bothPresent_probesApiAndPasses() throws IOException {
        when(httpClient.buildProductsUrl("org1", 1, null)).thenReturn("http://fake/probe");
        when(httpClient.getPage("http://fake/probe", "apikey"))
                .thenReturn(new SalsifyHttpClient.PageResult(new com.google.gson.JsonArray(), null, 0));

        assertDoesNotThrow(this::validateAttributesValid);
    }

    @ParameterizedTest
    @MethodSource("provideInvalidAttributesCases")
    void validateAttributes_invalidCases_throwsException(String caseDescription, Map<String, Object> attrs, boolean checkBothPresent) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                extension.validateAttributes(attrs));
        if (checkBothPresent) {
            assertTrue(ex.getMessage().contains("Org ID"));
            assertTrue(ex.getMessage().contains("API Key"));
        }
    }

    @Test
    void validateAttributes_api401_throwsIOExceptionWithInvalidTokenMessage() throws IOException {
        when(httpClient.buildProductsUrl("org1", 1, null)).thenReturn("http://fake/probe");
        when(httpClient.getPage(anyString(), anyString()))
                .thenThrow(new SalsifyHttpClient.ClientErrorException(Constants.ERR_INVALID_TOKEN));

        IOException ex = assertThrows(IOException.class, this::validateAttributesBadKey);
        assertTrue(ex.getMessage().contains("401") || ex.getMessage().contains("Invalid"));
    }

    @Test
    void validateAttributes_api403_throwsIOExceptionWithForbiddenMessage() throws IOException {
        when(httpClient.buildProductsUrl("org1", 1, null)).thenReturn("http://fake/probe");
        when(httpClient.getPage(anyString(), anyString()))
                .thenThrow(new SalsifyHttpClient.ClientErrorException("403 forbidden"));

        IOException ex = assertThrows(IOException.class, this::validateAttributesBadKey);
        assertTrue(ex.getMessage().contains("403") || ex.getMessage().contains("Access denied"));
    }

    @Test
    void validateAttributes_api404_throwsIOExceptionWithNotFoundMessage() throws IOException {
        when(httpClient.buildProductsUrl("org1", 1, null)).thenReturn("http://fake/probe");
        when(httpClient.getPage(anyString(), anyString()))
                .thenThrow(new SalsifyHttpClient.ClientErrorException("404 not found"));

        IOException ex = assertThrows(IOException.class, this::validateAttributesBadKey);
        assertTrue(ex.getMessage().contains("404") || ex.getMessage().contains("not found"));
    }

    @Test
    void validateAttributes_apiNullMessage_throwsIOExceptionWithUnknownMessage() throws IOException {
        when(httpClient.buildProductsUrl("org1", 1, null)).thenReturn("http://fake/probe");
        when(httpClient.getPage(anyString(), anyString()))
                .thenThrow(new SalsifyHttpClient.ClientErrorException(null));

        IOException ex = assertThrows(IOException.class, this::validateAttributesBadKey);
        assertEquals(Constants.ERR_UNKNOWN, ex.getMessage());
    }

    @Test
    void validateAttributes_otherClientError_throwsIOExceptionWithDetails() throws IOException {
        when(httpClient.buildProductsUrl("org1", 1, null)).thenReturn("http://fake/probe");
        when(httpClient.getPage(anyString(), anyString()))
                .thenThrow(new SalsifyHttpClient.ClientErrorException("Some unexpected error"));

        IOException ex = assertThrows(IOException.class, this::validateAttributesBadKey);
        assertTrue(ex.getMessage().contains("Details"));
    }

    @Test
    void validateAttributes_networkError_throwsIOExceptionWithUnknown() throws IOException {
        when(httpClient.buildProductsUrl("org1", 1, null)).thenReturn("http://fake/probe");
        when(httpClient.getPage(anyString(), anyString()))
                .thenThrow(new IOException("Connection refused"));

        IOException ex = assertThrows(IOException.class, this::validateAttributesValidCredentials);
        assertTrue(ex.getMessage().startsWith(Constants.ERR_UNKNOWN));
    }

    @Test
    void validateAttributes_api401WithoutConstantMessage_throwsIOExceptionWithInvalidToken() throws IOException {
        when(httpClient.buildProductsUrl("org1", 1, null)).thenReturn("http://fake/probe");
        when(httpClient.getPage(anyString(), anyString()))
                .thenThrow(new SalsifyHttpClient.ClientErrorException("401 Unauthorized"));

        IOException ex = assertThrows(IOException.class, this::validateAttributesBadKey);
        assertTrue(ex.getMessage().contains("Invalid") || ex.getMessage().contains("401"));
    }

    @Test
    void validateAttributes_api403WithoutConstantMessage_throwsIOExceptionWithForbidden() throws IOException {
        when(httpClient.buildProductsUrl("org1", 1, null)).thenReturn("http://fake/probe");
        when(httpClient.getPage(anyString(), anyString()))
                .thenThrow(new SalsifyHttpClient.ClientErrorException("403 Forbidden"));

        IOException ex = assertThrows(IOException.class, this::validateAttributesBadKey);
        assertTrue(ex.getMessage().contains("Access") || ex.getMessage().contains("403"));
    }

    @Test
    void validateAttributes_api404WithoutConstantMessage_throwsIOExceptionWithNotFound() throws IOException {
        when(httpClient.buildProductsUrl("org1", 1, null)).thenReturn("http://fake/probe");
        when(httpClient.getPage(anyString(), anyString()))
                .thenThrow(new SalsifyHttpClient.ClientErrorException("404 Not Found"));

        IOException ex = assertThrows(IOException.class, this::validateAttributesBadKey);
        assertTrue(ex.getMessage().contains("Resource") || ex.getMessage().contains("404"));
    }

    @Test
    void validateAttributes_unknownError_throwsIOExceptionWithDetails() throws IOException {
        when(httpClient.buildProductsUrl("org1", 1, null)).thenReturn("http://fake/probe");
        when(httpClient.getPage(anyString(), anyString()))
                .thenThrow(new SalsifyHttpClient.ClientErrorException("500 Internal Server Error"));

        IOException ex = assertThrows(IOException.class, this::validateAttributesBadKey);
        assertTrue(ex.getMessage().contains("Details") && ex.getMessage().contains("500"));
    }

    // ─── validateAttributes helper methods ────────────────────────────────────

    private void validateAttributesValid() throws IOException {
        extension.validateAttributes(
                Map.of(AttributeKeys.ORG_ID, "org1", AttributeKeys.API_KEY, "apikey"));
    }


    private void validateAttributesBadKey() throws IOException {
        extension.validateAttributes(Map.of(
                AttributeKeys.ORG_ID, "org1",
                AttributeKeys.API_KEY, "bad-key"));
    }

    private void validateAttributesValidCredentials() throws IOException {
        extension.validateAttributes(Map.of(
                AttributeKeys.ORG_ID, "org1",
                AttributeKeys.API_KEY, "apikey"));
    }

    // ─── attributesUpdated ───────────────────────────────────────────────────

    @Test
    void attributesUpdated_validCredentials_savesCredentials() {
        extension.attributesUpdated(Map.of(), Map.of(
                AttributeKeys.ORG_ID, "org1",
                AttributeKeys.API_KEY, "apikey"));

        verify(decopcAttributes).update(any());
        verify(credentialStore).saveCredentials("org1", "apikey");
    }

    @ParameterizedTest
    @MethodSource("provideInvalidAttributesForUpdate")
    void attributesUpdated_invalidCredentials_doesNotSaveCredentials(String description, Map<String, Object> attrs) {
        extension.attributesUpdated(Map.of(), attrs);
        verify(credentialStore, never()).saveCredentials(any(), any());
    }

    // ─── testConnection ──────────────────────────────────────────────────────

    @Test
    void testConnection_validCredentials_passes() throws IOException {
        when(credentialStore.getOrgId()).thenReturn("org1");
        when(credentialStore.getApiKey()).thenReturn("apikey");
        when(httpClient.buildProductsUrl("org1", 1, null)).thenReturn("http://fake/probe");
        when(httpClient.getPage(anyString(), anyString()))
                .thenReturn(new SalsifyHttpClient.PageResult(new com.google.gson.JsonArray(), null, 0));

        assertDoesNotThrow(extension::testConnection);
    }

    @Test
    void testConnection_noCredentials_throwsIllegalStateException() {
        when(credentialStore.getOrgId()).thenReturn(null);

        assertThrows(IllegalStateException.class, extension::testConnection);
    }

    @Test
    void testConnection_blankOrgId_throwsIllegalStateException() {
        when(credentialStore.getOrgId()).thenReturn("  ");

        assertThrows(IllegalStateException.class, extension::testConnection);
    }

    @Test
    void testConnection_nullApiKey_throwsIllegalStateException() {
        when(credentialStore.getOrgId()).thenReturn("org1");
        when(credentialStore.getApiKey()).thenReturn(null);

        assertThrows(IllegalStateException.class, extension::testConnection);
    }

    @Test
    void testConnection_blankApiKey_throwsIllegalStateException() {
        when(credentialStore.getOrgId()).thenReturn("org1");
        when(credentialStore.getApiKey()).thenReturn("");

        assertThrows(IllegalStateException.class, extension::testConnection);
    }

    @Test
    void testConnection_apiError_throwsIOException() throws IOException {
        when(credentialStore.getOrgId()).thenReturn("org1");
        when(credentialStore.getApiKey()).thenReturn("apikey");
        when(httpClient.buildProductsUrl("org1", 1, null)).thenReturn("http://fake/probe");
        when(httpClient.getPage(anyString(), anyString()))
                .thenThrow(new SalsifyHttpClient.ClientErrorException(Constants.ERR_INVALID_TOKEN));

        assertThrows(IOException.class, extension::testConnection);
    }

    // ─── invokerRemoved ──────────────────────────────────────────────────────

    @Test
    void invokerRemoved_callsRemoveCredentials() {
        extension.invokerRemoved();

        verify(credentialStore).removeCredentials();
    }

    // ─── customTabs ──────────────────────────────────────────────────────────

    @Test
    void customTabs_returnsDocumentationEntry() {
        Map<String, String> tabs = extension.customTabs();

        assertEquals("static/docs", tabs.get("Documentation"));
    }

    // ─── mapApiError (private static method via reflection) ─────────────────

    @ParameterizedTest
    @MethodSource("provideMapApiErrorTestCases")
    void mapApiError_variousCases_returnsCorrectMessage(String input, String expectedContains) throws Exception {
        Method mapApiErrorMethod = SalsifyExtension.class.getDeclaredMethod("mapApiError", String.class);
        mapApiErrorMethod.setAccessible(true);
        String result = (String) mapApiErrorMethod.invoke(null, input);

        assertTrue(result.contains(expectedContains),
                "Expected result to contain '" + expectedContains + "' but got '" + result + "'");
    }

    // ─── Data providers ───────────────────────────────────────────────────────

    private static Stream<Arguments> provideInvalidAttributesCases() {
        return Stream.of(
            Arguments.of("missing org id", Map.<String, Object>of(AttributeKeys.API_KEY, "apikey"), false),
            Arguments.of("blank org id", Map.<String, Object>of(AttributeKeys.ORG_ID, "", AttributeKeys.API_KEY, "apikey"), false),
            Arguments.of("missing api key", Map.<String, Object>of(AttributeKeys.ORG_ID, "org1"), false),
            Arguments.of("blank api key", Map.<String, Object>of(AttributeKeys.ORG_ID, "org1", AttributeKeys.API_KEY, "  "), false),
            Arguments.of("both missing", Map.of(), true)
        );
    }

    private static Stream<Arguments> provideMapApiErrorTestCases() {
        return Stream.of(
            // Branch: null input
            Arguments.of(null, Constants.ERR_UNKNOWN),

            // Branch 401: contains only "401" (first condition true)
            Arguments.of("401 Unauthorized", Constants.ERR_INVALID_TOKEN),

            // Branch 401: contains only Constants.ERR_INVALID_TOKEN (second condition true, first false)
            Arguments.of(Constants.ERR_INVALID_TOKEN, Constants.ERR_INVALID_TOKEN),

            // Branch 401: contains both "401" and Constants.ERR_INVALID_TOKEN
            Arguments.of("401 " + Constants.ERR_INVALID_TOKEN, Constants.ERR_INVALID_TOKEN),

            // Branch 403: contains only "403" (first condition true)
            Arguments.of("403 Forbidden", Constants.ERR_FORBIDDEN),

            // Branch 403: contains only Constants.ERR_FORBIDDEN (second condition true, first false)
            Arguments.of(Constants.ERR_FORBIDDEN, Constants.ERR_FORBIDDEN),

            // Branch 403: contains both "403" and Constants.ERR_FORBIDDEN
            Arguments.of("403 " + Constants.ERR_FORBIDDEN, Constants.ERR_FORBIDDEN),

            // Branch 404: contains only "404" (first condition true)
            Arguments.of("404 Not Found", Constants.ERR_NOT_FOUND),

            // Branch 404: contains only Constants.ERR_NOT_FOUND (second condition true, first false)
            Arguments.of(Constants.ERR_NOT_FOUND, Constants.ERR_NOT_FOUND),

            // Branch 404: contains both "404" and Constants.ERR_NOT_FOUND
            Arguments.of("404 " + Constants.ERR_NOT_FOUND, Constants.ERR_NOT_FOUND),

            // Branch: default case (unrecognized error)
            Arguments.of("Some unexpected error message", Constants.ERR_UNKNOWN)
        );
    }

    private static Stream<Arguments> provideInvalidAttributesForUpdate() {
        return Stream.of(
            Arguments.of("blank org id", Map.of(AttributeKeys.ORG_ID, "  ", AttributeKeys.API_KEY, "apikey")),
            Arguments.of("missing api key", Map.of(AttributeKeys.ORG_ID, "org1")),
            Arguments.of("null org id", Map.of(AttributeKeys.API_KEY, "apikey")),
            Arguments.of("blank api key", Map.of(AttributeKeys.ORG_ID, "org1", AttributeKeys.API_KEY, "  "))
        );
    }
}
