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
        orchestrator = new FilterProductsOrchestrator(httpClient, dynamicColumnSelector);
    }

    // ─── parseFiltersToString Tests ───

    @Test
    void parseFiltersToString_singleFilter_buildsCorrectExpression() {
        when(httpClient.buildFilterExpression("PISGRV", Constants.OPERATOR_EQUALS, "High Value"))
                .thenReturn("'PISGRV':'High Value'");
        when(httpClient.buildFilterString(List.of("'PISGRV':'High Value'")))
                .thenReturn("='PISGRV':'High Value'");

        Map<String, Object> filters = new HashMap<>();
        filters.put("PISGRV", "High Value");

        String result = orchestrator.parseFiltersToString(filters);

        assertEquals("='PISGRV':'High Value'", result);
        verify(httpClient).buildFilterExpression("PISGRV", Constants.OPERATOR_EQUALS, "High Value");
    }

    @Test
    void parseFiltersToString_multipleFilters_ANDCombined() {
        when(httpClient.buildFilterExpression("PISGRV", Constants.OPERATOR_EQUALS, "High Value"))
                .thenReturn("'PISGRV':'High Value'");
        when(httpClient.buildFilterExpression("PILICT", Constants.OPERATOR_EQUALS, "Y"))
                .thenReturn("'PILICT':'Y'");
        when(httpClient.buildFilterString(any()))
                .thenReturn("='PISGRV':'High Value','PILICT':'Y'");

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("PISGRV", "High Value");
        filters.put("PILICT", "Y");

        String result = orchestrator.parseFiltersToString(filters);

        assertEquals("='PISGRV':'High Value','PILICT':'Y'", result);
        verify(httpClient).buildFilterExpression("PISGRV", Constants.OPERATOR_EQUALS, "High Value");
        verify(httpClient).buildFilterExpression("PILICT", Constants.OPERATOR_EQUALS, "Y");
    }

    @Test
    void parseFiltersToString_nullFilters_returnsNull() {
        String result = orchestrator.parseFiltersToString(null);
        assertNull(result);
    }

    @Test
    void parseFiltersToString_emptyFilters_returnsNull() {
        String result = orchestrator.parseFiltersToString(new HashMap<>());
        assertNull(result);
    }

    @Test
    void parseFiltersToString_invalidFieldValue_skipped() {
        Map<String, Object> filters = new HashMap<>();
        filters.put("", "value"); // Empty field
        filters.put("field", null); // Null value

        String result = orchestrator.parseFiltersToString(filters);

        assertNull(result);
        verify(httpClient, never()).buildFilterString(any());
    }

    // ─── filterProductsByColumns Tests ───

    @Test
    void filterProductsByColumns_selectsRequestedColumns() {
        JsonObject product = buildProduct("ID-1", "Item-001");
        product.addProperty("PIUPCD", "123456789");
        product.addProperty("PISIZE", "12 CT");

        List<String> requestedColumns = List.of("Item#", "PIUPCD");
        List<Map<String, Object>> result = orchestrator.filterProductsByColumns(
                List.of(product), requestedColumns);

        assertEquals(1, result.size());
        Map<String, Object> firstProduct = result.get(0);
        assertTrue(firstProduct.containsKey("Item#"));
        assertTrue(firstProduct.containsKey("PIUPCD"));
        assertFalse(firstProduct.containsKey("PISIZE"));
    }

    @Test
    void filterProductsByColumns_nullProducts_returnsEmptyList() {
        List<Map<String, Object>> result = orchestrator.filterProductsByColumns(
                null, List.of("Item#"));

        assertTrue(result.isEmpty());
    }

    @Test
    void filterProductsByColumns_emptyProducts_returnsEmptyList() {
        List<Map<String, Object>> result = orchestrator.filterProductsByColumns(
                new ArrayList<>(), List.of("Item#"));

        assertTrue(result.isEmpty());
    }

    @Test
    void filterProductsByColumns_nullColumns_returnsEmptyList() {
        JsonObject product = buildProduct("ID-1", "Item-001");
        List<Map<String, Object>> result = orchestrator.filterProductsByColumns(
                List.of(product), null);

        assertTrue(result.isEmpty());
    }

    @Test
    void filterProductsByColumns_emptyColumns_returnsEmptyList() {
        JsonObject product = buildProduct("ID-1", "Item-001");
        List<Map<String, Object>> result = orchestrator.filterProductsByColumns(
                List.of(product), new ArrayList<>());

        assertTrue(result.isEmpty());
    }

    @Test
    void filterProductsByColumns_multipleProducts_filtersAll() {
        JsonObject product1 = buildProduct("ID-1", "Item-001");
        JsonObject product2 = buildProduct("ID-2", "Item-002");

        List<Map<String, Object>> result = orchestrator.filterProductsByColumns(
                List.of(product1, product2), List.of("Item#"));

        assertEquals(2, result.size());
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

    // ─── Helper Methods ───

    private JsonObject buildProduct(String id, String itemNumber) {
        JsonObject product = new JsonObject();
        product.addProperty("salsify:id", id);
        product.addProperty("Item#", itemNumber);
        return product;
    }
}
