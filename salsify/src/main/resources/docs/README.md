# Salsify Extension

## Overview

The Salsify Extension integrates Krista with Salsify's **Product Experience Management (PXM)** platform. It allows Krista workflows to query and filter live product data directly from Salsify using your organisation's API credentials.

Authentication is handled via a Salsify API Key stored securely in the Krista KeyValueStore. No OAuth flows are required.

---

## Key Features

- **Live product queries** — Fetch products from Salsify in real time with flexible filtering
- **Composite AND filtering** — Combine multiple property filters in a single request
- **Status filtering** — Quickly filter active or discontinued products via a dedicated toggle
- **Flexible pagination** — Choose between automatic pagination (fetch all matches) or offset-based pagination (fetch specific page)
- **Resilient HTTP client** — Exponential backoff on rate limiting (429) and automatic retry on server errors (500/503)
- **Secure credential storage** — API Key is masked in the UI and encrypted at rest in the Krista platform

---

## Quick Start Guide

1. **Configure the extension** — Enter your Salsify Org ID and API Key in the Setup tab. See [Extension Configuration](pages/ExtensionConfiguration.md).
2. **Obtain your API Key** — If you do not have a Salsify API Key, see [Creating a Salsify API Key](pages/CreatingSalsifyAccount.md).
3. **Test the connection** — Use the built-in Test Connection button to verify your credentials.
4. **Build your workflow** — Use the [Filter Products](pages/FilterProducts.md) catalog request to query product data from Salsify.

---

## Documentation Structure

### Getting Started
| Page | Description |
|------|-------------|
| [Extension Configuration](pages/ExtensionConfiguration.md) | Configure Org ID and API Key |
| [Authentication](pages/Authentication.md) | How API Key authentication works |
| [Creating a Salsify API Key](pages/CreatingSalsifyAccount.md) | Step-by-step guide to generating an API Key in Salsify |

### Product Operations
| Page | Description |
|------|-------------|
| [Filter Products](pages/FilterProducts.md) | Retrieve and filter products from Salsify |

### Reference
| Page | Description |
|------|-------------|
| [Release Notes](pages/release-notes.md) | Version history, features, and updates |

---

## Support & Resources

- **Extension Version**: 1.0.8
- **Ecosystem**: Product Information Management
- **Salsify API**: [https://developers.salsify.com](https://developers.salsify.com)
- **Base URL**: `https://app.salsify.com`
- **API Version**: v1
- **[Release Notes](pages/release-notes.md)** — Version history and updates

> **📝 Note**: For large catalogs (> 10,000 products), results are capped at 10,000 per call. Consider adding filters to narrow results.
