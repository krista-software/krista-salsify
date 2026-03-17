package com.krista.krista.extension.salsify.helpers;

import app.krista.model.base.FreeForm;
import com.google.gson.JsonObject;
import com.krista.krista.extension.salsify.connection.SalsifyHttpClient;
import com.krista.krista.extension.salsify.utils.Constants;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

/**
 * Orchestrates the filtering and response building for the filterProducts catalog request.
 * Handles all business logic while maintaining pure controller orchestration.
 */
@Service
@Singleton
public class FilterProductsOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilterProductsOrchestrator.class);

    private final SalsifyHttpClient httpClient;
    private final DynamicColumnSelector dynamicColumnSelector;

    @Inject
    public FilterProductsOrchestrator(SalsifyHttpClient httpClient,
                                      DynamicColumnSelector dynamicColumnSelector) {
        this.httpClient = httpClient;
        this.dynamicColumnSelector = dynamicColumnSelector;
    }

    /**
     * Converts a composite filter map to Salsify filter expression strings.
     *
     * <p>The filter map contains property codes as keys and their filter values as values:
     * <code>
     * {
     *   "PISGRV": "High Value",
     *   "PILICT": "Y",
     *   "PIDISC": "N"
     * }
     * </code>
     *
     * @param filters composite map with Salsify property codes as keys and filter values
     * @return Salsify filter expression string, or null if no valid filters
     */
    public String parseFiltersToString(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            LOGGER.debug("No filters provided");
            return null;
        }

        List<String> conditions = new ArrayList<>();

        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String field = entry.getKey();
            String value = entry.getValue() != null ? entry.getValue().toString() : null;

            if (field != null && !field.isEmpty() && value != null && !value.isEmpty()) {
                String condition = httpClient.buildFilterExpression(
                        field,
                        Constants.OPERATOR_EQUALS,
                        value.trim());
                conditions.add(condition);
                LOGGER.info("Added filter condition: field='{}', value='{}'", field, value);
            } else {
                LOGGER.warn("Skipping invalid filter: field='{}', value='{}'", field, value);
            }
        }

        if (conditions.isEmpty()) {
            LOGGER.debug("No valid filters found");
            return null;
        }

        return httpClient.buildFilterString(conditions);
    }

    /**
     * Filters products by selecting only requested columns.
     *
     * <p>Returns a list of maps where each map contains only the columns specified by the user.
     * Column names are matched exactly (case-sensitive).
     *
     * @param products raw Salsify product JSON objects from API
     * @param requestedColumns ordered list of column names to include in response
     * @return list of maps with only requested columns
     */
    public List<Map<String, Object>> filterProductsByColumns(
            List<JsonObject> products,
            List<String> requestedColumns) {

        List<Map<String, Object>> result = new ArrayList<>();

        if (products == null || products.isEmpty()) {
            LOGGER.debug("No products to filter");
            return result;
        }

        if (requestedColumns == null || requestedColumns.isEmpty()) {
            LOGGER.warn("No columns requested");
            return result;
        }

        for (JsonObject product : products) {
            Map<String, Object> selectedColumns = dynamicColumnSelector.selectColumns(product, requestedColumns);
            result.add(selectedColumns);
        }

        LOGGER.info("Filtered {} products to {} columns", result.size(), requestedColumns.size());
        return result;
    }

    /**
     * Builds a successful response FreeForm with status and message information.
     *
     * @param productCount number of products retrieved
     * @return FreeForm object with status and success message
     */
    public FreeForm buildSuccessResponseInfo(int productCount) {
        FreeForm responseInfo = new FreeForm();
        responseInfo.put(Constants.RESPONSE_STATUS_KEY, Constants.FREFORM_TYPE_TEXT, Constants.RESPONSE_STATUS_SUCCESS);
        responseInfo.put(Constants.RESPONSE_MESSAGE_KEY, Constants.FREFORM_TYPE_TEXT,
                String.format(Constants.RESPONSE_MESSAGE_SUCCESS_TEMPLATE.replace("{}", "%d"), productCount));
        return responseInfo;
    }

    /**
     * Builds an error response FreeForm with error status and error message.
     *
     * @param errorMessage the error message to include
     * @return FreeForm object with error status and message
     */
    public FreeForm buildErrorResponseInfo(String errorMessage) {
        FreeForm errorInfo = new FreeForm();
        errorInfo.put(Constants.RESPONSE_STATUS_KEY, Constants.FREFORM_TYPE_TEXT, Constants.RESPONSE_STATUS_ERROR);
        errorInfo.put(Constants.RESPONSE_MESSAGE_KEY, Constants.FREFORM_TYPE_TEXT, errorMessage);
        return errorInfo;
    }

    /**
     * Builds the complete response field map for successful responses.
     *
     * @param filteredProducts list of filtered products (map format)
     * @param totalRecords total number of records from API
     * @return Map with Product List, Response Info, and Total Records
     */
    public Map<String, Object> buildSuccessResponseFields(
            List<Map<String, Object>> filteredProducts,
            long totalRecords) {
        Map<String, Object> responseFields = new HashMap<>();
        responseFields.put(Constants.RESPONSE_FIELD_PRODUCT_LIST, filteredProducts);
        responseFields.put(Constants.RESPONSE_FIELD_RESPONSE_INFO, buildSuccessResponseInfo(filteredProducts.size()));
        responseFields.put(Constants.RESPONSE_FIELD_TOTAL_RECORDS, (double) totalRecords);
        return responseFields;
    }

    /**
     * Builds the complete response field map for error responses.
     *
     * @param errorMessage the error message
     * @return Map with empty Product List, Error Info, and zero Total Records
     */
    public Map<String, Object> buildErrorResponseFields(String errorMessage) {
        Map<String, Object> errorFields = new HashMap<>();
        errorFields.put(Constants.RESPONSE_FIELD_PRODUCT_LIST, new ArrayList<>());
        errorFields.put(Constants.RESPONSE_FIELD_RESPONSE_INFO, buildErrorResponseInfo(errorMessage));
        errorFields.put(Constants.RESPONSE_FIELD_TOTAL_RECORDS, 0.0);
        return errorFields;
    }
}
