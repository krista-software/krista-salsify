# Salsify Extension

A [Krista](https://krista.ai) platform extension that integrates with [Salsify PXM](https://www.salsify.com) to enable live product data queries within Krista workflows.

## Version

`1.0.8`

## Overview

This extension connects Krista to Salsify's Product Experience Management (PXM) system. It exposes a single catalog request — **Filter Products** — that allows Krista workflows to query, filter, and retrieve structured product data from Salsify using API Key authentication.

## Features

- Filter products by one or more Salsify property values (AND logic)
- Filter by product status: active or discontinued
- Automatic full pagination (up to 10,000 products)
- Exponential backoff on rate limiting (429); retry on server errors (500/503)
- Credentials stored encrypted in Krista KeyValueStore

## Module Structure

```
salsify/
├── src/
│   └── main/
│       ├── java/com/krista/krista/extension/salsify/
│       │   ├── SalsifyExtension.java          # Extension entry point, lifecycle hooks
│       │   ├── config/
│       │   │   ├── AttributeKeys.java         # Attribute key constants
│       │   │   └── SalsifyAttributes.java     # Runtime attribute wrapper
│       │   ├── connection/
│       │   │   └── SalsifyHttpClient.java     # OkHttp client, retry logic, pagination
│       │   ├── controller/
│       │   │   └── SalsifyProductArea.java    # filterProducts catalog request
│       │   ├── entity/
│       │   │   └── ProductList.java           # Product entity (21 fields)
│       │   ├── mapper/
│       │   │   └── ProductListMapper.java     # Salsify JSON → ProductList
│       │   ├── service/
│       │   │   ├── SalsifyService.java        # Service interface
│       │   │   └── impl/
│       │   │       ├── ProductListData.java   # Pagination result holder
│       │   │       └── SalsifyServiceImpl.java # Pagination orchestration
│       │   ├── store/
│       │   │   └── CredentialStore.java       # KVS-backed credential storage
│       │   └── utils/
│       │       ├── Constants.java             # All constants
│       │       └── FieldMapper.java           # Y/N → Yes/No normalisation
│       └── resources/
│           └── docs/                          # Docsify documentation
│               ├── README.md
│               ├── _sidebar.md
│               └── pages/
│                   ├── ExtensionConfiguration.md
│                   ├── Authentication.md
│                   ├── CreatingSalsifyAccount.md
│                   └── FilterProducts.md
└── build.gradle
```

## Configuration

| Parameter | Required | Description |
|-----------|----------|-------------|
| Salsify Org ID | Yes | Your Salsify organisation identifier |
| Salsify API Key | Yes | Your Salsify API Key (stored encrypted) |

## Build

```bash
./gradlew build
```

### SonarQube Scan

```bash
./gradlew sonar
```

## Documentation

Full user documentation is available in `src/main/resources/docs/` and served as a Documentation tab within the Krista extension UI.

- [Extension Configuration](src/main/resources/docs/pages/ExtensionConfiguration.md)
- [Authentication](src/main/resources/docs/pages/Authentication.md)
- [Creating a Salsify API Key](src/main/resources/docs/pages/CreatingSalsifyAccount.md)
- [Filter Products](src/main/resources/docs/pages/FilterProducts.md)

## Key Technical Notes

- The `filterProducts` catalog request returns `List<ProductList>` directly — **do not wrap in `ExtensionResponse`**. Doing so causes a `ClassCastException` in the Krista framework serialisation layer.
- Salsify API base URL defaults to `https://app.salsify.com` and can be overridden via the system property `-Dsalsify.base.url=...` for testing.
- Large catalogs (> 10,000 products) are capped at the `LARGE_CATALOG_THRESHOLD`. Apply filters to work within this limit.
