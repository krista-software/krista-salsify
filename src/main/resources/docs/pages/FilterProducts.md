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

### Filters (Composite - Required)

A composite map (Map<String, Object>) where keys are Salsify property codes and values are the filter values. Use exact equality matching.

**Example filter format**:
```json
{
  "PISGRV": "HBD - BIRTHDAY",
  "PILICT": "Y",
  "PIDISC": "N"
}
```

All filters are combined with AND logic. You can filter by ANY Salsify property code.

### Columns (Text - Required)

A comma-separated list of column names to include in the response. Use exact Salsify property names (case-sensitive).

**Example**:
```
Item#, Item Description, PIUPCD, PISIZE, DecoPac Alternate Items
```

The response will contain only these columns, preserving their original names and data types.

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
- **Missing columns** are omitted from the map (not included with null values)

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
> **📝 Note**: If a requested column is missing from a product, it is omitted from the response map.

---

## Validation Rules

| Validation | Error Message | Resolution |
|------------|---------------|------------|
| Credentials must be configured | "No Salsify credentials configured. Please validate the extension attributes first." | Complete the extension Setup tab. See [Extension Configuration](/pages/ExtensionConfiguration.md) |
| Filters must be a valid composite map | *(filter skipped)* | Provide a map with Salsify property codes as keys |
| Columns parameter must be provided | *(error)* | Provide a comma-separated list of column names |
| Column names are case-sensitive | *(column omitted from result)* | Use exact Salsify property names (e.g., `"Item#"` not `"item#"`) |

> **📝 Note**: Invalid filter entries (null/empty key or value) are skipped with a warning log; the request continues with remaining valid filters.

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

### Example 1: Retrieve Specific Columns from All Products

**Input**:
```
Filters: {} (empty - no filters)
Columns: "Item#,Item Description,PIUPCD,PISIZE"
Page Size: (blank)
Offset: (blank)
```

**Output**:
```json
[
  {
    "Item#": "10165",
    "Item Description": "POP TOPS-FELIZ CUMPLEANOS",
    "PIUPCD": "607772101654",
    "PISIZE": "24/PKG"
  },
  {
    "Item#": "10166",
    "Item Description": "Another Product",
    "PIUPCD": "812345678901",
    "PISIZE": "12 CT"
  }
]
```

---

### Example 2: Filter by Multiple Properties with Column Selection

**Input**:
```
Filters: {
  "PISGRV": "HBD - BIRTHDAY",
  "PIDISC": "N"
}
Columns: "Item#,Item Description,PIUPCD,PISGRV,PIDISC"
Page Size: (blank)
Offset: (blank)
```

**Result**: Returns only products where `PISGRV = "HBD - BIRTHDAY"` AND `PIDISC = "N"`, with only the specified columns.

---

### Example 3: Filter by Any Salsify Property

**Input**:
```
Filters: {
  "PICPRC": "6.69",
  "PICTRY": "CN - CHINA"
}
Columns: "Item#,Item Description,PICPRC,PICTRY"
```

**Result**: Returns products with contract price of 6.69 AND country of origin "CN - CHINA".

---

### Example 4: Include Array-Valued Fields

**Input**:
```
Filters: {} (empty)
Columns: "Item#,Item Description,DecoPac Alternate Items"
Page Size: 50
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

### Example 5: Fetch a Specific Page Using Offset

**Input**:
```
Filters: {} (empty)
Columns: "Item#,Item Description,PIUPCD"
Page Size: 50
Offset: 100
```

**Result**: Returns products 101–150 (the 3rd page of 50 items per page). Offset calculation: `(pageNumber - 1) * pageSize` = `(3 - 1) * 50` = `100`.

**Use case**: End-users can navigate through results page-by-page without fetching all products. Efficient for large catalogs.

---

## Business Rules

1. **Filter Combination**: All filters are combined with **AND** logic. There is no OR support within a single call.
2. **Column Names**: Column names are **case-sensitive**. Use exact Salsify property names (e.g., `"Item#"` not `"item#"`).
3. **Missing Columns**: If a requested column is not present in a product, it is **omitted from the response map** (not included with null values).
4. **Array Preservation**: Array-valued fields (e.g., `DecoPac Alternate Items`) are returned as **arrays**, NOT comma-separated strings.
5. **Invalid Filters**: Filter objects with null/empty field or value are silently skipped with a warning log; valid filters continue to be applied.
6. **Pagination Strategy**:
   - If `Offset` is **not provided** (null): Automatic pagination is used. The extension fetches all pages and returns a combined list (maximum **10,000 products**).
   - If `Offset` is **provided** (0 or positive number): Offset-based pagination is used. A single page of results starting at that offset is returned.
7. **Offset Bounds**: Offset values must be non-negative and not exceed 1,000,000. Invalid offsets return an error.
8. **Large Catalogs**: When using **automatic pagination** (offset not provided): If the total product count reaches **10,000**, pagination stops. Apply filters to reduce result size for larger catalogs.
9. **Empty Results**: The extension returns an empty list (not an error) if an unexpected exception occurs. Check logs for details.

---

## Limitations

1. **Maximum 10,000 products in automatic mode** — When Offset is NOT provided, results are capped at 10,000 products. For catalogs larger than 10,000, apply filters to narrow results, or use offset-based pagination.
2. **Offset-based pagination returns single page** — When Offset is provided, only one page of results is returned.
3. **Equality operator only** — Filters use exact equality matching. Advanced operators (contains, starts_with, gt, lt, etc.) are not supported.
4. **No OR logic** — All filter conditions are AND-combined. Cannot request A OR B in a single call.
5. **Dynamic field selection only** — You select which columns to include; all other columns are excluded.
6. **Salsify API rate limits** — Salsify enforces rate limits. The extension retries automatically (up to 3 times with backoff) but sustained high-frequency calls may be delayed.
7. **Network timeouts** — Connect timeout 30s, read timeout 60s, write timeout 30s.

---

## Best Practices

### 1. Always Provide Specific Columns
Rather than requesting all columns and filtering in Krista, specify only the columns you need. This reduces response payload size and processing time.

### 2. Use Filters to Reduce Results
For large catalogs, apply filters to push conditions to Salsify. This reduces network traffic and API calls.

**Good**:
```
Filters: {"PIDISC": "N"}
Columns: "Item#,Item Description,PIUPCD"
```

**Not ideal**:
```
Filters: {} (fetch all products and filter in Krista)
Columns: "Item#,Item Description,PIUPCD,PIDISC,..."
```

### 3. Handle Array Values Correctly
Array-valued fields are returned as arrays. Write workflows to iterate over array items, not split comma-separated strings.

**Good workflow**: For each item in `DecoPac Alternate Items`, perform an action.
**Avoid**: Splitting `DecoPac Alternate Items` by comma.

### 4. Use Offset-Based Pagination for Navigation
For UI pagination, use offset-based pagination. Set `Page Size` to a reasonable value (e.g., 50 items per page) and calculate offset based on user's page selection.

### 5. Handle Missing Columns Gracefully
If a requested column is missing from products, it won't appear in the response map. Use `null` checks or defaults in your workflows.

### 6. Avoid Tight Loop Calls
Do not invoke `filterProducts` in a tight loop or at very high frequency. Salsify rate limits are enforced server-side. Space out calls and use filters to minimize requests.

---

## Common Use Cases

### 1. Sync Product Data to Downstream System

**Scenario**: Export active products to an ERP or order management system.

**Action**: Call filterProducts with filter for active status and select required fields.
```
Filters: {"PIDISC": "N"}
Columns: "Item#,Item Description,PIUPCD,PICPRC,PISIZE,PICTRY"
```

**Result**: Returns the active product list with essential fields for system sync.

---

### 2. Identify Discontinued Products and Replacements

**Scenario**: Find all discontinued products and their replacements.

**Action**: Call filterProducts with discontinued status filter.
```
Filters: [{"field": "PIDISC", "value": "Y"}]
Columns: "Item#,Item Description,PIREPL"
```

**Result**: Returns discontinued products with replacement item references.

---

### 3. Filter by Custom Property

**Scenario**: Get all products with license type in a specific revenue segment.

**Action**: Apply multiple filters.
```
Filters: {
  "PILICT": "Y",
  "PISGRV": "HIGH VALUE"
}
Columns: "Item#,Item Description,PIUPCD,PILICT,PISGRV"
```

**Result**: Returns licensed, high-value products.

---

### 4. Paginate Through Large Catalog

**Scenario**: Navigate a 50,000-product catalog 100 items per page.

**Action**: Use offset-based pagination.
```
Page Size: 100
Offset: 0 (first page), 100 (second page), 200 (third page), etc.
Columns: "Item#,Item Description,PIUPCD"
```

**Result**: Efficiently retrieve specific pages without fetching entire catalog.

---

## Technical Implementation

### Area Class
- **Class**: `SalsifyProductArea`
- **Package**: `com.krista.krista.extension.salsify.controller`
- **Method**: `filterProducts(List<Map<String, Object>> filters, String columns, Double pageSize, Double offset)`

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
**Cause**: Workflow is splitting string representation instead of using native array type.
**Solution**: Treat array-valued fields as lists in your workflow. Iterate directly over array items without splitting by comma.

### Empty list returned unexpectedly
**Cause**: Exception occurred (credentials invalid, network error, etc.).
**Solution**: Check Krista application logs for the `filterProducts` method. Look for `ERROR` log entries.

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
