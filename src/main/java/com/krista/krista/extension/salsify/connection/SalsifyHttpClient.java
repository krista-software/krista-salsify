package com.krista.krista.extension.salsify.connection;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.krista.krista.extension.salsify.utils.Constants;
import okhttp3.*;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for the Salsify PXM API.
 *
 * <p>Key behaviours:
 * <ul>
 *   <li>Adds {@code Authorization: Bearer <TOKEN>} to every request.</li>
 *   <li>Parses {@code Link} response headers to support cursor-based pagination.</li>
 *   <li>Implements exponential backoff for HTTP 429: waits 2 s, 4 s, then 8 s before
 *       propagating the failure (3 retries).</li>
 *   <li>Retries once on HTTP 500 / 503, then fails gracefully.</li>
 *   <li>Never logs the bearer token or the full Authorization header.</li>
 * </ul>
 */
@Service
public class SalsifyHttpClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(SalsifyHttpClient.class);
    private static final Gson GSON = new Gson();

    private final OkHttpClient httpClient;

    @Inject
    public SalsifyHttpClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Constants.DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(Constants.DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(Constants.DEFAULT_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        LOGGER.info("SalsifyHttpClient initialised");
    }

    /** Package-private constructor for testing — allows injecting a custom {@link OkHttpClient}. */
    SalsifyHttpClient(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    // ─── Public API ──────────────────────────────────────────────────────────────

    /**
     * Fetches a single page of products from the given full URL.
     *
     * @param url        fully-qualified URL (first page or follow-up pagination URL)
     * @param bearerToken the Salsify API Key — never log this value
     * @return {@link PageResult} containing the products array, optional next-page URL,
     *         and total entry count
     * @throws IOException on HTTP error or network failure
     */
    public PageResult getPage(String url, String bearerToken) throws IOException {
        LOGGER.info("GET page: {}", url);
        Request request = new Request.Builder()
                .url(url)
                .header(Constants.HEADER_AUTHORIZATION, Constants.BEARER_PREFIX + bearerToken)
                .header(Constants.HEADER_ACCEPT, Constants.CONTENT_TYPE_JSON)
                .get()
                .build();
        return executeWithRetry(request);
    }

    /**
     * Builds the initial products URL with optional page size and filter.
     *
     * @param orgId        the Salsify organisation ID
     * @param pageSize     products per page, or {@code null} to omit {@code per_page}
     *                     and let the Salsify API apply its own default
     * @param filterString combined Salsify filter string starting with {@code =}, or {@code null}
     * @return fully-qualified URL string ready for {@link #getPage}
     */
    public String buildProductsUrl(String orgId, Integer pageSize, String filterString) {
        return buildProductsUrl(orgId, pageSize, filterString, null);
    }

    /**
     * Builds a products URL for a specific page number.
     *
     * @param orgId        the Salsify organisation ID
     * @param pageSize     products per page, or {@code null} to use the API default
     * @param filterString combined Salsify filter string starting with {@code =}, or {@code null}
     * @param page         1-based page number; values &le; 1 omit the {@code page} parameter
     * @return fully-qualified URL string ready for {@link #getPage}
     */
    public String buildProductsUrl(String orgId, Integer pageSize, String filterString, Integer page) {
        String path = Constants.SALSIFY_BASE_URL
                + String.format(Constants.PRODUCTS_PATH, orgId);
        HttpUrl base = HttpUrl.parse(path);
        if (base == null) {
            throw new IllegalArgumentException("Cannot build Salsify URL for org: " + orgId);
        }
        HttpUrl.Builder builder = base.newBuilder();
        if (pageSize != null && pageSize > 0) {
            builder.addQueryParameter(Constants.QUERY_PARAM_PER_PAGE, String.valueOf(pageSize));
        }
        if (filterString != null && !filterString.isBlank()) {
            builder.addQueryParameter(Constants.QUERY_PARAM_FILTER, filterString);
        }
        if (page != null && page > 1) {
            builder.addQueryParameter(Constants.QUERY_PARAM_PAGE, String.valueOf(page));
        }
        return builder.build().toString();
    }

    /**
     * Builds a products URL using offset-based pagination.
     * This allows end-users to fetch a specific page of results starting at the given offset.
     *
     * @param orgId        the Salsify organisation ID
     * @param pageSize     products per page, or {@code null} to use the API default
     * @param filterString combined Salsify filter string starting with {@code =}, or {@code null}
     * @param offset       starting offset (0 for first page, incremented by pageSize for next pages)
     * @return fully-qualified URL string ready for {@link #getPage}
     */
    public String buildProductsUrlWithOffset(String orgId, Integer pageSize, String filterString, Integer offset) {
        String path = Constants.SALSIFY_BASE_URL
                + String.format(Constants.PRODUCTS_PATH, orgId);
        HttpUrl base = HttpUrl.parse(path);
        if (base == null) {
            throw new IllegalArgumentException("Cannot build Salsify URL for org: " + orgId);
        }
        HttpUrl.Builder builder = base.newBuilder();
        if (pageSize != null && pageSize > 0) {
            builder.addQueryParameter(Constants.QUERY_PARAM_PER_PAGE, String.valueOf(pageSize));
        }
        if (filterString != null && !filterString.isBlank()) {
            builder.addQueryParameter(Constants.QUERY_PARAM_FILTER, filterString);
        }
        if (offset != null && offset >= 0) {
            builder.addQueryParameter(Constants.QUERY_PARAM_OFFSET, String.valueOf(offset));
        }
        LOGGER.info("Built offset-based products URL for offset={}", offset);
        return builder.build().toString();
    }

    /**
     * Combines one or more filter conditions into a single Salsify filter string.
     *
     * <p>Salsify requires all AND conditions in a single {@code filter} parameter,
     * comma-separated, prefixed with {@code =}:
     * <pre>='PICCDE':'POPT - POP TOPS','PIICLS':'12 - POP TOPS/LAYON'</pre>
     *
     * @param conditions list of individual condition expressions (no leading {@code =})
     * @return combined filter string, or {@code null} if the list is empty
     */
    public String buildFilterString(List<String> conditions) {
        if (conditions == null || conditions.isEmpty()) return null;
        return "=" + String.join(",", conditions);
    }

    /**
     * Builds a single Salsify filter condition (without leading {@code =}).
     *
     * <p>Supported operators and their output:
     * <ul>
     *   <li>{@code equals}      → {@code 'Property':'Value'}</li>
     *   <li>{@code not_equals}  → {@code 'Property':^'Value'}</li>
     *   <li>{@code contains}    → {@code 'Property':contains('Value')}</li>
     *   <li>{@code starts_with} → {@code 'Property':starts_with('Value')}</li>
     *   <li>{@code gt}          → {@code 'Property':gt('Value')}</li>
     *   <li>{@code lt}          → {@code 'Property':lt('Value')}</li>
     *   <li>{@code gte}         → {@code 'Property':gte('Value')}</li>
     *   <li>{@code lte}         → {@code 'Property':lte('Value')}</li>
     * </ul>
     * Pass the result to {@link #buildFilterString} to combine with other conditions.
     */
    public String buildFilterExpression(String property, String operator, String value) {
        switch (operator) {
            case Constants.OPERATOR_NOT_EQUALS:
                return "'" + property + "':^'" + value + "'";
            case Constants.OPERATOR_CONTAINS:
                return "'" + property + "':contains('" + value + "')";
            case Constants.OPERATOR_STARTS_WITH:
                return "'" + property + "':starts_with('" + value + "')";
            case Constants.OPERATOR_GT:
                return "'" + property + "':gt('" + value + "')";
            case Constants.OPERATOR_LT:
                return "'" + property + "':lt('" + value + "')";
            case Constants.OPERATOR_GTE:
                return "'" + property + "':gte('" + value + "')";
            case Constants.OPERATOR_LTE:
                return "'" + property + "':lte('" + value + "')";
            default:
                // OPERATOR_EQUALS and any unknown operator default to equals
                return "'" + property + "':'" + value + "'";
        }
    }

    // ─── Internal ────────────────────────────────────────────────────────────────

    /**
     * Executes the request with retry logic:
     * <ul>
     *   <li>429 → exponential backoff using {@link Constants#RATE_LIMIT_BACKOFF_MS}</li>
     *   <li>500 / 503 → retry once, then fail</li>
     *   <li>401 / 403 / 404 → fail immediately (non-retryable)</li>
     * </ul>
     */
    private PageResult executeWithRetry(Request request) throws IOException {
        int rateLimitAttempt = 0;
        int serverErrorAttempt = 0;

        while (true) {
            try {
                return executeAndParse(request);
            } catch (RateLimitException e) {
                if (rateLimitAttempt >= Constants.MAX_RATE_LIMIT_RETRIES) {
                    throw new IOException(Constants.ERR_RATE_LIMITED, e);
                }
                long delay = Constants.RATE_LIMIT_BACKOFF_MS[rateLimitAttempt];
                LOGGER.warn("429 Rate Limited (attempt {}/{}), backing off {}ms",
                        rateLimitAttempt + 1, Constants.MAX_RATE_LIMIT_RETRIES, delay);
                sleep(delay);
                rateLimitAttempt++;
            } catch (ServerErrorException e) {
                if (serverErrorAttempt >= Constants.MAX_SERVER_ERROR_RETRIES) {
                    throw new IOException(Constants.ERR_SERVER_ERROR, e);
                }
                LOGGER.warn("5xx Server Error (attempt {}/{}), retrying once: {}",
                        serverErrorAttempt + 1, Constants.MAX_SERVER_ERROR_RETRIES, e.getMessage());
                serverErrorAttempt++;
            } catch (ClientErrorException e) {
                // 401 / 403 / 404 — never retry
                throw e;
            }
        }
    }

    private PageResult executeAndParse(Request request) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String body = getResponseBody(responseBody);

            if (!response.isSuccessful()) {
                String msg = "Salsify API error HTTP " + response.code() + ": " + body;
                LOGGER.error("{} {} → {}", request.method(), request.url(), msg);
                throwAppropriateException(response.code(), msg);
            }

            String nextUrl = extractNextLink(response.header(Constants.HEADER_LINK));
            JsonArray products = parseProducts(body);
            int totalCount = parseTotalCount(body);

            LOGGER.info("Page fetched: {} products, totalCount={}, hasNext={}",
                    products.size(), totalCount, nextUrl != null);
            return new PageResult(products, nextUrl, totalCount);
        }
    }

    /**
     * Safely extracts response body content.
     * Returns the body content if available, otherwise returns empty string.
     */
    private String getResponseBody(ResponseBody responseBody) throws IOException {
        if (responseBody == null) {
            return "";
        }
        return responseBody.string();
    }

    /**
     * Throws an appropriate exception based on HTTP status code.
     * Maps specific error codes to semantic exceptions, with fallback handling for other errors.
     */
    private void throwAppropriateException(int statusCode, String msg) throws IOException {
        switch (statusCode) {
            case 401:
                throw new ClientErrorException(Constants.ERR_INVALID_TOKEN);
            case 403:
                throw new ClientErrorException(Constants.ERR_FORBIDDEN);
            case 404:
                throw new ClientErrorException(Constants.ERR_NOT_FOUND);
            case 429:
                throw new RateLimitException(msg);
            case 500, 503:
                throw new ServerErrorException(msg);
            default:
                if (isClientError(statusCode)) {
                    throw new ClientErrorException(msg);
                } else if (isServerError(statusCode)) {
                    throw new ServerErrorException(msg);
                } else {
                    throw new IOException(msg);
                }
        }
    }

    private static boolean isClientError(int statusCode) {
        return statusCode >= 400 && statusCode < 500;
    }

    private static boolean isServerError(int statusCode) {
        return statusCode >= 500 && statusCode < 600;
    }

    /**
     * Parses the product array from a Salsify response body.
     * Handles three formats:
     * <ol>
     *   <li>{@code {"data": [...]}}</li>
     *   <li>{@code {"products": [...]}}</li>
     *   <li>Direct array {@code [...]}</li>
     *   <li>Single product wrapped in {@code {"product": {...}}}</li>
     * </ol>
     */
    private JsonArray parseProducts(String body) {
        if (body == null || body.isBlank()) return new JsonArray();
        try {
            JsonElement root = GSON.fromJson(body, JsonElement.class);
            if (root.isJsonArray()) {
                return root.getAsJsonArray();
            }
            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has(Constants.RESPONSE_KEY_DATA) && obj.get(Constants.RESPONSE_KEY_DATA).isJsonArray()) {
                    return obj.getAsJsonArray(Constants.RESPONSE_KEY_DATA);
                }
                if (obj.has(Constants.RESPONSE_KEY_PRODUCTS) && obj.get(Constants.RESPONSE_KEY_PRODUCTS).isJsonArray()) {
                    return obj.getAsJsonArray(Constants.RESPONSE_KEY_PRODUCTS);
                }
                // Single product response
                if (obj.has(Constants.RESPONSE_KEY_PRODUCT)) {
                    JsonArray arr = new JsonArray();
                    arr.add(obj.get(Constants.RESPONSE_KEY_PRODUCT));
                    return arr;
                }
                // Fallback: treat the whole object as a single product
                JsonArray arr = new JsonArray();
                arr.add(root);
                return arr;
            }
        } catch (com.google.gson.JsonSyntaxException | IllegalStateException e) {
            LOGGER.warn("Failed to parse products from response body: {}", e.getMessage());
        }
        return new JsonArray();
    }

    /**
     * Extracts the total entry count from the {@code meta.total_entries} field, or 0 if absent.
     */
    private int parseTotalCount(String body) {
        if (body == null || body.isBlank()) return 0;
        try {
            JsonElement root = GSON.fromJson(body, JsonElement.class);
            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has(Constants.RESPONSE_KEY_META)) {
                    JsonObject meta = obj.getAsJsonObject(Constants.RESPONSE_KEY_META);
                    if (meta.has(Constants.RESPONSE_KEY_TOTAL_ENTRIES)) {
                        return meta.get(Constants.RESPONSE_KEY_TOTAL_ENTRIES).getAsInt();
                    }
                }
            }
        } catch (com.google.gson.JsonSyntaxException | IllegalStateException | NumberFormatException | ClassCastException e) {
            LOGGER.debug("Could not parse total_entries: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * Parses the HTTP {@code Link} header and returns the URL for {@code rel="next"}, or null.
     *
     * <p>Example header value:
     * {@code <https://app.salsify.com/api/v1/orgs/abc/products?page=2>; rel="next", ...}
     */
    String extractNextLink(String linkHeader) {
        if (linkHeader == null || linkHeader.isBlank()) return null;
        for (String segment : linkHeader.split(",")) {
            segment = segment.trim();
            if (segment.contains(Constants.LINK_REL_NEXT)) {
                int start = segment.indexOf('<');
                int end = segment.indexOf('>');
                if (start >= 0 && end > start) {
                    return segment.substring(start + 1, end);
                }
            }
        }
        return null;
    }

    private void sleep(long ms) throws IOException {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Retry interrupted during backoff", ie);
        }
    }

    // ─── Exception Types ─────────────────────────────────────────────────────────

    /** Non-retryable 4xx client error (except 429). */
    public static final class ClientErrorException extends IOException {
        public ClientErrorException(String message) { super(message); }
    }

    /** Retryable 429 Too Many Requests. */
    private static final class RateLimitException extends IOException {
        RateLimitException(String message) { super(message); }
    }

    /** Retryable 500 / 503 server error. */
    private static final class ServerErrorException extends IOException {
        ServerErrorException(String message) { super(message); }
    }

    // ─── Result Type ─────────────────────────────────────────────────────────────

    /**
     * Holds the result of a single paginated GET request to the Salsify products API.
     */
    public static final class PageResult {
        /** The products returned on this page. */
        public final JsonArray products;
        /** URL for the next page, or {@code null} if this is the last page. */
        public final String nextUrl;
        /** Total entry count from meta, or 0 if not provided. */
        public final int totalCount;

        public PageResult(JsonArray products, String nextUrl, int totalCount) {
            this.products = products;
            this.nextUrl = nextUrl;
            this.totalCount = totalCount;
        }
    }
}
