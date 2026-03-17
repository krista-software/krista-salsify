package com.krista.krista.extension.salsify.service;

import com.krista.krista.extension.salsify.service.impl.ProductListData;
import org.jvnet.hk2.annotations.Contract;

import java.io.IOException;

/**
 * Service contract for Salsify PXM API operations.
 */
@Contract
public interface SalsifyService {

    /**
     * Retrieves products from Salsify with optional filtering and pagination.
     *
     * <p>When offset is null or 0, paginates through all pages using Link header cursors
     * until no more pages exist, or until the
     * {@link com.krista.krista.extension.salsify.utils.Constants#LARGE_CATALOG_THRESHOLD}
     * is reached, at which point the result includes an export recommendation flag.
     *
     * <p>When offset is provided, performs offset-based pagination to fetch a single page
     * of results starting at the given offset, allowing end-users flexible pagination.
     *
     * @param orgId        the Salsify organisation ID
     * @param apiKey       the Salsify API Key — never log
     * @param filterString combined Salsify filter string (e.g. {@code ='PICCDE':'POPT'}), or {@code null}
     * @param pageSize     products per page, or {@code null} to let the Salsify API use its own default
     * @param offset       starting offset for pagination (0 for first page), or null/0 for automatic pagination
     * @return aggregated {@link ProductListData}
     * @throws IOException on API communication failure
     */
    ProductListData getAllProducts(String orgId, String apiKey,
                                  String filterString, Integer pageSize, Integer offset) throws IOException;
}
