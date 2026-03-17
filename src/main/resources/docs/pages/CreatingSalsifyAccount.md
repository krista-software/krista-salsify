# Creating a Salsify API Key

## Overview

This guide walks you through obtaining the two values required to configure the Salsify Extension:

1. **Salsify Org ID** — your organisation's identifier in Salsify
2. **Salsify API Key** — your personal or service-account API Key for authenticating requests

---

## Prerequisites

- An active Salsify account with at minimum **Viewer** access to product data
- Access to your Salsify organisation's dashboard at [https://app.salsify.com](https://app.salsify.com)

> **💡 Tip**: For production integrations, ask your Salsify administrator to create a dedicated **service account** rather than using a personal user's API Key. This prevents disruption if the personal account is removed or changed.

---

## Step 1 — Find Your Salsify Org ID

Your **Org ID** is embedded in the URL when you are logged into Salsify:

```
https://app.salsify.com/app/orgs/<YOUR_ORG_ID>/products
```

1. Log in to [https://app.salsify.com](https://app.salsify.com).
2. Look at the URL in your browser's address bar.
3. Copy the segment after `/orgs/` and before `/products` (or any other path segment).

**Example**:

```
URL:    https://app.salsify.com/app/orgs/salsify-12345/products
Org ID: salsify-12345
```

> **📝 Note**: Org IDs are case-sensitive. Copy the value exactly as it appears in the URL.

---

## Step 2 — Generate an API Key

1. While logged in to Salsify, click your **user avatar** or **profile name** in the top-right corner.
2. Select **My Account** or **Profile Settings** from the dropdown.
3. Navigate to the **API Keys** or **Access Tokens** section.
4. Click **Generate New API Key** (or **Create Token**).
5. Give the key a descriptive name, for example: `Krista Integration`.
6. Click **Save** or **Generate**.
7. **Copy the API Key immediately** — Salsify may only display it once.

> **⚠️ Warning**: Store the API Key in a secure location (such as a password manager) before navigating away. If you lose it, you must generate a new one.

---

## Step 3 — Verify Permissions

The API Key must have access to read products for your organisation. To verify:

1. In Salsify, go to **Settings → Users & Permissions** (exact menu path may vary by Salsify plan).
2. Confirm the account associated with the API Key has at least **Viewer** access to the product catalog.
3. If using a service account, ensure the account is assigned to your organisation.

---

## Step 4 — Enter Credentials in Krista

1. Open the **Salsify Extension** Setup tab in Krista.
2. Paste your **Salsify Org ID** into the `Salsify Org ID` field.
3. Paste your **Salsify API Key** into the `Salsify API Key` field (it will be masked).
4. Click **Save**.
5. Krista will validate the credentials automatically. A success message confirms the connection.

See [Extension Configuration](/pages/ExtensionConfiguration.md) for full details.

---

## Verification

After saving the configuration, click the **Test Connection** button in the extension panel. A successful test confirms:

- ✅ Org ID is valid and accessible
- ✅ API Key is accepted by Salsify
- ✅ The API Key has read access to the products endpoint

---

## Rotating or Replacing an API Key

If you need to rotate the API Key (for example, on a security schedule):

1. Generate a new API Key in Salsify following Step 2 above.
2. Return to the Krista extension Setup tab.
3. Enter the new API Key and save.
4. After confirming the new key works (Test Connection), revoke the old key in Salsify.

> **💡 Tip**: Update the Krista extension with the new key **before** revoking the old one to avoid any downtime in your workflows.

---

## Next Steps

- [Extension Configuration](/pages/ExtensionConfiguration.md) — Complete the setup
- [Authentication](/pages/Authentication.md) — Understand how credentials are used
- [Filter Products](/pages/FilterProducts.md) — Start querying product data
