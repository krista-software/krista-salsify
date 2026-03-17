# Authentication

## Overview

The Salsify Extension uses **API Key authentication** (Bearer token). There is no OAuth flow. You provide a single Salsify API Key, and the extension attaches it as a `Bearer` token on every request to the Salsify API.

---

## How It Works

Every HTTP request to the Salsify API includes the following header:

```
Authorization: Bearer <YOUR_SALSIFY_API_KEY>
```

The API Key is your credential — it identifies both your identity and the organisation you are operating on (combined with your Org ID in the request URL).

---

## Credential Flow

```
1. You enter Org ID + API Key in the Krista extension Setup tab
          ↓
2. Krista validates credentials via a live API probe (1-product GET)
          ↓
3. On success: credentials saved to Krista KeyValueStore (encrypted)
          ↓
4. On each catalog request: credentials are loaded from KeyValueStore
          ↓
5. API Key is attached as Bearer token on every Salsify HTTP request
          ↓
6. Response is returned to the Krista workflow
```

---

## Token Storage

| Property | Detail |
|----------|--------|
| Storage location | Krista KeyValueStore |
| Encryption | Encrypted at rest by the Krista platform |
| Key format | `{invokerId}_salsify_credentials` |
| Scope | Per Krista invoker (each integration has independent credentials) |
| Expiry | No automatic expiry — API Keys are valid until revoked in Salsify |

---

## API Key Permissions

The Salsify API Key you use must have **read access** to the products endpoint for your organisation:

```
GET https://app.salsify.com/api/v1/orgs/{orgId}/products
```

If the API Key lacks the required permission, you will receive a **403 Forbidden** error when saving or testing the connection.

> **💡 Tip**: If you are using a service account API Key in Salsify, ensure the account has at minimum **Viewer** access to the product data you need to query.

---

## Error Handling

The extension maps HTTP error codes from Salsify to user-friendly messages:

| HTTP Code | Meaning | Error Message |
|-----------|---------|---------------|
| 401 | Invalid or expired API Key | "Invalid API token (401). Please verify your Salsify API Key." |
| 403 | API Key lacks permission for this org | "Access denied (403). The API Key does not have permission for this organisation." |
| 404 | Org ID not found | "Resource not found (404). Please verify the Org ID and product identifier." |
| 429 | Rate limit exceeded | "Salsify rate limit exceeded. All retries exhausted. Please try again later." |
| 500 / 503 | Salsify server error | "Salsify server error. Retry attempted once. Please try again later." |

---

## Retry Behaviour

The extension's HTTP client includes built-in retry logic to handle transient failures:

### Rate Limit Handling (HTTP 429)

When Salsify returns a 429 response, the extension automatically retries with **exponential backoff**:

| Attempt | Delay Before Retry |
|---------|--------------------|
| 1st retry | 2 seconds |
| 2nd retry | 4 seconds |
| 3rd retry | 8 seconds |
| After 3 retries | Error returned to workflow |

### Server Error Handling (HTTP 500 / 503)

The extension retries **once** on server errors. If the second attempt also fails, the error is returned to the workflow.

### Non-Retryable Errors

HTTP **401**, **403**, and **404** responses are never retried. They indicate a credential or configuration problem that requires human intervention.

---

## Re-Authentication

Salsify API Keys do not expire automatically. You may need to update your credentials if:

- You rotate the API Key in Salsify (for security best practice)
- The API Key is revoked by a Salsify administrator
- Your Salsify account access level changes

To update credentials, go to the extension **Setup tab** in Krista and enter the new API Key. See [Extension Configuration](/pages/ExtensionConfiguration.md).

---

## Security Best Practices

1. **Never share your API Key** — treat it like a password.
2. **Use a dedicated service account** in Salsify for integrations rather than a personal user's API Key.
3. **Rotate the API Key periodically** — update it in the Krista extension Setup tab after rotating.
4. **Revoke immediately** if you suspect the key has been compromised.
5. **Do not hardcode** the API Key in workflow definitions or exported configurations.

---

## Troubleshooting Authentication

| Symptom | Likely Cause | Resolution |
|---------|--------------|------------|
| 401 on Test Connection | API Key is wrong or was deleted | Log into Salsify and generate a new API Key. See [Creating a Salsify API Key](/pages/CreatingSalsifyAccount.md) |
| 403 on Test Connection | API Key belongs to a different org | Confirm that the Org ID matches the API Key's organisation |
| Credentials lost after platform restart | KVS data cleared | Re-enter credentials in the Setup tab |
| 429 errors in production | High query volume | Add filters to reduce result set size; avoid calling Filter Products in tight loops |

---

## See Also

- [Extension Configuration](/pages/ExtensionConfiguration.md)
- [Creating a Salsify API Key](/pages/CreatingSalsifyAccount.md)
- [Filter Products](/pages/FilterProducts.md)
