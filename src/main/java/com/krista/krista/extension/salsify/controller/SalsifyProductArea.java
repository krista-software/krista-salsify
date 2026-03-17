package com.krista.krista.extension.salsify.controller;

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.impl.anno.*;
import com.krista.krista.extension.salsify.helpers.CredentialExtractor;
import com.krista.krista.extension.salsify.helpers.DynamicColumnSelector;
import com.krista.krista.extension.salsify.helpers.ExtensionResponseFactory;
import com.krista.krista.extension.salsify.helpers.FilterProductsOrchestrator;
import com.krista.krista.extension.salsify.helpers.PaginationParametersProcessor;
import com.krista.krista.extension.salsify.service.impl.ProductListData;
import com.krista.krista.extension.salsify.service.impl.SalsifyServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

/**
 * Catalog area for Salsify PXM product operations.
 *
 * <h3>Response Pattern</h3>
 * <p>Catalog request methods return {@code ExtensionResponse} containing dynamic maps.
 * The Krista framework deserializes the responseValue map and maps fields
 * to the output parameters based on field annotations.
 */
@Domain(id = "catEntryDomain_a0243554-f813-4b27-ab5e-132da84be905",
        name = "Salsify Products",
        ecosystemId = "catEntryEcosystem_7e64d944-7dd2-4176-817a-57fdcac05550",
        ecosystemName = "Product Information Management",
        ecosystemVersion = "98d71217-6c4f-45f4-8145-8c7061bae60a")
public class SalsifyProductArea {

    private static final Logger LOGGER = LoggerFactory.getLogger(SalsifyProductArea.class);

    private final SalsifyServiceImpl salsifyService;
    private final CredentialExtractor credentialExtractor;
    private final DynamicColumnSelector dynamicColumnSelector;
    private final FilterProductsOrchestrator filterProductsOrchestrator;

    @Inject
    public SalsifyProductArea(SalsifyServiceImpl salsifyService,
                               CredentialExtractor credentialExtractor,
                               DynamicColumnSelector dynamicColumnSelector,
                               FilterProductsOrchestrator filterProductsOrchestrator) {
        this.salsifyService = salsifyService;
        this.credentialExtractor = credentialExtractor;
        this.dynamicColumnSelector = dynamicColumnSelector;
        this.filterProductsOrchestrator = filterProductsOrchestrator;
    }

    // ─── Catalog Requests ────────────────────────────────────────────────────────

    @CatalogRequest(
            id = "localDomainRequest_86d11018-6e80-4d60-be20-32f54c6d9a2b",
            name = "Filter Products",
            description = "Retrieve products from Salsify using dynamic composite filters and column selection. "
                    + "All filter conditions are AND-combined. Select which columns appear in the response.",
            area = "SalsifyProduct",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Desc(name = "Product List", type = "[ Composite ]", required = false)
    @Field(name = "Response Info", type = "FreeForm", required = false)
    @Field(name = "Total Records", type = "Number", required = false,
            attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public ExtensionResponse filterProducts(
            @Field.Desc(name = "Filters", type = "Composite", required = true)
            Map<String, Object> filters,

            @Field.Text(name = "Columns", required = true,
                    attributes = {@Attribute(name = "visualWidth", value = "L")}, options = {})
            String columns,

            @Field(name = "Page Size", type = "Number", required = false,
                    attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
            Double pageSize,

            @Field(name = "Offset", type = "Number", required = false,
                    attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
            Double offset) {

        try {
            // Parse filters
            String filterString = filterProductsOrchestrator.parseFiltersToString(filters);

            // Call service
            ProductListData data = salsifyService.getAllProducts(
                    credentialExtractor.extractOrgId(),
                    credentialExtractor.extractApiKey(),
                    filterString,
                    PaginationParametersProcessor.extractPageSize(pageSize),
                    PaginationParametersProcessor.extractOffset(offset)
            );

            // Parse columns and filter products
            List<String> requestedColumns = dynamicColumnSelector.parseColumnsString(columns);
            List<Map<String, Object>> filteredProducts = filterProductsOrchestrator.filterProductsByColumns(
                    data.products, requestedColumns);

            // Build response and return
            LOGGER.info("Filtered {} products with {} columns", filteredProducts.size(), requestedColumns.size());
            Map<String, Object> responseFields = filterProductsOrchestrator.buildSuccessResponseFields(
                    filteredProducts, data.totalCount);
            return ExtensionResponseFactory.create(responseFields);

        } catch (Exception e) {
            LOGGER.error("Error in filterProducts: {}", e.getMessage());
            Map<String, Object> errorFields = filterProductsOrchestrator.buildErrorResponseFields(e.getMessage());
            return ExtensionResponseFactory.create(errorFields);
        }
    }

}
