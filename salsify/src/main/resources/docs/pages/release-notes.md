# Release Notes

## Version 1.0.8 - Current Release

**Release Date**: May 2026
**Developer**: Asem Abdelfattah

### Version Information

| Component | Version     |
|-----------|-------------|
| Extension Version | 1.0.8       |
| Krista APIs | 1.0.121     |
| Salsify API | v1          |
| Global Catalog Version | GC-2026.5.1 |

### What's New in 1.0.8

**Fix: PIDISC-style "Include Empty For" columns no longer drop their headers from the result table.**

When a column is listed in `Include Empty For`, the resulting product set has a high proportion of `null` cells for that column (because include-empty intentionally pulls in products where the field is unset). Krista's table widget was dropping the column header when the wire payload had `null` values in that field, even though the extension was correctly inserting the column key for every product (per the 1.0.6 contract).

`filterProductsByColumns` now substitutes `""` (empty string) for `null` cells **only on columns listed in Include Empty For**. The substitution guarantees a non-null value reaches Krista's renderer so the column header survives. For Excel-cell workflows, `""` and `null` render identically as a blank cell, so no visible difference.

### Behavioral Notes

- The substitution is **scoped to Include Empty For fields only**. Regular columns still return `null` for missing values (unchanged contract).
- Pre-existing empty strings (e.g. a product with `PIDISC` literally set to `""` in Salsify) are not modified — only `null` values are substituted.
- Per-product try/catch escape rows are also covered: if a product fails extraction, its all-null row gets the same substitution for include-empty columns so the column header stays consistent.

### Workflow Impact

Workflow code that does `if (cell == null)` checks on Include Empty For columns will need to switch to `if (cell.isEmpty())` (or accept both). Workflows that don't use Include Empty For are unaffected. For Excel-cell workflows, no code change needed.

### Tests

6 new tests cover the substitution behavior: null → "" for include-empty columns, no substitution for non-include-empty columns, no override of pre-existing empty strings, no-op for include-empty fields not in the requested columns, and substitution applies to per-product escape rows.

### Known Limitations (carried forward)
- Maximum 10,000 products per shard.
- Cartesian product of primary value counts must not exceed 20.
- Offset-based pagination is incompatible with multi-shard fan-out.
- Equality operator only in API.
- Salsify rate limit: 5,000 requests/hour per user/session.

---

## Version 1.0.7

**Release Date**: April 2026
**Developer**: Asem Abdelfattah

### Version Information

| Component | Version |
|-----------|---------|
| Extension Version | 1.0.7 |
| Krista APIs | 1.0.121 |
| Salsify API | v1 |
| Global Catalog Version | GC-2026.4.1 |

### What's New in 1.0.7

**Production 500 fix: `Main Image` array tolerance and extraction resilience.**

This is a bugfix release. No input parameters or output structure changed — existing workflows require no updates.

- **Fix: `Main Image` as array no longer crashes the query** — Some products in Salsify carry `Main Image` as a JSON array of asset IDs rather than a scalar string. The previous code called `.getAsString()` on the field directly; GSON threw `Array must have size 1, but has size 2` and killed the entire query mid-extraction. A new `extractMainImageIds` helper now checks the field shape first — scalar strings continue to work unchanged; arrays are iterated and the URL of the first matching asset in `salsify:digital_assets` is returned. Bug was introduced in 1.0.1 and affected versions 1.0.1 through 1.0.6.

- **Fix: per-column extraction boundary** — Each `extractColumnValue` call inside `selectColumns` is now wrapped in try/catch. A single malformed column on one product no longer unwinds the entire row. On failure the cell is set to `null` and a `WARN` log captures the product ID, column name, and cause so the offending product can be found in Salsify.

- **Fix: per-product extraction boundary** — Each `selectColumns` call inside `filterProductsByColumns` is now wrapped in try/catch. Any extraction failure that escapes the per-column boundary no longer halts the full query. The product is emitted as an all-null row (column set stays consistent) and a `WARN` log identifies it. A 700-product query with one malformed product now completes cleanly.

### Operational Notes

When extraction degrades, search Krista logs for:
- `WARN | DynamicColumnSelector | Failed to extract column 'X' for product (salsify:id=Y)` — single cell degraded to null
- `WARN | FilterProductsOrchestrator | Failed to extract columns for product (salsify:id=Y); returning nulls` — full row degraded to null

The product ID in the log names the offending product to inspect and correct in Salsify.

### Known Limitations
- Maximum 10,000 products **per shard**. Pick narrow primaries to keep shards under the cap.
- Cartesian product of primary value counts must not exceed 20.
- Offset-based pagination is incompatible with multi-shard fan-out.
- Equality operator only in API (no contains, starts_with, gt/lt operators).
- Comma character (`,`) is reserved as the OR separator within filter values.
- Secondary OR filters are applied client-side due to Salsify API grouping limitations.
- Salsify rate limit: 5,000 requests/hour per user/session.

---

## Version 1.0.6

**Release Date**: April 2026
**Developer**: Asem Abdelfattah

### Version Information

| Component | Version |
|-----------|---------|
| Extension Version | 1.0.6 |
| Krista APIs | 1.0.121     |
| Salsify API | v1          |
| Global Catalog Version | GC-2026.4.1 |

### What's New in 1.0.6

**Two-stage query model.** `filterProducts` now takes two filter maps:
- **Primary Filters** (Composite, **required**) — pushed to Salsify server-side via cartesian fan-out. Each value combination across primary fields becomes one API call.
- **Secondary Filters** (Composite, optional) — applied to the aggregated result. Single-value secondary filters become base AND conditions on every shard; multi-value secondary filters apply client-side.

The previous single `Filters` parameter is renamed to **Secondary Filters** and is now optional. The text-based Primary Filters parameter from earlier 1.0.6 design iterations is gone — primaries now carry their own values directly.

**Why**: a production bug where multi-value-only OR queries returned fewer matches than narrower queries (because the unfiltered fetch capped at 10K and never reached the matches). Forcing every workflow to designate at least one server-side filter eliminates the silent 10K trap.

**Other changes**:
- **Cartesian cap of 20** — primary value counts can't multiply beyond 20 shards. Errors include a breakdown (e.g. `PISGRV×5, PIDISC×5 = 25`) so users know which field to trim.
- **`Include Empty For` scoped to Secondary** — listing a primary field in Include Empty For triggers a validation error. Empty-match semantics require client-side evaluation, which is incompatible with server-side fan-out.
- **Offset incompatible with multi-shard fan-out** — rejected with a clear validation error. Single-shard primaries with Offset still work.
- **Rate Limit Cost Model docs** — `FilterProducts.md` now documents Salsify's 5,000-req/hour budget and per-strategy cost.
- **Fix: missing columns now appear as `null`** — Previously, if a requested column had no value on a product, the column key was omitted from that product's response map. Combined with `Include Empty For`, this caused the result table to drop columns whenever a no-value product happened to be the first row. The response map now always contains every requested column key, with `null` for missing values, so the table renders consistently.
- **Fix: `Main Image` now tolerates array values** — Some products carry `Main Image` as an array of asset IDs rather than a scalar. The previous code called `.getAsString()` on the field and threw `Array must have size 1, but has size 2` when the array had multiple entries, killing the entire query mid-extraction. `extractSalsifyUrl` now accepts both scalar and array shapes, iterating asset IDs and returning the URL of the first match in `salsify:digital_assets`.
- **Fix: per-column and per-product extraction boundaries** — A single malformed product or column no longer kills the whole query. Per-column failures are caught in `DynamicColumnSelector.selectColumns`; per-product failures are caught in `FilterProductsOrchestrator.filterProductsByColumns`. In both cases the offending cell/row degrades to `null` and a `WARN` log entry names the product (`salsify:id`) and column so operators can find the bad data in Salsify. The rest of the result set is unaffected.

### Input Parameters (new shape)

| # | Field | Type | Required |
|---|---|---|---|
| 1 | Primary Filters | Composite | ✅ |
| 2 | Secondary Filters | Composite | ❌ |
| 3 | Columns | Text | ✅ |
| 4 | Include Empty For | Text | ❌ |
| 5 | Page Size | Number | ❌ |
| 6 | Offset | Number | ❌ |

### Migration Guide (1.0.5 → 1.0.6)

Every existing workflow needs an update because the input shape changed.

| 1.0.5 input | 1.0.6 input |
|---|---|
| `Filters: { "PISGRV": "BBY - BABY" }` | `Primary Filters: { "PISGRV": "BBY - BABY" }` |
| `Filters: { "PISGRV": "BBY - BABY", "PIDISC": "N" }` (both single-value) | `Primary Filters: { "PISGRV": "BBY - BABY" }`, `Secondary Filters: { "PIDISC": "N" }` (or both in primary — your call) |
| `Filters: { "PISGRV": "BBY,WED", "PIDISC": "P,S" }` (multi-value, hit by the production bug) | `Primary Filters: { "PISGRV": "BBY,WED", "PIDISC": "P,S" }` — fan-out resolves the 10K trap |
| `Filters: {}` (browse all) | **No equivalent**: Primary Filters is required. Add at least one filter (e.g. `{ "PIDISC": "N" }` for active products). |
| `Filters: { "PISGRV": "BBY,WED" }`, `Include Empty For: "PISGRV"` | **Move PISGRV to Secondary**: `Secondary Filters: { "PISGRV": "BBY,WED" }`, `Include Empty For: "PISGRV"`, plus a primary placeholder. Include Empty For is incompatible with primary. |

### Known Limitations
- Maximum 10,000 products **per shard**. Pick narrow primaries to keep shards under the cap.
- Cartesian product of primary value counts must not exceed 20.
- Offset-based pagination is incompatible with multi-shard fan-out.
- Equality operator only in API (no contains, starts_with, gt/lt operators).
- Comma character (`,`) is reserved as the OR separator within filter values.
- Secondary OR filters are applied client-side due to Salsify API grouping limitations.
- Salsify rate limit: 5,000 requests/hour per user/session — heavy fan-out workflows share the budget.

---

## Version 1.0.5

**Release Date**: April 2026
**Developer**: Asem Abdelfattah

### Version Information

| Component | Version     |
|-----------|-------------|
| Extension Version | 1.0.5       |
| Krista APIs | 1.0.121     |
| Salsify API | v1          |
| Global Catalog Version | GC-2026.4.5 |

### What's New in 1.0.5
- **Fix: OR filters no longer silently include products with unset values** — Previously, any multi-value OR filter (e.g. `"PISGRV": "Baby,Wed"`) also matched products where the filtered property was null, empty, or missing — as if every OR filter implicitly carried an "or unset" rider. When two OR filters were combined (e.g. `"PISGRV": "Baby,Wed"` AND `"PIDISC": "S,P"`), the result set collapsed toward products where *all* filtered fields were unset, hiding real matches. OR filters are now strict: a product must have a value that appears in the allowed list.
- **New: `Include Empty For` parameter** — Opt-in control to include products with null/empty/missing values for specific fields. Supply a comma-separated list of Salsify property codes (e.g. `"PISGRV, PIDISC"`) to restore the old "value-or-unset" behavior on a per-field basis. A field listed here but absent from `Filters` returns only products where that property is unset — useful for "find products missing X" queries.
- **Internal cleanup** — Removed dead `parseFiltersToString` / `buildEmptyExpression` / `SALSIFY_EMPTY_MATCH` (−418 LOC) carried over from the pre-1.0.4 API-side OR path.

### Migration Guide (1.0.4 → 1.0.5)
If any of your workflows relied on the 1.0.4 behavior where OR filters implicitly included unset values, add those property codes to the new `Include Empty For` field. Workflows that do not use multi-value OR filters, or that relied only on the exact matches, require no changes.

| 1.0.4 behavior | 1.0.5 equivalent |
|---|---|
| `Filters: {"PISGRV": "Baby,Wed"}` returned matches **plus** PISGRV-unset | `Filters: {"PISGRV": "Baby,Wed"}` + `Include Empty For: "PISGRV"` |
| `Filters: {"PISGRV": "Baby,Wed"}` → strict matches only (not previously possible) | `Filters: {"PISGRV": "Baby,Wed"}` (default) |

### Known Limitations
- Maximum 10,000 products per query (apply filters for larger catalogs)
- Equality operator only in API (no contains, starts_with, gt/lt operators)
- Comma character (`,`) is reserved as the OR separator within filter values
- OR filters are applied client-side due to Salsify API grouping limitations (API may return a superset when OR filters are active)
- Subject to Salsify API rate limits

---

## Version 1.0.4

**Release Date**: April 2026
**Developer**: Asem Abdelfattah

### Version Information

| Component | Version     |
|-----------|-------------|
| Extension Version | 1.0.4       |
| Krista APIs | 1.0.121     |
| Salsify API | v1          |
| Global Catalog Version | GC-2026.4.1 |

### What's New in 1.0.4
- **Fix: Client-Side OR Filtering** — OR filters (comma-separated or Pick List multi-select) are now applied client-side in Java instead of being sent to the Salsify API. This fixes a critical bug where combining AND and OR filters produced incorrect results (returning thousands of unrelated records). The Salsify filtering language does not support parenthetical grouping — the OR operator (`=`) has lower precedence than AND (`,`), causing OR conditions to "leak" globally across all filter groups. Single-value (AND) filters are still sent to the API for server-side filtering; multi-value (OR) filters are evaluated after fetching results.
- **Accurate Total Records** — When OR filters are applied client-side, the Total Records count now reflects the filtered result count rather than the API's superset count.

### Known Limitations
- Maximum 10,000 products per query (apply filters for larger catalogs)
- Equality operator only in API (no contains, starts_with, gt/lt operators)
- Comma character (`,`) is reserved as the OR separator within filter values
- OR filters are applied client-side due to Salsify API grouping limitations (API may return a superset when OR filters are active)
- Subject to Salsify API rate limits

---

## Version 1.0.3

**Release Date**: March 2026
**Developer**: Asem Abdelfattah

### Version Information

| Component | Version     |
|-----------|-------------|
| Extension Version | 1.0.3       |
| Krista APIs | 1.0.121     |
| Salsify API | v1          |
| Global Catalog Version | GC-2026.4.1 |

### What's New in 1.0.3
- **Pick List Multi-Select Support** — Filter values from Pick List inputs with multiple selections are now handled natively as lists, in addition to comma-separated strings
- **Auto-Include Empty/Null Values** — When OR logic is used, products where the filtered field is null or empty are automatically included via the `^*` operator
- **Extension Renamed** — Extension name changed from "Salsify Extension" to "Salsify"

### Known Limitations
- Maximum 10,000 products per query (apply filters for larger catalogs)
- Equality operator only in API (no contains, starts_with, gt/lt operators)
- Comma character (`,`) is reserved as the OR separator within filter values
- Subject to Salsify API rate limits

---

## Version 1.0.2

**Release Date**: March 2026
**Developer**: Asem Abdelfattah

### Version Information

| Component | Version     |
|-----------|-------------|
| Extension Version | 1.0.2       |
| Krista APIs | 1.0.121     |
| Salsify API | v1          |
| Global Catalog Version | GC-2026.4.1 |

### Changes
- **OR Logic for Filters** — Comma-separated values within a single filter key are now treated as OR conditions (e.g., `"PISGRV": "Value1,Value2"` matches either value). Different filter keys remain AND-combined. Uses Salsify's native `=` OR operator.
- **Extension Description** — Added description to the `@Extension` annotation.
- **Package Rename** — Renamed `controller` package to `catalog` for consistency with domain terminology.
- **Version Bump** — Extension version updated from 1.0.1 to 1.0.2.

---

## Version 1.0.1

**Release Date**: March 2026
**Developer**: Vivek Sinha

### Version Information

| Component | Version     |
|-----------|-------------|
| Extension Version | 1.0.1       |
| Krista APIs | 1.0.121     |
| Salsify API | v1          |
| Global Catalog Version | GC-2026.3.2 |

### Features
- ✅ **Bearer Token Authentication** — API Key-based authentication with automatic bearer token injection
- ✅ **Dynamic Composite Filtering** — Query Salsify products with ANY property code and value via multi-field input, AND-logic combined
- ✅ **Dynamic Column Selection** — Select and return only requested columns in response via comma-separated text field
- ✅ **Flexible Filter Input** — Support for any Salsify property code as filter field with corresponding value parameter
- ✅ **Automatic Pagination** — Transparent handling of multi-page results (up to 10,000 products)
- ✅ **Offset-Based Pagination** — Flexible page navigation using offset parameter for end-user control
- ✅ **Asset URL Extraction** — Automatic extraction of product image URLs from Salsify digital assets
- ✅ **Resilient HTTP Client** — Exponential backoff on rate limiting (429) and automatic retry on server errors (500/503)
- ✅ **Secure Credential Storage** — API Keys encrypted at rest in Krista KeyValueStore
- ✅ **Property Mapping** — Salsify property codes mapped to human-readable entity fields
- ✅ **Dynamic Response Mapping** — Map any Salsify property to output columns with native type preservation (arrays, objects, primitives)

### Catalog Requests
- **Filter Products** — Retrieve and filter products from Salsify PXM

### Configuration
- Org ID (required)
- API Key (required, secured)

### Validation & Testing
- ✅ Live credential validation on save
- ✅ Test Connection button support
- ✅ Comprehensive error mapping for HTTP status codes

### What's New in 1.0.1
- **Dynamic Composite Filters** — No longer limited to 4 static filter fields; filter by ANY Salsify property code
- **Column Selection** — Choose which columns to display in response results via comma-separated text input
- **Case-Sensitive Column Names** — Preserve exact column naming as provided by user
- **Array Value Handling** — Support for array-valued Salsify properties in responses
- **Improved Test Coverage** — Enhanced test suite with 248 comprehensive tests (95% code coverage on helpers)
- **Code Quality** — Resolved 15+ SonarQube issues for maintainability and reliability

### Known Limitations
- Maximum 10,000 products per query (apply filters for larger catalogs)
- Equality operator only in API (no contains, starts_with, gt/lt operators)
- No OR logic (only AND-combined filters)
- Subject to Salsify API rate limits

### Breaking Changes
- **Filter Products API Updated** — Method signature changed from 4 static filter parameters to dynamic composite filter object with column selection field
  - Old: `filterProducts(segmentationRevenueAvenue, licenseType, discCode, customItem, pageSize, offset)`
  - New: `filterProducts(filters, columns, pageSize, offset)` where filters is a composite object and columns is a comma-separated string
- **Response Structure Changed** — Now returns `List<Map<String, Object>>` instead of typed entity list for maximum flexibility

### Migration Guide (from v1.0.0 to v1.0.1)
To use the new dynamic filtering and column selection:
1. Update your filter input: Instead of 4 separate filter fields, use composite filter object with format: `{field: "PROPERTY_CODE", value: "filter_value"}`
2. Update your column selection: Specify desired columns as comma-separated string (e.g., "Item#,Item Description,PIUPCD")
3. Results will only contain requested columns with native types preserved

### Bug Fixes
- Fixed SonarQube maintainability issues (15+ fixes)
- Improved error handling and logging
- Enhanced code test coverage from 85% → 95%

### Deprecations
None.

### Security
- API Key masked in UI
- Encrypted storage in KeyValueStore
- No logging of credentials
- Bearer token attachment on all requests

### Supported Salsify Editions
- Salsify Cloud (v1 API)
- Salsify Enterprise (v1 API)

### Next Steps
1. See [Extension Configuration](/pages/ExtensionConfiguration.md) to set up credentials
2. See [Creating a Salsify API Key](/pages/CreatingSalsifyAccount.md) if you don't have an API Key
3. Use [Filter Products](/pages/FilterProducts.md) to start querying your catalog

---

## Upgrade Notes

### Version 1.0.0 → 1.0.1

**BREAKING CHANGE**: The Filter Products method signature has changed. This version introduces dynamic filtering and column selection which require updates to existing integrations.

**Required Actions**:
1. Update Filter Products requests to use new composite filter format
2. Specify desired columns via comma-separated text field (previously columns were determined by static entity mapping)
3. Update any integration code that depends on previous static 4-filter structure

**Benefits**:
- ✨ Filter by ANY Salsify property (not limited to 4 predefined filters)
- ✨ Select which columns to return in response
- ✨ More flexible and powerful filtering capabilities
- ✨ Better code quality with 95% test coverage

**Backward Compatibility**: NO — this is a breaking change. Please review the [Filter Products documentation](/pages/FilterProducts.md) for the new API structure.

For compatibility with future versions, always keep your Salsify API Key current and ensure your Krista platform is up to date with the latest extension packages.

---

## Support

If you encounter issues:

1. Check the Troubleshooting section in the relevant documentation page:
   - [Troubleshooting Extension Configuration](/pages/ExtensionConfiguration.md#troubleshooting)
   - [Troubleshooting Authentication](/pages/Authentication.md#troubleshooting-authentication)
   - [Troubleshooting Filter Products](/pages/FilterProducts.md#troubleshooting)
2. Verify your Org ID and API Key are correct (see [Extension Configuration](/pages/ExtensionConfiguration.md))
3. Test the connection using the **Test Connection** button
4. Review Krista application logs for detailed error messages
5. Contact the Krista Extension Team for assistance

---

## Feedback & Requests

Have feedback on this extension or want to request a feature? Please reach out to the Krista Extension Team with:

- Your use case
- Desired functionality
- Any limitations you've encountered

Your feedback helps us improve future releases.
