# Release Notes

## Version 1.0.1 - Current Release

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
