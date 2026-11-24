# Filter Products

## Overview

Retrieves a list of products from the Salsify PXM catalog with dynamic filtering and column selection. You can filter by any Salsify property and select which columns appear in the response.

---

## Request Details

- **Area**: SalsifyProduct
- **Type**: CHANGE_SYSTEM
- **Retry Support**: ✅ Yes — automatic exponential backoff on rate limiting (429) and one automatic retry on server errors (500/503)

---

## Input Parameters

The query model is two-stage: **Primary Filters** fetch an initial product set from Salsify server-side via cartesian fan-out, then **Secondary Filters** refine the aggregated result client-side in Java.

### Primary Filters (Composite - Required)

A composite map (`Map<String, Object>`) where keys are Salsify property codes and values are the filter values used to fetch products from Salsify. Each value combination across primary fields becomes one API call (cartesian fan-out); results are aggregated.

**Example (single value per field)** — 1 API call:
```json
{
  "PISGRV": "HBD - BIRTHDAY"
}
```

**Example (multi-value via comma-separated string)** — 4 API calls:
```json
{
  "PISGRV": "HBD - BIRTHDAY,HIGH VALUE",
  "PIDISC": "P,S"
}
```
Cartesian product: `2 × 2 = 4 shards` — `(HBD,P)`, `(HBD,S)`, `(HIGH,P)`, `(HIGH,S)`.

**Cartesian cap**: The product of value counts across all primary fields cannot exceed **20**. If exceeded, the request returns a clear error with the breakdown (e.g. `PISGRV×5, PIDISC×5 = 25`) so you can trim or move a field to Secondary Filters.

**Constraints**:
- At least one field with at least one valid value is required.
- A field listed in Primary Filters cannot also appear in Secondary Filters or Include Empty For.
- Offset-based pagination is not supported with multi-shard fan-out. If Primary Filters produce more than one shard, Offset must be unset or 0.

### Secondary Filters (Composite - Optional)

A composite map of additional filters applied to the aggregated primary result set. Same shape as Primary Filters (property code → value or comma-separated values).

**Routing within Secondary Filters**:
- Single value, not in Include Empty For → AND-combined into the base API conditions of every primary shard (so Salsify pre-filters per-shard).
- Multi-value, or any field in Include Empty For → applied client-side after fetch.

**Example**:
```json
{
  "PILICT": "Y",
  "PIDISC": "P,S"
}
```
With this Secondary Filters and a single-shard Primary, the API call is `(primary-condition, PILICT='Y')` and the returned set is then narrowed client-side to `PIDISC ∈ {P, S}`.

### Columns (Text - Required)

A comma-separated list of column names to include in the response. Use exact Salsify property names (case-sensitive).

**Example**:
```
Item#, Item Description, PIUPCD, PISIZE, DecoPac Alternate Items
```

The response will contain only these columns, preserving their original names and data types.

### Include Empty For (Text - Optional)

A comma-separated list of property codes (only valid for **Secondary Filters fields**) for which products with null, empty, or missing values should also be treated as a match.

**Example**: `PIDISC`

```
Primary Filters:    { "PISGRV": "BBY - BABY" }
Secondary Filters:  { "PIDISC": "P,S" }
Include Empty For:  "PIDISC"
```
Returns products where `PISGRV = "BBY - BABY"` AND (`PIDISC ∈ {P, S}` OR `PIDISC` is unset).

A field listed in Include Empty For but absent from Secondary Filters matches *only* products where that property is unset:
```
Primary Filters:    { "PISGRV": "BBY - BABY" }
Include Empty For:  "PIDISC"
```
Returns products where `PISGRV = "BBY - BABY"` AND `PIDISC` is unset.

> **📝 Note (1.0.8)**: For Include Empty For columns specifically, missing/null cell values are returned as the empty string `""` instead of `null`. This guarantees the column header survives Krista's table renderer. Workflow code that distinguishes `null` from `""` should use `cell.isEmpty()` for these columns. Other columns retain the `null`-on-missing behavior.

### Page Size (Number - Optional)

Number of products to fetch per API page. Leave blank to use the Salsify API default. Useful for tuning performance on large catalogs.

**Example**: `100`

### Offset (Number - Optional)

Starting offset for offset-based pagination. Use this to fetch a specific page: calculate as `(pageNumber - 1) * pageSize`. Leave blank to fetch all pages automatically.

**Example**: `100`

---

## Output Parameters

Returns a list of **composite maps** (Map<String, Object>). Each map contains only the columns you requested.

### Response Format

```json
[
  {
    "Item#": "10165",
    "Item Description": "POP TOPS-FELIZ CUMPLEANOS",
    "PIUPCD": "607772101654",
    "PISIZE": "24/PKG",
    "DecoPac Alternate Items": ["27239", "6667"]
  },
  {
    "Item#": "10166",
    "Item Description": "Another Product",
    "PIUPCD": "812345678901",
    "PISIZE": "12 CT",
    "DecoPac Alternate Items": ["3075", "48165"]
  }
]
```

### Data Type Preservation

- **Primitive values** (strings, numbers, booleans) are returned as-is
- **Array values** (e.g., `DecoPac Alternate Items`) are returned as **arrays/lists**, NOT comma-separated strings
- **Object values** are serialized to nested maps
- **Missing columns** are included in the map with a `null` value, so the column always appears in the result table even when some products don't have a value for it

---

## Common Salsify Property Codes

| Property Code | Description | Example Values |
|---|---|---|
| `Item#` | Item number (primary key) | `"10165"` |
| `Item Description` | Full product description | `"POP TOPS-FELIZ CUMPLEANOS"` |
| `PIUPCD` | Universal Product Code | `"607772101654"` |
| `PISIZE` | Package size | `"24/PKG"` |
| `PINETC` | Net count per package | `"24/PKG"` |
| `PICPR1` | Customer price | `"6.37"` |
| `PICPRC` | Contract price | `"6.69"` |
| `PICTRY` | Country of origin | `"CN - CHINA"` |
| `PICCDE` | Product type classification | `"POPT - POP TOPS"` |
| `PIICLS` | Product category | `"12 - POP TOPS/LAYON"` |
| `PISGIG` | Image group identifier | `"NLCPPR - Non Licensed..."` |
| `PIDDTE` | Discontinued date | `"2024-02-08"` |
| `PIDISC` | Discontinued status | `"Y"` or `"N"` |
| `PIREPL` | Replacement item number | `"38138"` |
| `PIMKDT` | Market date | `"0001-01-01"` |
| `PIATPD` | Available-to-promise date | `"0001-01-01"` |
| `PISGRV` | Segmentation - Revenue Avenue | `"HBD - BIRTHDAY"`, `"HIGH VALUE"` |
| `PILICT` | License Type | `"Y"` or `"N"` |
| `PICSTM` | Custom Item flag | `"Y"` or `"N"` |
| `DecoPac Alternate Items` | Comma-separated alternate items | `["27239", "6667"]` (array) |
| `salsify:url` | Main product image URL | `"http://images.salsify.com/..."` |

> **📝 Note**: Boolean field values (Y/N, Yes/No) are returned as provided in Salsify (typically Y or N).
> **📝 Note**: If a requested column is missing from a product, it is included in the response map as `null`. This keeps column headers consistent across rows so the result table always shows the requested columns.

---

## Validation Rules

| Validation | Error Message | Resolution |
|------------|---------------|------------|
| Credentials must be configured | "No Salsify credentials configured. Please validate the extension attributes first." | Complete the extension Setup tab. See [Extension Configuration](/pages/ExtensionConfiguration.md) |
| Primary Filters is required | "Primary Filters is required and must contain at least one field with at least one value." | Add at least one entry to Primary Filters |
| Field cannot appear in both filter maps | "Field 'X' cannot appear in both Primary Filters and Secondary Filters." | Pick one — Primary if you want server-side fan-out, Secondary if you want client-side refinement |
| Primary field cannot be in Include Empty For | "Field 'X' cannot be both a Primary Filter and listed in Include Empty For." | Move the field to Secondary Filters, or remove it from Include Empty For |
| Cartesian product cap | "Primary Filters cartesian product is too large: N combinations exceeds the limit of 20. Breakdown: ..." | Reduce values per primary field, or move a field to Secondary |
| Offset incompatible with multi-shard | "Offset-based pagination is not supported when Primary Filters produce multiple shards." | Omit Offset, or use single-value primary filters only |
| Columns parameter must be provided | *(error)* | Provide a comma-separated list of column names |
| Column names are case-sensitive | *(column omitted from result)* | Use exact Salsify property names (e.g., `"Item#"` not `"item#"`) |

> **📝 Note**: Invalid filter entries (null/empty key or value) are skipped with a warning log; the request continues with remaining valid filters. Validation errors are returned as a populated error response, not a thrown exception.

---

## Error Handling

### Authorization Errors
**Cause**: Credentials are missing or invalid.
**Error message**: "No Salsify credentials configured. Please validate the extension attributes first."
**Resolution**: Open the extension Setup tab, enter valid credentials, and save. See [Extension Configuration](/pages/ExtensionConfiguration.md).

### Input Errors
**Cause**: Invalid Org ID or API Key causes a 401/403/404 response from Salsify.

| HTTP Code | Error Message | Resolution |
|-----------|---------------|------------|
| 401 | "Invalid API token (401). Please verify your Salsify API Key." | Update the API Key in the extension Setup tab |
| 403 | "Access denied (403). The API Key does not have permission for this organisation." | Verify the Org ID matches the API Key's organisation |
| 404 | "Resource not found (404). Please verify the Org ID and product identifier." | Check the Org ID value |

### System Errors
**Cause**: Salsify API is rate-limiting requests or experiencing server errors.

| HTTP Code | Error Message | Resolution |
|-----------|---------------|------------|
| 429 (after 3 retries) | "Salsify rate limit exceeded. All retries exhausted. Please try again later." | Wait and retry; consider adding filters to reduce request frequency |
| 500 / 503 (after 1 retry) | "Salsify server error. Retry attempted once. Please try again later." | Check Salsify service status; retry after a brief delay |

> **📝 Note**: On any exception, `filterProducts` returns an **empty list** rather than propagating the error to the workflow. Check Krista logs for detailed error messages.

---

## Usage Examples

### Example 1: Single Primary Filter

**Input**:
```
Primary Filters:    { "PISGRV": "HBD - BIRTHDAY" }
Secondary Filters:  (blank)
Columns:            "Item#,Item Description,PIUPCD,PISGRV"
```

**Behavior**: One API call with `'PISGRV':'HBD - BIRTHDAY'`. Salsify pre-filters server-side; results returned as-is.

---

### Example 2: Primary Fan-Out (production-bug fix)

For large catalogs where multi-value matches sit beyond the 10K-per-shard cap, push the multi-value filter to Primary so Salsify does the work per-shard.

**Input**:
```
Primary Filters:    { "PISGRV": "BBY - BABY,WED - WEDDING", "PIDISC": "P,S" }
Secondary Filters:  (blank)
Columns:            "Item#,Item Description,PISGRV,PIDISC"
```

**Behavior**: The extension issues 4 API calls — `(BBY,P)`, `(BBY,S)`, `(WED,P)`, `(WED,S)` — and aggregates results. Each shard hits its own 10K cap independently, so unlike pre-1.0.6 client-side OR, products beyond row 10K of the unfiltered catalog still surface.

**Cartesian product**: `2 × 2 = 4 shards` — well under the cap of 20.

---

### Example 3: Primary + Secondary Refinement

**Input**:
```
Primary Filters:    { "PISGRV": "HBD - BIRTHDAY,HIGH VALUE" }
Secondary Filters:  { "PIDISC": "P,S", "PILICT": "Y" }
Columns:            "Item#,Item Description,PISGRV,PIDISC,PILICT"
```

**Behavior**:
1. Two API calls fan-out on PISGRV: `(HBD,PILICT='Y')` and `(HIGH,PILICT='Y')`. Single-value Secondary `PILICT='Y'` joins each shard as a base AND condition.
2. Aggregated results are then narrowed client-side: `PIDISC ∈ {P, S}`.

---

### Example 4: Include Empty Values for a Secondary Field

**Input**:
```
Primary Filters:    { "PISGRV": "HBD - BIRTHDAY,HIGH VALUE" }
Secondary Filters:  { "PIDISC": "P,S" }
Include Empty For:  "PIDISC"
Columns:            "Item#,Item Description,PISGRV,PIDISC"
```

**Result**: Products matching the primary fan-out, AND (`PIDISC ∈ {P, S}` OR `PIDISC` is unset).

---

### Example 5: "Find Products Missing a Field"

Use Include Empty For without listing the field in Secondary Filters.

**Input**:
```
Primary Filters:    { "PISGRV": "HBD - BIRTHDAY" }
Secondary Filters:  (blank)
Include Empty For:  "PIDISC"
Columns:            "Item#,Item Description,PISGRV,PIDISC"
```

**Result**: Products matching the primary, AND `PIDISC` is unset.

---

### Example 6: Array-Valued Columns

**Input**:
```
Primary Filters:    { "PISGRV": "HBD - BIRTHDAY" }
Columns:            "Item#,Item Description,DecoPac Alternate Items"
Page Size:          50
```

**Output** (note array preservation):
```json
[
  {
    "Item#": "10165",
    "Item Description": "POP TOPS-FELIZ CUMPLEANOS",
    "DecoPac Alternate Items": ["27239", "6667"]
  }
]
```

> **📝 Important**: Array values are returned as **arrays/lists**, NOT comma-separated strings. This allows you to iterate over individual items in workflows.

---

### Example 7: Offset-Based Pagination (single-shard primary only)

**Input**:
```
Primary Filters:    { "PISGRV": "HBD - BIRTHDAY" }
Columns:            "Item#,Item Description,PIUPCD"
Page Size:          50
Offset:             100
```

**Result**: Returns products 101–150 within the primary-filtered set. Offset calculation: `(pageNumber - 1) * pageSize`.

**Constraint**: Offset > 0 only works when Primary Filters produce exactly one shard (no fan-out).

---

## Business Rules

1. **Two-Stage Query**: Primary Filters fetch from Salsify server-side (cartesian fan-out across multi-value primaries). Secondary Filters refine the aggregated result — single-value secondaries become base AND conditions on every shard, multi-value secondaries are applied client-side.
2. **Primary Required**: At least one primary field with at least one value. Empty/missing Primary Filters returns a validation error.
3. **No Field Overlap**: A field cannot appear in both Primary Filters and Secondary Filters.
4. **Include Empty For**: Only valid for Secondary Filters fields. A primary field listed in Include Empty For triggers a validation error.
5. **Cartesian Cap**: Product of primary value counts must not exceed 20.
6. **Column Names**: Case-sensitive. Use exact Salsify property names.
7. **Missing Columns**: Included in the response map for every requested column. Default value is `null`. **Exception**: columns listed in Include Empty For receive `""` (empty string) instead of `null` for missing values, so Krista's table widget keeps the column header even in result sets dominated by null cells. Pre-existing empty strings in Salsify are preserved as-is.
8. **Array Preservation**: Array-valued fields are returned as arrays, not comma-separated strings.
9. **Invalid Filters**: Entries with null/empty key or value are skipped with a warning log; the request continues with remaining valid filters.
10. **Pagination Strategy**:
    - If `Offset` is **not provided** (null/0): each shard auto-paginates up to 10,000 products.
    - If `Offset` is **provided and > 0**: only valid for single-shard primaries; fetches one page at the given offset.
11. **Large Catalogs**: 10K-per-shard cap. Fan-out narrows individual shards so the cap rarely bites in practice — pick narrow primaries when possible.
12. **Empty Results**: On any exception, `filterProducts` returns an empty list with an error message in `Response Info` rather than propagating the exception to the workflow.

---

## Rate Limit Cost Model

Salsify enforces a rate limit of **5,000 requests per hour, per user, per session**. The Filter Products request consumes from this budget through pagination — each page of results is one HTTP call.

**Cost per query (rough estimates)**:

| Strategy | Requests per query (typical) | Notes |
|---|---|---|
| No filter | 1 page × pages-of-catalog (capped at 10K products / page size) | Up to 100 requests |
| Single AND filter | 1 page × pages-of-result | Usually 1–10 requests |
| Multi-value OR (no Primary Filters) | Same as no filter | Filters apply client-side; full unfiltered fetch |
| **Primary Filters fan-out** | **N shards × pages-per-shard** | N capped at 20; each shard usually 1–10 pages |

**Worst case** with cap=20 and shards each hitting 10K products = ~2,000 requests. Stay below ~2 worst-case queries per hour to leave headroom for retries and concurrent workflows. Realistic queries are far cheaper.

**Rules of thumb**:
- Pick narrow primary fields. A field whose values segment the catalog into roughly equal buckets gives the best per-shard speed.
- Multiple workflows under the same Salsify user share the budget. Heavy fan-out workflows can starve other workflows.
- The retry-on-429 backoff (2s/4s/8s, 3 attempts per page) consumes additional budget when triggered.

---

## Limitations

1. **Maximum 10,000 products per shard** — Each API call (one shard, or the no-filter case) caps at 10,000 products. Use Primary Filters to keep individual shards narrow on large catalogs.
2. **Cartesian cap of 20** — Primary Filters cannot produce more than 20 fan-out combinations. Reduce values per primary field, or move a field to secondary.
3. **Offset-based pagination returns single page** — When Offset is provided, only one page of results is returned.
4. **Offset incompatible with multi-shard fan-out** — If Primary Filters produce more than one shard, Offset must be 0 or unset. Otherwise the request fails with a validation error.
5. **Equality operator only** — Filters use exact equality matching. Advanced operators (contains, starts_with, gt, lt, etc.) are not supported.
6. **Comma as OR separator** — The comma character (`,`) is reserved as the OR separator within filter values. Values that legitimately contain a comma cannot be used in OR filters.
7. **Secondary OR filters applied client-side** — Salsify's filter syntax does not support parenthetical grouping. The OR operator (`=`) has lower precedence than AND (`,`), so combining them produces incorrect results. Non-primary multi-value filters are evaluated in Java after fetching results.
8. **Dynamic field selection only** — You select which columns to include; all other columns are excluded.
9. **Salsify API rate limits** — 5,000 requests/hour per user/session. The extension retries automatically (up to 3 times with backoff) but sustained high-frequency calls may be delayed.
10. **Network timeouts** — Connect timeout 30s, read timeout 60s, write timeout 30s.

---

## Best Practices

### 1. Always Provide Specific Columns
Rather than requesting all columns and filtering in Krista, specify only the columns you need. This reduces response payload size and processing time.

### 2. Pick Narrow Primary Filters
Each primary shard caps at 10K products. Choose primary fields whose values segment the catalog into roughly equal, smaller buckets — that way each shard returns far fewer than 10K and the cap rarely bites.

**Good** (each value typically returns < 5K products):
```
Primary Filters:    { "PISGRV": "BBY - BABY,WED - WEDDING" }
Secondary Filters:  { "PIDISC": "P,S" }
```

**Not ideal** (PIDISC has only 2 values, each ~50% of catalog — shards may exceed 10K):
```
Primary Filters:    { "PIDISC": "P,S" }
Secondary Filters:  { "PISGRV": "BBY - BABY,WED - WEDDING" }
```

### 3. Handle Array Values Correctly
Array-valued fields are returned as arrays. Write workflows to iterate over array items, not split comma-separated strings.

**Good workflow**: For each item in `DecoPac Alternate Items`, perform an action.
**Avoid**: Splitting `DecoPac Alternate Items` by comma.

### 4. Use Offset-Based Pagination for Navigation
For UI pagination, use offset-based pagination. Set `Page Size` to a reasonable value (e.g., 50 items per page) and calculate offset based on user's page selection.

### 5. Handle Missing Columns Gracefully
Requested columns missing from a product appear as `null` in the response map. Use `null` checks or defaults in downstream workflow steps.

### 6. Avoid Tight Loop Calls
Do not invoke `filterProducts` in a tight loop or at very high frequency. Salsify rate limits are enforced server-side. Space out calls and use filters to minimize requests.

---

## Common Use Cases

### 1. Sync Active Products to Downstream System

**Scenario**: Export active products to an ERP or order management system.
```
Primary Filters:    { "PIDISC": "N" }
Columns:            "Item#,Item Description,PIUPCD,PICPRC,PISIZE,PICTRY"
```

---

### 2. Identify Discontinued Products and Replacements

**Scenario**: Find all discontinued products and their replacements.
```
Primary Filters:    { "PIDISC": "Y" }
Columns:            "Item#,Item Description,PIREPL"
```

---

### 3. Filter by Multiple Custom Properties

**Scenario**: Licensed, high-value products.
```
Primary Filters:    { "PISGRV": "HIGH VALUE" }
Secondary Filters:  { "PILICT": "Y" }
Columns:            "Item#,Item Description,PIUPCD,PILICT,PISGRV"
```

---

### 4. Multi-Value Cross-Section (cartesian fan-out)

**Scenario**: Birthday or wedding products that are pop-tops or low-cost.
```
Primary Filters:    { "PISGRV": "BBY - BABY,WED - WEDDING", "PICCDE": "POPT,LCST" }
Columns:            "Item#,Item Description,PISGRV,PICCDE"
```

**Result**: 4 fan-out shards run server-side, results aggregated.

---

### 5. Paginate Through a Single Primary Filter

**Scenario**: Page through Birthday products 100 at a time.
```
Primary Filters:    { "PISGRV": "BBY - BABY" }
Columns:            "Item#,Item Description,PIUPCD"
Page Size:          100
Offset:             0 (first page), 100 (second), 200 (third), ...
```

**Constraint**: Single-shard primary only — Offset is rejected when fan-out produces multiple shards.

---

## Technical Implementation

### Area Class
- **Class**: `SalsifyProductArea`
- **Package**: `com.krista.krista.extension.salsify.catalog`
- **Method**: `filterProducts(Map<String, Object> primaryFilters, Map<String, Object> secondaryFilters, String columns, String includeEmptyFor, Double pageSize, Double offset)`

### Service Delegation
- `SalsifyProductArea` delegates to `SalsifyServiceImpl.getAllProducts(orgId, apiKey, filterString, pageSize, offset)`.
- Dynamic filters are converted to Salsify filter expressions internally.
- Column selection is performed on the response using `DynamicColumnSelector`.

### Pagination Strategy
Two strategies based on the `Offset` parameter:

**Strategy 1: Automatic Pagination (offset not provided/null)**
1. First page fetched with built filter expression.
2. If the `Link` response header contains `rel="next"`, that URL is followed.
3. If no `Link` header, page-number pagination is used (`?page=N`).
4. Continues fetching all pages and combines results.
5. Stops when all pages exhausted or 10,000 products accumulated.

**Strategy 2: Offset-Based Pagination (offset >= 0)**
1. Fetches a single page using the offset parameter.
2. Returns only the page starting at the given offset, without following to subsequent pages.
3. Allows end-users to navigate results flexibly page-by-page.
4. Offset = 0 starts from the first record.

---

## Troubleshooting

### No products returned despite known data existing
**Cause**: Filter property code is incorrect or column selection is too restrictive.
**Solution**:
1. Verify filter property codes in Salsify product schema.
2. Ensure columns include at least one field that exists in products.
3. Check Krista logs for filter building details.

### Fewer products returned than expected
**Cause**: Large-catalog threshold (10,000) reached, or filter too restrictive.
**Solution**:
1. Review filter conditions.
2. For catalogs exceeding 10,000, use offset-based pagination.
3. Broaden filters if needed.

### Array fields appearing as comma-separated strings
**Cause**: Array values from Salsify are flattened to comma-separated strings (for Excel-cell compatibility). Arrays of objects (e.g. `salsify:relations`) are flattened to JSON-encoded strings.
**Solution**: If a downstream step needs to iterate items, split the comma-separated string. If array shape matters semantically (array of objects), parse the JSON string.

### Cells appear as `null` for one row but not others
**Cause**: A single product had a malformed value for a column (e.g. `Main Image` returned as a JSON array on a product where every other product returns a scalar). The extractor catches the per-column failure and emits `null` for that cell so the rest of the row and the rest of the result set still render.
**Solution**: Search Krista logs for `WARN | ... | DynamicColumnSelector | Failed to extract column 'X' for product (salsify:id=Y)` — the message names the offending product and column. Inspect the product in Salsify to confirm the value shape, and either correct it or drop the column from the request.

### Whole row appears as nulls for one product
**Cause**: A product threw during column extraction outside the per-column boundary. The orchestrator catches the per-product failure, emits an all-null map keyed by the requested columns, and continues with the rest of the result set.
**Solution**: Search Krista logs for `WARN | ... | FilterProductsOrchestrator | Failed to extract columns for product (salsify:id=Y); returning nulls`. Inspect the named product in Salsify.

### Empty list returned unexpectedly
**Cause**: Exception occurred at the request boundary (credentials invalid, network error, validation failure, etc.). Per-product extraction failures no longer surface here as of 1.0.6 — they degrade individual rows to nulls instead.
**Solution**: Check Krista application logs for the `filterProducts` method. Look for `ERROR` log entries from `SalsifyProductArea`.

### Slow response
**Cause**: Large catalog with many pages, or Salsify rate limiting.
**Solution**:
1. Add property filters to reduce result size.
2. Set `Page Size` to a smaller value to reduce individual response payload sizes.
3. Use offset-based pagination for specific pages instead of automatic full pagination.

---

## See Also

- [Extension Configuration](/pages/ExtensionConfiguration.md)
- [Authentication](/pages/Authentication.md)
- [Creating a Salsify API Key](/pages/CreatingSalsifyAccount.md)
