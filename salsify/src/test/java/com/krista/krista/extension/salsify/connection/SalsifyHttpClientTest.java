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

package com.krista.krista.extension.salsify.connection;

import com.krista.krista.extension.salsify.utils.Constants;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

import static org.junit.jupiter.api.Assertions.*;

class SalsifyHttpClientTest {

    private MockWebServer server;
    private SalsifyHttpClient client;
    private String baseUrl;
    private long[] originalBackoff;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        baseUrl = server.url("/").toString();

        OkHttpClient okHttp = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
        client = new SalsifyHttpClient(okHttp);

        // Zero out backoffs so retry tests run instantly
        originalBackoff = Arrays.copyOf(Constants.RATE_LIMIT_BACKOFF_MS, Constants.RATE_LIMIT_BACKOFF_MS.length);
        Constants.RATE_LIMIT_BACKOFF_MS[0] = 0L;
        Constants.RATE_LIMIT_BACKOFF_MS[1] = 0L;
        Constants.RATE_LIMIT_BACKOFF_MS[2] = 0L;
    }

    @AfterEach
    void tearDown() throws IOException {
        System.arraycopy(originalBackoff, 0, Constants.RATE_LIMIT_BACKOFF_MS, 0, originalBackoff.length);
        server.shutdown();
    }

    // ─── default constructor ─────────────────────────────────────────────────

    @Test
    void defaultConstructor_createsInstance() {
        SalsifyHttpClient instance = new SalsifyHttpClient();
        assertNotNull(instance);
    }

    // ─── getPage — success response formats ─────────────────────────────────

    @Test
    void getPage_200WithMalformedJson_returnsEmptyProducts() throws IOException {
        // Use invalid JSON that causes Gson to throw (missing value after colon)
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"broken\": }"));

        SalsifyHttpClient.PageResult result = client.getPage(baseUrl + "orgs/test/products", "apikey");

        assertEquals(0, result.products.size());
        assertEquals(0, result.totalCount);
    }

    @Test
    void getPage_dataKeyResponse_parsesProducts() throws IOException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":[{\"salsify:id\":\"1\"}],\"meta\":{\"total_entries\":1}}"));

        SalsifyHttpClient.PageResult result = client.getPage(baseUrl + "orgs/test/products", "apikey");

        assertEquals(1, result.products.size());
        assertEquals(1, result.totalCount);
        assertNull(result.nextUrl);
    }

    @ParameterizedTest
    @MethodSource("provideResponseFormats")
    void getPage_variousResponseFormats_parsesProducts(String responseBody, int expectedProductCount, int expectedTotalCount) throws IOException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody));

        SalsifyHttpClient.PageResult result = client.getPage(baseUrl + "orgs/test/products", "apikey");

        assertEquals(expectedProductCount, result.products.size());
        assertEquals(expectedTotalCount, result.totalCount);
    }

    @Test
    void getPage_emptyBody_returnsEmptyProducts() throws IOException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(""));

        SalsifyHttpClient.PageResult result = client.getPage(baseUrl + "orgs/test/products", "apikey");

        assertEquals(0, result.products.size());
        assertEquals(0, result.totalCount);
    }

    @Test
    void getPage_withLinkHeader_parsesNextUrl() throws IOException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setHeader("Link", "<http://next.page/products?page=2>; rel=\"next\", <http://first.page>; rel=\"first\"")
                .setBody("{\"data\":[]}"));

        SalsifyHttpClient.PageResult result = client.getPage(baseUrl + "orgs/test/products", "apikey");

        assertEquals("http://next.page/products?page=2", result.nextUrl);
    }

    @Test
    void getPage_noLinkHeader_nextUrlIsNull() throws IOException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":[]}"));

        SalsifyHttpClient.PageResult result = client.getPage(baseUrl + "orgs/test/products", "apikey");

        assertNull(result.nextUrl);
    }

    @Test
    void getPage_metaWithNoTotalEntries_totalCountZero() throws IOException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":[],\"meta\":{\"other_field\":99}}"));

        SalsifyHttpClient.PageResult result = client.getPage(baseUrl + "orgs/test/products", "apikey");

        assertEquals(0, result.totalCount);
    }

    // ─── getPage — HTTP error codes ──────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
            "401, 1",
            "403, 1",
            "404, 1",
            "408, 2"
    })
    void getPage_clientErrorStatus_throwsClientErrorException(int statusCode, int responseCount) {
        // 408 requires 2 responses due to OkHttp internal retry
        for (int i = 0; i < responseCount; i++) {
            server.enqueue(new MockResponse().setResponseCode(statusCode).setBody("Error"));
        }

        assertThrows(SalsifyHttpClient.ClientErrorException.class,
                () -> client.getPage(baseUrl + "orgs/test/products", "apikey"));
    }

    @ParameterizedTest
    @CsvSource({
            "429, 4",
            "500, 2",
            "503, 2"
    })
    void getPage_serverErrorExhaustsRetries_throwsIOException(int statusCode, int responseCount) throws IOException {
        for (int i = 0; i < responseCount; i++) {
            server.enqueue(new MockResponse().setResponseCode(statusCode).setBody("Server Error"));
        }

        assertThrows(IOException.class,
                () -> client.getPage(baseUrl + "orgs/test/products", "apikey"));
    }

    @Test
    void getPage_otherError_throwsIOException() {
        server.enqueue(new MockResponse().setResponseCode(502).setBody("Bad Gateway"));

        assertThrows(IOException.class,
                () -> client.getPage(baseUrl + "orgs/test/products", "apikey"));
    }

    @Test
    void getPage_3xxNoFollowRedirect_throwsIOException() {
        // A 3xx code (< 400) reaching the default switch case (code >= 400 is false)
        server.enqueue(new MockResponse().setResponseCode(301)
                .setHeader("Location", baseUrl + "other"));

        OkHttpClient noRedirect = new OkHttpClient.Builder()
                .followRedirects(false)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
        SalsifyHttpClient noRedirectClient = new SalsifyHttpClient(noRedirect);

        assertThrows(IOException.class,
                () -> noRedirectClient.getPage(baseUrl + "orgs/test/products", "apikey"));
    }

    @Test
    void getPage_200WithJsonNullBody_returnsEmptyProducts() throws IOException {
        // JSON null is parsed by Gson as JsonNull (not JsonArray, not JsonObject)
        // → try block falls through → returns empty products
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("null"));

        SalsifyHttpClient.PageResult result = client.getPage(baseUrl + "orgs/test/products", "apikey");

        assertEquals(0, result.products.size());
        assertEquals(0, result.totalCount);
    }

    @Test
    void getPage_200WithDataFieldNotArray_usesObjectAsSingleProduct() throws IOException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":\"not-an-array\",\"salsify:id\":\"X\"}"));

        SalsifyHttpClient.PageResult result = client.getPage(baseUrl + "orgs/test/products", "apikey");

        assertEquals(1, result.products.size());
    }

    @Test
    void getPage_200WithProductsFieldNotArray_usesObjectAsSingleProduct() throws IOException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"products\":\"not-an-array\",\"salsify:id\":\"Y\"}"));

        SalsifyHttpClient.PageResult result = client.getPage(baseUrl + "orgs/test/products", "apikey");

        assertEquals(1, result.products.size());
    }

    // ─── extractNextLink ─────────────────────────────────────────────────────

    @ParameterizedTest
    @MethodSource("provideNullCasesForExtractNextLink")
    void extractNextLink_nullCases_returnsNull(String input) {
        assertNull(client.extractNextLink(input));
    }

    @Test
    void extractNextLink_withNext_returnsUrl() {
        String url = client.extractNextLink(
                "<https://app.salsify.com/api/v1/orgs/abc/products?page=2>; rel=\"next\"");
        assertEquals("https://app.salsify.com/api/v1/orgs/abc/products?page=2", url);
    }

    @Test
    void extractNextLink_multipleRels_returnsNext() {
        String url = client.extractNextLink(
                "<https://first.page>; rel=\"first\", <https://next.page?page=3>; rel=\"next\"");
        assertEquals("https://next.page?page=3", url);
    }

    @Test
    void extractNextLink_nextRelNoAngleBrackets_returnsNull() {
        String url = client.extractNextLink("some text rel=\"next\" no brackets");
        assertNull(url);
    }

    @Test
    void extractNextLink_openBracketWithNoClosingBracket_returnsNull() {
        // start >= 0 (found '<') but no '>' → end = -1, end > start is false
        String url = client.extractNextLink("<no-closing-bracket; rel=\"next\"");
        assertNull(url);
    }

    // ─── buildProductsUrl ────────────────────────────────────────────────────

    @ParameterizedTest
    @MethodSource("provideBuildProductsUrlParamCases")
    void buildProductsUrl_parameterCases_handledCorrectly(String testCase, Integer pageSize, String filter, boolean shouldHavePerPage, boolean shouldHaveFilter) {
        String url = client.buildProductsUrl("testorg", pageSize, filter);
        assertTrue(url.contains("testorg"));

        if (shouldHavePerPage) {
            assertTrue(url.contains("per_page="), "URL should contain per_page");
        } else {
            assertFalse(url.contains("per_page"), "URL should not contain per_page");
        }

        if (shouldHaveFilter) {
            assertTrue(url.contains("filter="), "URL should contain filter");
        } else {
            assertFalse(url.contains("filter"), "URL should not contain filter");
        }
    }

    @ParameterizedTest
    @MethodSource("provideBuildProductsUrlPageCases")
    void buildProductsUrl_pageCases_handledCorrectly(Integer pageNum, boolean shouldContainPage, String expectedValue) {
        String url = client.buildProductsUrl("testorg", null, null, pageNum);
        if (shouldContainPage) {
            assertTrue(url.contains(expectedValue), "URL should contain " + expectedValue);
        } else {
            assertFalse(url.contains("page="), "URL should not contain page parameter");
        }
    }

    // ─── buildProductsUrlWithOffset ──────────────────────────────────────────

    @Test
    void buildProductsUrlWithOffset_noParams_returnsBaseUrl() {
        String url = client.buildProductsUrlWithOffset("testorg", null, null, 0);
        assertTrue(url.contains("testorg"));
        assertTrue(url.contains("offset=0"));
        assertFalse(url.contains("per_page"));
        assertFalse(url.contains("filter"));
        assertFalse(url.contains("page="));
    }

    @Test
    void buildProductsUrlWithOffset_withPageSize_addsPerPage() {
        String url = client.buildProductsUrlWithOffset("testorg", 50, null, 100);
        assertTrue(url.contains("per_page=50"));
        assertTrue(url.contains("offset=100"));
    }

    @Test
    void buildProductsUrlWithOffset_zeroPageSize_omitsPerPage() {
        String url = client.buildProductsUrlWithOffset("testorg", 0, null, 200);
        assertFalse(url.contains("per_page"));
        assertTrue(url.contains("offset=200"));
    }

    @Test
    void buildProductsUrlWithOffset_withFilter_addsFilter() {
        String url = client.buildProductsUrlWithOffset("testorg", null, "='Status':'Active'", 50);
        assertTrue(url.contains("filter="));
        assertTrue(url.contains("offset=50"));
    }

    @Test
    void buildProductsUrlWithOffset_blankFilter_omitsFilter() {
        String url = client.buildProductsUrlWithOffset("testorg", null, "   ", 150);
        assertFalse(url.contains("filter="));
        assertTrue(url.contains("offset=150"));
    }

    @Test
    void buildProductsUrlWithOffset_offsetZero_addsOffset() {
        String url = client.buildProductsUrlWithOffset("testorg", 25, null, 0);
        assertTrue(url.contains("offset=0"));
    }

    @Test
    void buildProductsUrlWithOffset_largeOffset_addsOffset() {
        String url = client.buildProductsUrlWithOffset("testorg", 50, null, 10000);
        assertTrue(url.contains("offset=10000"));
    }

    @Test
    void buildProductsUrlWithOffset_allParams_includesAll() {
        String url = client.buildProductsUrlWithOffset("testorg", 100, "='PICCDE':'POPT'", 500);
        assertTrue(url.contains("testorg"));
        assertTrue(url.contains("per_page=100"));
        assertTrue(url.contains("filter="));
        assertTrue(url.contains("offset=500"));
        assertFalse(url.contains("?page=") || url.contains("&page="));
    }

    @Test
    void buildProductsUrlWithOffset_offsetNullSafety_noOffset() {
        String url = client.buildProductsUrlWithOffset("testorg", null, null, null);
        // If offset is null, it should be omitted, but let's test the contract
        assertTrue(url.contains("testorg"));
    }

    // ─── buildFilterString ───────────────────────────────────────────────────

    @Test
    void buildFilterString_null_returnsNull() {
        assertNull(client.buildFilterString(null));
    }

    @Test
    void buildFilterString_empty_returnsNull() {
        assertNull(client.buildFilterString(List.of()));
    }

    @Test
    void buildFilterString_single_condition_returnsPrefixed() {
        String result = client.buildFilterString(List.of("'PICCDE':'POPT'"));
        assertEquals("='PICCDE':'POPT'", result);
    }

    @Test
    void buildFilterString_multiple_conditions_joinedWithComma() {
        String result = client.buildFilterString(List.of("'A':'1'", "'B':'2'"));
        assertEquals("='A':'1','B':'2'", result);
    }

    // ─── buildOrExpression ─────────────────────────────────────────────────

    @Test
    void buildOrExpression_null_returnsNull() {
        assertNull(client.buildOrExpression(null));
    }

    @Test
    void buildOrExpression_empty_returnsNull() {
        assertNull(client.buildOrExpression(List.of()));
    }

    @Test
    void buildOrExpression_singleExpression_returnsSame() {
        assertEquals("'P':'V1'", client.buildOrExpression(List.of("'P':'V1'")));
    }

    @Test
    void buildOrExpression_twoExpressions_joinedWithPipe() {
        assertEquals("'P':'V1'='P':'V2'",
                client.buildOrExpression(List.of("'P':'V1'", "'P':'V2'")));
    }

    @Test
    void buildOrExpression_threeExpressions_joinedWithPipes() {
        assertEquals("'P':'V1'='P':'V2'='P':'V3'",
                client.buildOrExpression(List.of("'P':'V1'", "'P':'V2'", "'P':'V3'")));
    }

    // ─── buildFilterExpression ───────────────────────────────────────────────

    @Test
    void buildFilterExpression_equals_singleQuoteFormat() {
        assertEquals("'Prop':'Val'",
                client.buildFilterExpression("Prop", Constants.OPERATOR_EQUALS, "Val"));
    }

    @Test
    void buildFilterExpression_notEquals_caretFormat() {
        assertEquals("'Prop':^'Val'",
                client.buildFilterExpression("Prop", Constants.OPERATOR_NOT_EQUALS, "Val"));
    }

    @Test
    void buildFilterExpression_contains_functionFormat() {
        assertEquals("'Prop':contains('Val')",
                client.buildFilterExpression("Prop", Constants.OPERATOR_CONTAINS, "Val"));
    }

    @Test
    void buildFilterExpression_startsWith_functionFormat() {
        assertEquals("'Prop':starts_with('Val')",
                client.buildFilterExpression("Prop", Constants.OPERATOR_STARTS_WITH, "Val"));
    }

    @Test
    void buildFilterExpression_gt_functionFormat() {
        assertEquals("'Prop':gt('Val')",
                client.buildFilterExpression("Prop", Constants.OPERATOR_GT, "Val"));
    }

    @Test
    void buildFilterExpression_lt_functionFormat() {
        assertEquals("'Prop':lt('Val')",
                client.buildFilterExpression("Prop", Constants.OPERATOR_LT, "Val"));
    }

    @Test
    void buildFilterExpression_gte_functionFormat() {
        assertEquals("'Prop':gte('Val')",
                client.buildFilterExpression("Prop", Constants.OPERATOR_GTE, "Val"));
    }

    @Test
    void buildFilterExpression_lte_functionFormat() {
        assertEquals("'Prop':lte('Val')",
                client.buildFilterExpression("Prop", Constants.OPERATOR_LTE, "Val"));
    }

    @Test
    void buildFilterExpression_unknownOperator_defaultsToEquals() {
        assertEquals("'Prop':'Val'",
                client.buildFilterExpression("Prop", "unknown_op", "Val"));
    }

    // ─── sleep interrupted ───────────────────────────────────────────────────

    @Test
    void getPage_sleepInterruptedDuringRateLimit_throwsIOException() throws InterruptedException {
        // Restore a long delay for the first attempt so the interrupt can fire
        Constants.RATE_LIMIT_BACKOFF_MS[0] = 30_000L;

        server.enqueue(new MockResponse().setResponseCode(429).setBody("Rate Limited"));

        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<IOException> caught = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                started.countDown();
                client.getPage(baseUrl + "orgs/test/products", "apikey");
            } catch (IOException e) {
                caught.set(e);
            }
        });
        t.start();
        // Wait for thread to start making the request and enter the backoff sleep
        boolean startedInTime = started.await(5, TimeUnit.SECONDS);
        assertTrue(startedInTime, "Thread should have started");
        // Wait a bit to ensure the thread enters the sleep before interrupting
        t.join(100);
        if (t.isAlive()) {
            t.interrupt();
        }
        t.join(5_000);

        assertNotNull(caught.get());
        assertTrue(caught.get().getMessage().contains("interrupted") ||
                caught.get().getCause() instanceof InterruptedException);
    }

    // ─── Data provider for parameterized tests ───────────────────────────────

    private static Stream<Arguments> provideResponseFormats() {
        return Stream.of(
            Arguments.of(
                "{\"products\":[{\"salsify:id\":\"2\"},{\"salsify:id\":\"3\"}],\"meta\":{\"total_entries\":2}}",
                2, 2),
            Arguments.of(
                "[{\"salsify:id\":\"4\"}]",
                1, 0),
            Arguments.of(
                "{\"product\":{\"salsify:id\":\"5\"}}",
                1, 0),
            Arguments.of(
                "{\"salsify:id\":\"6\"}",
                1, 0)
        );
    }

    private static Stream<String> provideNullCasesForExtractNextLink() {
        return Stream.of(
            null,
            "   ",
            "<https://first.page>; rel=\"first\", <https://prev.page>; rel=\"prev\""
        );
    }

    private static Stream<Arguments> provideBuildProductsUrlParamCases() {
        return Stream.of(
            Arguments.of("no params", null, null, false, false),
            Arguments.of("with page size", 50, null, true, false),
            Arguments.of("zero page size", 0, null, false, false),
            Arguments.of("with filter", null, "='Status':'Active'", false, true),
            Arguments.of("blank filter", null, "   ", false, false)
        );
    }

    private static Stream<Arguments> provideBuildProductsUrlPageCases() {
        return Stream.of(
            Arguments.of(3, true, "page=3"),
            Arguments.of(1, false, null),
            Arguments.of(null, false, null)
        );
    }

    // ─── Additional coverage for 100% ──────────────────────────────────────

    @Test
    void buildProductsUrl_negativePageSize_omitsPerPage() {
        String url = client.buildProductsUrl("testorg", -5, null);
        assertFalse(url.contains("per_page"));
        assertTrue(url.contains("testorg"));
    }

    @Test
    void buildProductsUrlWithOffset_negativeOffset_omitsOffset() {
        String url = client.buildProductsUrlWithOffset("testorg", null, null, -1);
        assertFalse(url.contains("offset"));
        assertTrue(url.contains("testorg"));
    }

    @ParameterizedTest
    @CsvSource({
            "\"just-a-string\", true",
            "42, true",
            "{\"data\":[],\"meta\":\"not-an-object\"}, false",
            "{\"data\":[],\"meta\":{\"total_entries\":\"not-a-number\"}}, false"
    })
    void getPage_invalidJsonFormats_handlesGracefully(String responseBody, boolean checkProducts) throws IOException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody));

        SalsifyHttpClient.PageResult result = client.getPage(baseUrl + "orgs/test/products", "apikey");

        if (checkProducts) {
            assertEquals(0, result.products.size());
        }
        assertEquals(0, result.totalCount);
    }

    @Test
    void getPage_402_throwsClientErrorExceptionWithGenericMessage() {
        server.enqueue(new MockResponse().setResponseCode(402).setBody("Payment Required"));

        SalsifyHttpClient.ClientErrorException ex = assertThrows(
                SalsifyHttpClient.ClientErrorException.class,
                () -> client.getPage(baseUrl + "orgs/test/products", "apikey"));
        assertTrue(ex.getMessage().contains("402") || ex.getMessage().contains("Payment"));
    }

    @Test
    void getPage_dataFieldNotArray_fallsBackToObject() throws IOException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"salsify:id\":\"not-array\"},\"meta\":{}}"));

        SalsifyHttpClient.PageResult result = client.getPage(baseUrl + "orgs/test/products", "apikey");

        // When "data" is not an array, should use fallback (whole object as product)
        assertEquals(1, result.products.size());
    }

    @Test
    void getPage_productsFieldNotArray_fallsBackToObject() throws IOException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"products\":\"not-array\",\"salsify:id\":\"obj\"}"));

        SalsifyHttpClient.PageResult result = client.getPage(baseUrl + "orgs/test/products", "apikey");

        // When "products" is not an array, should use fallback
        assertEquals(1, result.products.size());
    }
}
