package com.krista.krista.extension.salsify.utils;

/**
 * Constants for the Salsify extension.
 */
public final class Constants {

    private Constants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ─── Salsify API Base URL ───
    @SuppressWarnings("java:S1075")
    public static final String SALSIFY_BASE_URL =
            System.getProperty("salsify.base.url", "https://app.salsify.com");

    // ─── API Endpoint Path Templates ───
    /** Path template for listing/filtering products. Requires orgId. Customizable via salsify.products.path system property. */
    @SuppressWarnings("java:S1075")
    public static final String PRODUCTS_PATH =
            System.getProperty("salsify.products.path", "/api/v1/orgs/%s/products");

    // ─── HTTP Headers ───
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_ACCEPT = "Accept";
    public static final String HEADER_LINK = "Link";
    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String LINK_REL_NEXT = "rel=\"next\"";

    // ─── Salsify Response Keys ───
    public static final String RESPONSE_KEY_DATA = "data";
    public static final String RESPONSE_KEY_PRODUCTS = "products";
    public static final String RESPONSE_KEY_META = "meta";
    public static final String RESPONSE_KEY_TOTAL_ENTRIES = "total_entries";
    public static final String RESPONSE_KEY_PROPERTIES = "properties";
    public static final String RESPONSE_KEY_PRODUCT = "product";

    // ─── Salsify System Property Prefixes ───
    public static final String SALSIFY_SYSTEM_PREFIX = "salsify:";
    public static final String SALSIFY_ID_KEY = "salsify:id";
    public static final String SALSIFY_URL_KEY = "salsify:url";
    public static final String SALSIFY_DIGITAL_ASSETS_KEY = "salsify:digital_assets";


    // ─── API Query Parameters ───
    public static final String QUERY_PARAM_PER_PAGE = "per_page";
    public static final String QUERY_PARAM_FILTER = "filter";
    public static final String QUERY_PARAM_PAGE = "page";
    public static final String QUERY_PARAM_OFFSET = "offset";

    // ─── Salsify Boolean Field Values ───
    public static final String STATUS_DISCONTINUED = "Y";
    public static final String STATUS_ACTIVE       = "N";
    public static final String BOOLEAN_TRUE_LOWERCASE = "true";
    public static final String BOOLEAN_FALSE_LOWERCASE = "false";
    public static final String BOOLEAN_YES = "Yes";
    public static final String BOOLEAN_NO = "No";

    // ─── Salsify Filter Operators (Salsify filtering language) ───
    public static final String OPERATOR_EQUALS      = "equals";
    public static final String OPERATOR_NOT_EQUALS  = "not_equals";
    public static final String OPERATOR_CONTAINS    = "contains";
    public static final String OPERATOR_STARTS_WITH = "starts_with";
    public static final String OPERATOR_GT          = "gt";
    public static final String OPERATOR_LT          = "lt";
    public static final String OPERATOR_GTE         = "gte";
    public static final String OPERATOR_LTE         = "lte";

    // ─── Pagination ───
    /** Threshold above which Export Run is recommended instead of full pagination. */
    public static final int LARGE_CATALOG_THRESHOLD = 10_000;

    // ─── HTTP Timeouts ───
    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 30;
    public static final int DEFAULT_READ_TIMEOUT_SECONDS = 60;
    public static final int DEFAULT_WRITE_TIMEOUT_SECONDS = 30;

    // ─── Retry / Backoff ───
    /** Maximum retry attempts for 429 rate-limit responses. */
    public static final int MAX_RATE_LIMIT_RETRIES = 3;

    /** Exponential backoff delays (ms) for 429 responses: 2s, 4s, 8s. */
    @SuppressWarnings("java:S2386")
    public static final long[] RATE_LIMIT_BACKOFF_MS = {2_000L, 4_000L, 8_000L};

    /** Maximum retry attempts for 500/503 server errors. */
    public static final int MAX_SERVER_ERROR_RETRIES = 1;

    // ─── KeyValueStore Keys ───
    public static final String CREDENTIALS_KEY_SUFFIX = "_salsify_credentials";

    // ─── User-Facing Error Messages ───
    public static final String ERR_INVALID_TOKEN =
            "Invalid API token (401). Please verify your Salsify API Key.";
    public static final String ERR_FORBIDDEN =
            "Access denied (403). The API Key does not have permission for this organisation.";
    public static final String ERR_NOT_FOUND =
            "Resource not found (404). Please verify the Org ID and product identifier.";
    public static final String ERR_RATE_LIMITED =
            "Salsify rate limit exceeded. All retries exhausted. Please try again later.";
    public static final String ERR_SERVER_ERROR =
            "Salsify server error. Retry attempted once. Please try again later.";
    public static final String ERR_NO_CREDENTIALS =
            "No Salsify credentials configured. Please validate the extension attributes first.";
    public static final String ERR_UNKNOWN =
            "Failed to connect to Salsify API.";

    // ─── Logging Messages ───
    public static final String LOG_ADDING_FILTER = "Adding filter: property='{}', value='{}'";

    // ─── Response Field Names ───
    public static final String RESPONSE_FIELD_PRODUCT_LIST = "Product List";
    public static final String RESPONSE_FIELD_RESPONSE_INFO = "Response Info";
    public static final String RESPONSE_FIELD_TOTAL_RECORDS = "Total Records";

    // ─── FreeForm Field Types ───
    public static final String FREFORM_TYPE_TEXT = "Text";

    // ─── Response Status Messages ───
    public static final String RESPONSE_STATUS_SUCCESS = "200 OK";
    public static final String RESPONSE_STATUS_ERROR = "500 ERROR";
    public static final String RESPONSE_STATUS_KEY = "Status";
    public static final String RESPONSE_MESSAGE_KEY = "Message";
    public static final String RESPONSE_MESSAGE_SUCCESS_TEMPLATE = "Successfully retrieved {} products";
}
