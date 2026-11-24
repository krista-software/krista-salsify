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

package com.krista.krista.extension.salsify.helpers;

import app.krista.model.base.FreeForm;
import com.google.gson.JsonObject;
import com.krista.krista.extension.salsify.connection.SalsifyHttpClient;
import com.krista.krista.extension.salsify.utils.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilterProductsOrchestratorTest {

    @Mock private SalsifyHttpClient httpClient;

    private FilterProductsOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        DynamicColumnSelector dynamicColumnSelector = new DynamicColumnSelector();
        CartesianFilterGenerator cartesianFilterGenerator = new CartesianFilterGenerator();
        orchestrator = new FilterProductsOrchestrator(httpClient, dynamicColumnSelector, cartesianFilterGenerator);
    }

    // ─── filterProductsByColumns Tests ───

    @Test
    void filterProductsByColumns_selectsRequestedColumns() {
        JsonObject product = buildProduct("ID-1", "Item-001");
        product.addProperty("PIUPCD", "123456789");
        product.addProperty("PISIZE", "12 CT");

        List<String> requestedColumns = List.of("Item#", "PIUPCD");
        List<Map<String, Object>> result = orchestrator.filterProductsByColumns(
                List.of(product), requestedColumns, Set.of());

        assertEquals(1, result.size());
        Map<String, Object> firstProduct = result.get(0);
        assertTrue(firstProduct.containsKey("Item#"));
        assertTrue(firstProduct.containsKey("PIUPCD"));
        assertFalse(firstProduct.containsKey("PISIZE"));
    }

    @Test
    void filterProductsByColumns_nullProducts_returnsEmptyList() {
        List<Map<String, Object>> result = orchestrator.filterProductsByColumns(
                null, List.of("Item#"), Set.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void filterProductsByColumns_emptyProducts_returnsEmptyList() {
        List<Map<String, Object>> result = orchestrator.filterProductsByColumns(
                new ArrayList<>(), List.of("Item#"), Set.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void filterProductsByColumns_nullColumns_returnsEmptyList() {
        JsonObject product = buildProduct("ID-1", "Item-001");
        List<Map<String, Object>> result = orchestrator.filterProductsByColumns(
                List.of(product), null, Set.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void filterProductsByColumns_emptyColumns_returnsEmptyList() {
        JsonObject product = buildProduct("ID-1", "Item-001");
        List<Map<String, Object>> result = orchestrator.filterProductsByColumns(
                List.of(product), new ArrayList<>(), Set.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void filterProductsByColumns_multipleProducts_filtersAll() {
        JsonObject product1 = buildProduct("ID-1", "Item-001");
        JsonObject product2 = buildProduct("ID-2", "Item-002");

        List<Map<String, Object>> result = orchestrator.filterProductsByColumns(
                List.of(product1, product2), List.of("Item#"), Set.of());

        assertEquals(2, result.size());
    }

    @Test
    void filterProductsByColumns_oneProductThrows_othersStillProcessed() {
        // Custom DynamicColumnSelector that throws on the second product but succeeds on the first
        // and third. Verifies the per-product try/catch boundary in filterProductsByColumns.
        JsonObject p1 = buildProduct("ID-1", "Item-001");
        JsonObject p2 = buildProduct("ID-2", "Item-002");
        JsonObject p3 = buildProduct("ID-3", "Item-003");

        DynamicColumnSelector throwingSelector = new DynamicColumnSelector() {
            @Override
            public Map<String, Object> selectColumns(JsonObject product, List<String> columnNames) {
                if (product.has("salsify:id") && "ID-2".equals(product.get("salsify:id").getAsString())) {
                    throw new RuntimeException("simulated extraction failure");
                }
                return super.selectColumns(product, columnNames);
            }
        };
        FilterProductsOrchestrator throwingOrchestrator = new FilterProductsOrchestrator(
                httpClient, throwingSelector, new CartesianFilterGenerator());

        List<Map<String, Object>> result = throwingOrchestrator.filterProductsByColumns(
                List.of(p1, p2, p3), List.of("Item#"), Set.of());

        assertEquals(3, result.size());
        assertEquals("Item-001", result.get(0).get("Item#"));
        // Failed product gets an all-null map keyed by requested columns (column set stays consistent)
        assertTrue(result.get(1).containsKey("Item#"));
        assertNull(result.get(1).get("Item#"));
        assertEquals("Item-003", result.get(2).get("Item#"));
    }

    // ─── Include-Empty Null Substitution Tests ───

    @Test
    void filterProductsByColumns_includeEmpty_nullCellSubstitutedWithEmptyString() {
        // Product is missing PIDISC entirely. Without include-empty the cell would be null.
        // With include-empty for PIDISC, the cell becomes "" so Krista's table widget keeps
        // the column header.
        JsonObject product = buildProduct("ID-1", "Item-001");
        // no PIDISC

        List<Map<String, Object>> result = orchestrator.filterProductsByColumns(
                List.of(product), List.of("Item#", "PIDISC"), Set.of("PIDISC"));

        assertEquals(1, result.size());
        Map<String, Object> row = result.get(0);
        assertEquals("Item-001", row.get("Item#"));
        assertTrue(row.containsKey("PIDISC"));
        assertEquals("", row.get("PIDISC"));
    }

    @Test
    void filterProductsByColumns_includeEmpty_existingValueNotOverridden() {
        JsonObject product = buildProduct("ID-1", "Item-001");
        product.addProperty("PIDISC", "P");

        List<Map<String, Object>> result = orchestrator.filterProductsByColumns(
                List.of(product), List.of("Item#", "PIDISC"), Set.of("PIDISC"));

        assertEquals("P", result.get(0).get("PIDISC"));
    }

    @Test
    void filterProductsByColumns_includeEmpty_preExistingEmptyStringPreserved() {
        // A product with PIDISC explicitly set to "" should keep the empty string —
        // we only substitute null, not pre-existing "".
        JsonObject product = buildProduct("ID-1", "Item-001");
        product.addProperty("PIDISC", "");

        List<Map<String, Object>> result = orchestrator.filterProductsByColumns(
                List.of(product), List.of("Item#", "PIDISC"), Set.of("PIDISC"));

        assertEquals("", result.get(0).get("PIDISC"));
    }

    @Test
    void filterProductsByColumns_nonIncludeEmptyColumn_nullsRemainNull() {
        // PIDISC is NOT in include-empty. Null cells must remain null (regular columns
        // keep the documented null-on-missing semantic).
        JsonObject product = buildProduct("ID-1", "Item-001");
        // no PIDISC

        List<Map<String, Object>> result = orchestrator.filterProductsByColumns(
                List.of(product), List.of("Item#", "PIDISC"), Set.of());

        assertTrue(result.get(0).containsKey("PIDISC"));
        assertNull(result.get(0).get("PIDISC"));
    }

    @Test
    void filterProductsByColumns_includeEmptyForFieldNotInColumns_noOp() {
        // Include-empty for PILICT, but PILICT not in requested columns. The map never
        // had PILICT, so substitution is a no-op. Other behavior unchanged.
        JsonObject product = buildProduct("ID-1", "Item-001");

        List<Map<String, Object>> result = orchestrator.filterProductsByColumns(
                List.of(product), List.of("Item#"), Set.of("PILICT"));

        assertEquals(1, result.size());
        assertFalse(result.get(0).containsKey("PILICT"));
    }

    @Test
    void filterProductsByColumns_escapePathRow_alsoSubstituted() {
        // When a product throws and the per-product catch emits an all-null row,
        // include-empty substitution still applies so the column header survives.
        JsonObject p1 = buildProduct("ID-1", "Item-001");
        p1.addProperty("PIDISC", "P");
        JsonObject p2 = buildProduct("ID-2", "Item-002");

        DynamicColumnSelector throwingSelector = new DynamicColumnSelector() {
            @Override
            public Map<String, Object> selectColumns(JsonObject product, List<String> columnNames) {
                if (product.has("salsify:id") && "ID-2".equals(product.get("salsify:id").getAsString())) {
                    throw new RuntimeException("boom");
                }
                return super.selectColumns(product, columnNames);
            }
        };
        FilterProductsOrchestrator throwingOrchestrator = new FilterProductsOrchestrator(
                httpClient, throwingSelector, new CartesianFilterGenerator());

        List<Map<String, Object>> result = throwingOrchestrator.filterProductsByColumns(
                List.of(p1, p2), List.of("Item#", "PIDISC"), Set.of("PIDISC"));

        assertEquals(2, result.size());
        assertEquals("P", result.get(0).get("PIDISC"));
        // p2 escaped via the catch path; PIDISC was null, now substituted to ""
        assertEquals("", result.get(1).get("PIDISC"));
        // Item# is NOT in include-empty, so its escape-path null stays null
        assertNull(result.get(1).get("Item#"));
    }

    // ─── buildSuccessResponseInfo Tests ───

    @Test
    void buildSuccessResponseInfo_createsCorrectFreeForm() {
        FreeForm result = orchestrator.buildSuccessResponseInfo(42);

        assertNotNull(result);
        assertEquals(Constants.RESPONSE_STATUS_SUCCESS, result.get(Constants.RESPONSE_STATUS_KEY));
        assertTrue(result.get(Constants.RESPONSE_MESSAGE_KEY).toString().contains("42"));
    }

    @Test
    void buildSuccessResponseInfo_zeroProducts_createsValidMessage() {
        FreeForm result = orchestrator.buildSuccessResponseInfo(0);

        assertNotNull(result);
        assertEquals(Constants.RESPONSE_STATUS_SUCCESS, result.get(Constants.RESPONSE_STATUS_KEY));
        assertTrue(result.get(Constants.RESPONSE_MESSAGE_KEY).toString().contains("0"));
    }

    // ─── buildErrorResponseInfo Tests ───

    @Test
    void buildErrorResponseInfo_createsCorrectFreeForm() {
        String errorMsg = "Test error message";
        FreeForm result = orchestrator.buildErrorResponseInfo(errorMsg);

        assertNotNull(result);
        assertEquals(Constants.RESPONSE_STATUS_ERROR, result.get(Constants.RESPONSE_STATUS_KEY));
        assertEquals(errorMsg, result.get(Constants.RESPONSE_MESSAGE_KEY));
    }

    // ─── buildSuccessResponseFields Tests ───

    @Test
    void buildSuccessResponseFields_createsCompleteResponse() {
        List<Map<String, Object>> filteredProducts = List.of(new HashMap<>());

        Map<String, Object> result = orchestrator.buildSuccessResponseFields(filteredProducts, 100);

        assertNotNull(result);
        assertEquals(filteredProducts, result.get(Constants.RESPONSE_FIELD_PRODUCT_LIST));
        assertNotNull(result.get(Constants.RESPONSE_FIELD_RESPONSE_INFO));
        assertEquals(100.0, result.get(Constants.RESPONSE_FIELD_TOTAL_RECORDS));
    }

    @Test
    void buildSuccessResponseFields_emptyProducts_stillCreatesResponse() {
        List<Map<String, Object>> filteredProducts = new ArrayList<>();

        Map<String, Object> result = orchestrator.buildSuccessResponseFields(filteredProducts, 0);

        assertNotNull(result);
        assertTrue(((List<?>) result.get(Constants.RESPONSE_FIELD_PRODUCT_LIST)).isEmpty());
        assertEquals(0.0, result.get(Constants.RESPONSE_FIELD_TOTAL_RECORDS));
    }

    // ─── buildErrorResponseFields Tests ───

    @Test
    void buildErrorResponseFields_createsErrorResponse() {
        String errorMsg = "API connection failed";

        Map<String, Object> result = orchestrator.buildErrorResponseFields(errorMsg);

        assertNotNull(result);
        assertTrue(((List<?>) result.get(Constants.RESPONSE_FIELD_PRODUCT_LIST)).isEmpty());
        assertNotNull(result.get(Constants.RESPONSE_FIELD_RESPONSE_INFO));
        assertEquals(0.0, result.get(Constants.RESPONSE_FIELD_TOTAL_RECORDS));

        FreeForm responseInfo = (FreeForm) result.get(Constants.RESPONSE_FIELD_RESPONSE_INFO);
        assertEquals(Constants.RESPONSE_STATUS_ERROR, responseInfo.get(Constants.RESPONSE_STATUS_KEY));
        assertEquals(errorMsg, responseInfo.get(Constants.RESPONSE_MESSAGE_KEY));
    }

    @Test
    void buildErrorResponseFields_emptyErrorMessage_createsValidResponse() {
        Map<String, Object> result = orchestrator.buildErrorResponseFields("");

        assertNotNull(result);
        assertEquals(0.0, result.get(Constants.RESPONSE_FIELD_TOTAL_RECORDS));
    }

    // ─── parseFilters Tests ───

    @Test
    void parseFilters_nullPrimary_throwsRequired() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orchestrator.parseFilters(null, null, null));
        assertTrue(ex.getMessage().contains("Primary Filters is required"));
    }

    @Test
    void parseFilters_emptyPrimary_throwsRequired() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orchestrator.parseFilters(new HashMap<>(), null, null));
        assertTrue(ex.getMessage().contains("Primary Filters is required"));
    }

    @Test
    void parseFilters_primaryWithOnlyEmptyValues_throwsRequired() {
        Map<String, Object> primary = new HashMap<>();
        primary.put("PISGRV", "");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orchestrator.parseFilters(primary, null, null));
        assertTrue(ex.getMessage().contains("Primary Filters is required"));
    }

    @Test
    void parseFilters_primarySingleValue_producesOneShard() {
        when(httpClient.buildFilterExpression("PISGRV", Constants.OPERATOR_EQUALS, "BBY - BABY"))
                .thenReturn("'PISGRV':'BBY - BABY'");
        when(httpClient.buildFilterString(List.of("'PISGRV':'BBY - BABY'")))
                .thenReturn("='PISGRV':'BBY - BABY'");

        Map<String, Object> primary = new HashMap<>();
        primary.put("PISGRV", "BBY - BABY");

        ParsedFilters result = orchestrator.parseFilters(primary, null, null);

        assertEquals(List.of("='PISGRV':'BBY - BABY'"), result.apiFilterShards);
        assertFalse(result.hasClientSideFilters());
        assertFalse(result.isFanOut());
    }

    @Test
    void parseFilters_primaryAndSecondarySingleValue_secondaryJoinsAsBaseAnd() {
        when(httpClient.buildFilterExpression(any(), any(), any()))
                .thenAnswer(inv -> "'" + inv.getArgument(0) + "':'" + inv.getArgument(2) + "'");
        when(httpClient.buildFilterString(anyList()))
                .thenAnswer(inv -> "=" + String.join(",", (List<String>) inv.getArgument(0)));

        Map<String, Object> primary = new LinkedHashMap<>();
        primary.put("PISGRV", "Baby");
        Map<String, Object> secondary = new LinkedHashMap<>();
        secondary.put("PILICT", "Y");

        ParsedFilters result = orchestrator.parseFilters(primary, secondary, null);

        assertEquals(1, result.apiFilterShards.size());
        // Secondary single-value joins via base AND condition; order: base conditions then primary
        assertEquals("='PILICT':'Y','PISGRV':'Baby'", result.apiFilterShards.get(0));
        assertFalse(result.hasClientSideFilters());
    }

    @Test
    void parseFilters_primaryAndSecondaryMultiValue_secondaryGoesClientSide() {
        when(httpClient.buildFilterExpression(any(), any(), any()))
                .thenAnswer(inv -> "'" + inv.getArgument(0) + "':'" + inv.getArgument(2) + "'");
        when(httpClient.buildFilterString(anyList()))
                .thenAnswer(inv -> "=" + String.join(",", (List<String>) inv.getArgument(0)));

        Map<String, Object> primary = new HashMap<>();
        primary.put("PISGRV", "Baby");
        Map<String, Object> secondary = new HashMap<>();
        secondary.put("PIDISC", "P,S");

        ParsedFilters result = orchestrator.parseFilters(primary, secondary, null);

        assertEquals(1, result.apiFilterShards.size());
        assertTrue(result.hasClientSideFilters());
        assertEquals(Set.of("P", "S"), result.clientSideOrFilters.get("PIDISC"));
    }

    @Test
    void parseFilters_secondaryListValueMultiItem_goesClientSide() {
        when(httpClient.buildFilterExpression(any(), any(), any()))
                .thenAnswer(inv -> "'" + inv.getArgument(0) + "':'" + inv.getArgument(2) + "'");
        when(httpClient.buildFilterString(anyList()))
                .thenAnswer(inv -> "=" + String.join(",", (List<String>) inv.getArgument(0)));

        Map<String, Object> primary = new HashMap<>();
        primary.put("PISGRV", "Baby");
        Map<String, Object> secondary = new HashMap<>();
        secondary.put("PIDISC", List.of("P", "S"));

        ParsedFilters result = orchestrator.parseFilters(primary, secondary, null);

        assertTrue(result.hasClientSideFilters());
        assertEquals(Set.of("P", "S"), result.clientSideOrFilters.get("PIDISC"));
    }

    @Test
    void parseFilters_fieldInBothMaps_throws() {
        Map<String, Object> primary = new HashMap<>();
        primary.put("PISGRV", "Baby");
        Map<String, Object> secondary = new HashMap<>();
        secondary.put("PISGRV", "Wed");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orchestrator.parseFilters(primary, secondary, null));
        assertTrue(ex.getMessage().contains("PISGRV"));
        assertTrue(ex.getMessage().contains("cannot appear in both"));
    }

    // ─── applyClientSideFilters Tests ───

    @Test
    void applyClientSideFilters_matchesOrLogic() {
        JsonObject p1 = buildProduct("1", "Item-001");
        p1.addProperty("PIDISC", "P");
        JsonObject p2 = buildProduct("2", "Item-002");
        p2.addProperty("PIDISC", "S");
        JsonObject p3 = buildProduct("3", "Item-003");
        p3.addProperty("PIDISC", "N");

        Map<String, Set<String>> clientFilters = Map.of("PIDISC", Set.of("P", "S"));

        List<JsonObject> result = orchestrator.applyClientSideFilters(
                List.of(p1, p2, p3), parsed(clientFilters));

        assertEquals(2, result.size());
        assertEquals("P", result.get(0).get("PIDISC").getAsString());
        assertEquals("S", result.get(1).get("PIDISC").getAsString());
    }

    @Test
    void applyClientSideFilters_missingFieldExcluded() {
        JsonObject p1 = buildProduct("1", "Item-001");
        p1.addProperty("PIDISC", "P");
        JsonObject p2 = buildProduct("2", "Item-002");
        // p2 has no PIDISC field — must be excluded

        Map<String, Set<String>> clientFilters = Map.of("PIDISC", Set.of("P"));

        List<JsonObject> result = orchestrator.applyClientSideFilters(
                List.of(p1, p2), parsed(clientFilters));

        assertEquals(1, result.size());
        assertEquals("Item-001", result.get(0).get("Item#").getAsString());
    }

    @Test
    void applyClientSideFilters_emptyStringFieldExcluded() {
        JsonObject p1 = buildProduct("1", "Item-001");
        p1.addProperty("PIDISC", "");

        Map<String, Set<String>> clientFilters = Map.of("PIDISC", Set.of("P"));

        List<JsonObject> result = orchestrator.applyClientSideFilters(
                List.of(p1), parsed(clientFilters));

        assertTrue(result.isEmpty());
    }

    @Test
    void applyClientSideFilters_twoMultiValueFields_strictIntersection() {
        // Case C regression: PISGRV in {Baby, Wed} AND PIDISC in {S, P}
        // must return only products matching both sets — no null slot-through.
        JsonObject match1 = buildProduct("1", "Item-001");
        match1.addProperty("PISGRV", "Baby");
        match1.addProperty("PIDISC", "S");

        JsonObject match2 = buildProduct("2", "Item-002");
        match2.addProperty("PISGRV", "Baby");
        match2.addProperty("PIDISC", "P");

        JsonObject pidiscMismatch = buildProduct("3", "Item-003");
        pidiscMismatch.addProperty("PISGRV", "Wed");
        pidiscMismatch.addProperty("PIDISC", "X");

        JsonObject pidiscMissing = buildProduct("4", "Item-004");
        pidiscMissing.addProperty("PISGRV", "Baby");
        // no PIDISC

        JsonObject pisgrvMissing = buildProduct("5", "Item-005");
        pisgrvMissing.addProperty("PIDISC", "S");
        // no PISGRV

        JsonObject bothMissing = buildProduct("6", "Item-006");

        JsonObject pisgrvOther = buildProduct("7", "Item-007");
        pisgrvOther.addProperty("PISGRV", "Other");
        pisgrvOther.addProperty("PIDISC", "S");

        Map<String, Set<String>> clientFilters = new LinkedHashMap<>();
        clientFilters.put("PISGRV", Set.of("Baby", "Wed"));
        clientFilters.put("PIDISC", Set.of("S", "P"));

        List<JsonObject> result = orchestrator.applyClientSideFilters(
                List.of(match1, match2, pidiscMismatch, pidiscMissing,
                        pisgrvMissing, bothMissing, pisgrvOther),
                parsed(clientFilters));

        assertEquals(2, result.size());
        assertEquals("Item-001", result.get(0).get("Item#").getAsString());
        assertEquals("Item-002", result.get(1).get("Item#").getAsString());
    }

    @Test
    void applyClientSideFilters_emptyMap_returnsAll() {
        JsonObject p1 = buildProduct("1", "Item-001");
        JsonObject p2 = buildProduct("2", "Item-002");

        List<JsonObject> result = orchestrator.applyClientSideFilters(
                List.of(p1, p2), parsed(Collections.emptyMap()));

        assertEquals(2, result.size());
    }

    @Test
    void applyClientSideFilters_multipleFields_andLogic() {
        JsonObject p1 = buildProduct("1", "Item-001");
        p1.addProperty("PIDISC", "P");
        p1.addProperty("STATUS", "Active");

        JsonObject p2 = buildProduct("2", "Item-002");
        p2.addProperty("PIDISC", "P");
        p2.addProperty("STATUS", "Inactive");

        Map<String, Set<String>> clientFilters = new LinkedHashMap<>();
        clientFilters.put("PIDISC", Set.of("P", "S"));
        clientFilters.put("STATUS", Set.of("Active"));

        List<JsonObject> result = orchestrator.applyClientSideFilters(
                List.of(p1, p2), parsed(clientFilters));

        assertEquals(1, result.size());
        assertEquals("Item-001", result.get(0).get("Item#").getAsString());
    }

    @Test
    void applyClientSideFilters_nullProducts_returnsEmptyList() {
        Map<String, Set<String>> clientFilters = Map.of("PIDISC", Set.of("P"));

        List<JsonObject> result = orchestrator.applyClientSideFilters(null, parsed(clientFilters));

        assertTrue(result.isEmpty());
    }

    // ─── Include Empty For Tests ───

    @Test
    void parseFilters_includeEmpty_secondaryFieldRoutedClientSide() {
        // PISGRV (primary, single value) → 1 shard
        // PIDISC (secondary, single value) — would normally go to API, but include-empty
        // forces it client-side.
        when(httpClient.buildFilterExpression("PISGRV", Constants.OPERATOR_EQUALS, "Baby"))
                .thenReturn("'PISGRV':'Baby'");
        when(httpClient.buildFilterString(List.of("'PISGRV':'Baby'")))
                .thenReturn("='PISGRV':'Baby'");

        Map<String, Object> primary = new HashMap<>();
        primary.put("PISGRV", "Baby");
        Map<String, Object> secondary = new HashMap<>();
        secondary.put("PIDISC", "P");

        ParsedFilters result = orchestrator.parseFilters(primary, secondary, Set.of("PIDISC"));

        assertEquals(List.of("='PISGRV':'Baby'"), result.apiFilterShards);
        assertEquals(Set.of("P"), result.clientSideOrFilters.get("PIDISC"));
        assertTrue(result.includeEmptyFields.contains("PIDISC"));
    }

    @Test
    void parseFilters_includeEmpty_emptyOnlyFilter_addsEmptyAllowedSet() {
        // PIDISC listed in include-empty but absent from secondary → empty allowed-set,
        // matches only products where PIDISC is null/empty.
        when(httpClient.buildFilterExpression("PISGRV", Constants.OPERATOR_EQUALS, "Baby"))
                .thenReturn("'PISGRV':'Baby'");
        when(httpClient.buildFilterString(List.of("'PISGRV':'Baby'")))
                .thenReturn("='PISGRV':'Baby'");

        Map<String, Object> primary = new HashMap<>();
        primary.put("PISGRV", "Baby");

        ParsedFilters result = orchestrator.parseFilters(primary, null, Set.of("PIDISC"));

        assertEquals(List.of("='PISGRV':'Baby'"), result.apiFilterShards);
        assertTrue(result.clientSideOrFilters.containsKey("PIDISC"));
        assertTrue(result.clientSideOrFilters.get("PIDISC").isEmpty());
        assertTrue(result.includeEmptyFields.contains("PIDISC"));
    }

    @Test
    void parseFilters_includeEmpty_secondaryMultiValueClientSide() {
        // PISGRV (primary), PIDISC (secondary multi-value with include-empty) → client-side
        when(httpClient.buildFilterExpression("PISGRV", Constants.OPERATOR_EQUALS, "Baby"))
                .thenReturn("'PISGRV':'Baby'");
        when(httpClient.buildFilterString(List.of("'PISGRV':'Baby'")))
                .thenReturn("='PISGRV':'Baby'");

        Map<String, Object> primary = new HashMap<>();
        primary.put("PISGRV", "Baby");
        Map<String, Object> secondary = new HashMap<>();
        secondary.put("PIDISC", "P,S");

        ParsedFilters result = orchestrator.parseFilters(primary, secondary, Set.of("PIDISC"));

        assertEquals(List.of("='PISGRV':'Baby'"), result.apiFilterShards);
        assertEquals(Set.of("P", "S"), result.clientSideOrFilters.get("PIDISC"));
        assertTrue(result.includeEmptyFields.contains("PIDISC"));
    }

    @Test
    void applyClientSideFilters_includeEmpty_acceptsNullValue() {
        JsonObject match = buildProduct("1", "Item-001");
        match.addProperty("PISGRV", "Baby");
        JsonObject nullField = buildProduct("2", "Item-002");
        // no PISGRV
        JsonObject mismatch = buildProduct("3", "Item-003");
        mismatch.addProperty("PISGRV", "Other");

        Map<String, Set<String>> clientFilters = Map.of("PISGRV", Set.of("Baby", "Wed"));
        ParsedFilters spec = parsedWithEmpty(clientFilters, Set.of("PISGRV"));

        List<JsonObject> result = orchestrator.applyClientSideFilters(
                List.of(match, nullField, mismatch), spec);

        assertEquals(2, result.size());
        assertEquals("Item-001", result.get(0).get("Item#").getAsString());
        assertEquals("Item-002", result.get(1).get("Item#").getAsString());
    }

    @Test
    void applyClientSideFilters_includeEmpty_acceptsEmptyString() {
        JsonObject emptyField = buildProduct("1", "Item-001");
        emptyField.addProperty("PISGRV", "");

        Map<String, Set<String>> clientFilters = Map.of("PISGRV", Set.of("Baby"));
        ParsedFilters spec = parsedWithEmpty(clientFilters, Set.of("PISGRV"));

        List<JsonObject> result = orchestrator.applyClientSideFilters(List.of(emptyField), spec);

        assertEquals(1, result.size());
    }

    @Test
    void applyClientSideFilters_emptyOnlyAllowedSet_returnsOnlyEmptyValues() {
        // Empty-only filter: include-empty with no allowed values → only nulls match.
        JsonObject hasValue = buildProduct("1", "Item-001");
        hasValue.addProperty("PIDISC", "S");
        JsonObject noValue = buildProduct("2", "Item-002");

        Map<String, Set<String>> clientFilters = Map.of("PIDISC", Collections.emptySet());
        ParsedFilters spec = parsedWithEmpty(clientFilters, Set.of("PIDISC"));

        List<JsonObject> result = orchestrator.applyClientSideFilters(
                List.of(hasValue, noValue), spec);

        assertEquals(1, result.size());
        assertEquals("Item-002", result.get(0).get("Item#").getAsString());
    }

    @Test
    void applyClientSideFilters_includeEmptyOnSomeFieldsOnly_appliesPerField() {
        // PISGRV allows empty; PIDISC is strict. Only products that match PIDISC and
        // either match PISGRV or are missing PISGRV should be returned.
        JsonObject bothMatch = buildProduct("1", "Item-001");
        bothMatch.addProperty("PISGRV", "Baby");
        bothMatch.addProperty("PIDISC", "S");

        JsonObject pisgrvNullAndPidiscMatch = buildProduct("2", "Item-002");
        pisgrvNullAndPidiscMatch.addProperty("PIDISC", "S");

        JsonObject pidiscNull = buildProduct("3", "Item-003");
        pidiscNull.addProperty("PISGRV", "Baby");
        // no PIDISC — must be excluded (PIDISC is strict)

        JsonObject pisgrvOther = buildProduct("4", "Item-004");
        pisgrvOther.addProperty("PISGRV", "Other");
        pisgrvOther.addProperty("PIDISC", "S");

        Map<String, Set<String>> clientFilters = new LinkedHashMap<>();
        clientFilters.put("PISGRV", Set.of("Baby", "Wed"));
        clientFilters.put("PIDISC", Set.of("S", "P"));
        ParsedFilters spec = parsedWithEmpty(clientFilters, Set.of("PISGRV"));

        List<JsonObject> result = orchestrator.applyClientSideFilters(
                List.of(bothMatch, pisgrvNullAndPidiscMatch, pidiscNull, pisgrvOther), spec);

        assertEquals(2, result.size());
        assertEquals("Item-001", result.get(0).get("Item#").getAsString());
        assertEquals("Item-002", result.get(1).get("Item#").getAsString());
    }

    // ─── Primary Filters / Cartesian Fan-Out Tests ───

    @Test
    void parseFilters_singlePrimaryMultiValue_producesNShards() {
        when(httpClient.buildFilterExpression("PISGRV", Constants.OPERATOR_EQUALS, "Baby"))
                .thenReturn("'PISGRV':'Baby'");
        when(httpClient.buildFilterExpression("PISGRV", Constants.OPERATOR_EQUALS, "Wed"))
                .thenReturn("'PISGRV':'Wed'");
        when(httpClient.buildFilterString(List.of("'PISGRV':'Baby'")))
                .thenReturn("='PISGRV':'Baby'");
        when(httpClient.buildFilterString(List.of("'PISGRV':'Wed'")))
                .thenReturn("='PISGRV':'Wed'");

        Map<String, Object> primary = new HashMap<>();
        primary.put("PISGRV", "Baby,Wed");

        ParsedFilters result = orchestrator.parseFilters(primary, null, null);

        assertEquals(2, result.apiFilterShards.size());
        assertTrue(result.apiFilterShards.contains("='PISGRV':'Baby'"));
        assertTrue(result.apiFilterShards.contains("='PISGRV':'Wed'"));
        assertTrue(result.isFanOut());
        assertFalse(result.hasClientSideFilters());
    }

    @Test
    void parseFilters_twoPrimariesMultiValueEach_producesCartesianShards() {
        // Production-bug regression: PISGRV ∈ {Baby, Wed} × PIDISC ∈ {P, S} = 4 shards
        when(httpClient.buildFilterExpression(eq("PISGRV"), eq(Constants.OPERATOR_EQUALS), anyString()))
                .thenAnswer(inv -> "'PISGRV':'" + inv.getArgument(2) + "'");
        when(httpClient.buildFilterExpression(eq("PIDISC"), eq(Constants.OPERATOR_EQUALS), anyString()))
                .thenAnswer(inv -> "'PIDISC':'" + inv.getArgument(2) + "'");
        when(httpClient.buildFilterString(anyList()))
                .thenAnswer(inv -> "=" + String.join(",", (List<String>) inv.getArgument(0)));

        Map<String, Object> primary = new LinkedHashMap<>();
        primary.put("PISGRV", "Baby,Wed");
        primary.put("PIDISC", "P,S");

        ParsedFilters result = orchestrator.parseFilters(primary, null, null);

        assertEquals(4, result.apiFilterShards.size());
        assertTrue(result.apiFilterShards.contains("='PISGRV':'Baby','PIDISC':'P'"));
        assertTrue(result.apiFilterShards.contains("='PISGRV':'Baby','PIDISC':'S'"));
        assertTrue(result.apiFilterShards.contains("='PISGRV':'Wed','PIDISC':'P'"));
        assertTrue(result.apiFilterShards.contains("='PISGRV':'Wed','PIDISC':'S'"));
        assertTrue(result.isFanOut());
        assertFalse(result.hasClientSideFilters());
    }

    @Test
    void parseFilters_primaryFanOutPlusSecondarySingleValue_eachShardCarriesBaseAnd() {
        // PISGRV multi-value primary (2 shards) + PILICT secondary single-value (base AND).
        // Each shard must carry the PILICT base condition.
        when(httpClient.buildFilterExpression(any(), any(), any()))
                .thenAnswer(inv -> "'" + inv.getArgument(0) + "':'" + inv.getArgument(2) + "'");
        when(httpClient.buildFilterString(anyList()))
                .thenAnswer(inv -> "=" + String.join(",", (List<String>) inv.getArgument(0)));

        Map<String, Object> primary = new HashMap<>();
        primary.put("PISGRV", "Baby,Wed");
        Map<String, Object> secondary = new HashMap<>();
        secondary.put("PILICT", "Y");

        ParsedFilters result = orchestrator.parseFilters(primary, secondary, null);

        assertEquals(2, result.apiFilterShards.size());
        assertTrue(result.apiFilterShards.contains("='PILICT':'Y','PISGRV':'Baby'"));
        assertTrue(result.apiFilterShards.contains("='PILICT':'Y','PISGRV':'Wed'"));
    }

    @Test
    void parseFilters_primaryFanOutPlusSecondaryMultiValue_correctlySplit() {
        // PISGRV multi-value primary → fan-out; PIDISC multi-value secondary → client-side
        when(httpClient.buildFilterExpression(any(), any(), any()))
                .thenAnswer(inv -> "'" + inv.getArgument(0) + "':'" + inv.getArgument(2) + "'");
        when(httpClient.buildFilterString(anyList()))
                .thenAnswer(inv -> "=" + String.join(",", (List<String>) inv.getArgument(0)));

        Map<String, Object> primary = new HashMap<>();
        primary.put("PISGRV", "Baby,Wed");
        Map<String, Object> secondary = new HashMap<>();
        secondary.put("PIDISC", "P,S");

        ParsedFilters result = orchestrator.parseFilters(primary, secondary, null);

        assertEquals(2, result.apiFilterShards.size());
        assertTrue(result.hasClientSideFilters());
        assertEquals(Set.of("P", "S"), result.clientSideOrFilters.get("PIDISC"));
    }

    @Test
    void parseFilters_primaryConflictsWithIncludeEmpty_throws() {
        Map<String, Object> primary = new HashMap<>();
        primary.put("PISGRV", "Baby");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orchestrator.parseFilters(primary, null, Set.of("PISGRV")));

        assertTrue(ex.getMessage().contains("PISGRV"));
        assertTrue(ex.getMessage().contains("cannot be both"));
    }

    @Test
    void parseFilters_cartesianExceedsCap_throwsWithBreakdown() {
        // 5 × 5 = 25, over cap of 20.
        Map<String, Object> primary = new LinkedHashMap<>();
        primary.put("PISGRV", "a,b,c,d,e");
        primary.put("PIDISC", "v,w,x,y,z");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orchestrator.parseFilters(primary, null, null));

        assertTrue(ex.getMessage().contains("25"));
        assertTrue(ex.getMessage().contains("PISGRV×5"));
        assertTrue(ex.getMessage().contains("PIDISC×5"));
    }

    // ─── Helper Methods ───

    private JsonObject buildProduct(String id, String itemNumber) {
        JsonObject product = new JsonObject();
        product.addProperty("salsify:id", id);
        product.addProperty("Item#", itemNumber);
        return product;
    }

    private ParsedFilters parsed(Map<String, Set<String>> clientFilters) {
        return new ParsedFilters(null, clientFilters, null);
    }

    private ParsedFilters parsedWithEmpty(Map<String, Set<String>> clientFilters,
                                           Set<String> includeEmptyFields) {
        return new ParsedFilters(null, clientFilters, includeEmptyFields);
    }
}
