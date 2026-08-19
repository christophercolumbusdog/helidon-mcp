# Helidon Secured MCP Server

This guide demonstrates how to secure a Helidon server using the Model Context Protocol (MCP) with authorization provided 
by Keycloak. Security support was introduced in the MCP specification as of the 
[2025-03-26 release](https://modelcontextprotocol.io/specification/2025-03-26/basic/authorization).

In addition to endpoint-level OIDC authentication, this example demonstrates **declarative per-tool authorization**:

* `role-protected-tool` requires the `mcp-admin` role.
* `policy-protected-tool` requires an ABAC policy statement (`${subject.principal.name == 'mcp-user'}`).

Both tools rely on the optional `helidon4-extensions-mcp-security` module, an opt-in dependency (see `pom.xml`) that
evaluates `McpToolAuthorization` metadata with Helidon Security's attribute based access control (ABAC). Declaring a
role or a policy statement on a tool has no effect unless this dependency is present *and* an `abac` authorization
provider is configured (see `application.yaml`): a protected tool fails closed (denies every call) whenever the
authorization metadata cannot be evaluated, for example because the dependency is missing, no `SecurityContext` is
available, or the caller is not authenticated. `secured-tool` has no authorization metadata and keeps working exactly
as before, regardless of this module.

## Keycloak Configuration

This example uses Keycloak as a third-party authentication provider. To get started, launch a local Keycloak instance via Docker:

```bash
docker run -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:24.0.5 start-dev
```

This creates an admin user with the username/password set to `admin`. Access the Keycloak admin console at:
[http://127.0.0.1:8080/admin/master/console/](http://127.0.0.1:8080/admin/master/console/)

## 1. Create a New Realm

A *realm* isolates configuration for a set of applications, users, roles, and sessions.

> ⚠️ Avoid using the built-in `Master` realm for application configuration, as it's intended for creating and managing the realms in your system.

### Option A: Import Preconfigured Realm

Use the provided `mcp-realm.json` file located in the `resources/` directory to import a preconfigured realm:

* In the admin console, navigate to **Realm Settings > Import**
* Select the `mcp-realm.json` file
* Click **Create**

> Note: This file does **not** include a user. You’ll need to manually create one (see [Create a New User](#4-create-a-new-user)).

### Option B: Create Realm Manually

1. In the admin console, click the dropdown in the top-left corner and select **Create realm**.
2. Enter a name (e.g., `mcp-realm`) and click **Create**.

Once created, you’ll see the new realm name in the top-left dropdown. You can switch between realms by selecting from this dropdown.

---

## 2. Create a New Client

1. Ensure the current realm is set to `mcp-realm`.
2. Navigate to **Clients** in the left menu.
3. Click **Create client** and fill in:

  * **Client ID**: `mcp-client`
  * **Client Protocol**: `openid-connect`
  * Click **Next**
4. In the **Capability Config** step:

  * Disable **Client authentication**
  * Enable **Standard flow**
  * Click **Next**
5. Set the following:

  * **Valid Redirect URIs**: `http://localhost:6274/oauth/callback/debug`
  * **Web Origins**: `http://localhost:6274`
6. Click **Save**

---

## 3. Create a Client Scope

1. Go to **Client Scopes** in the left menu.
2. Click **Create client scope**:

  * **Name**: `mcp-scope`
  * **Type**: Optional
  * Click **Save**
3. In the `Mappers` tab:

  * Click **Create new mapper**
  * **Mapper Type**: `Audience`
  * **Name**: `mcp-audience`
  * **Included Custom Audience**: `mcp-scope`
  * Click **Save**
4. Assign the scope to the client:

  * Navigate to **Clients > mcp-client**
  * Go to the **Client Scopes** tab
  * Click **Add client scope**
  * Select `mcp-scope` and assign it as **Optional**

---

## 4. Create a New User

1. Go to **Users** in the left menu.
2. Click **Create new user** and enter:

  * **Username**: `mcp-user`
  * Click **Create**
3. Assign a password:

  * Go to the **Credentials** tab
  * Click **Set Password**
  * Enter and confirm your chosen password
  * Set **Temporary** to **Off**
  * Click **Set Password** to confirm

To test the user login:

* Visit the account console: [http://localhost:8080/realms/mcp-realm/account](http://localhost:8080/realms/mcp-realm/account)
* Log in using the `mcp-user` credentials

## 5. Build and Run the Application

### Build

```bash
mvn clean package
```

### Run

```bash
java -jar target/helidon-mcp-secured-server.java
```

---

## 6. Run MCP Inspector

The [MCP Inspector](https://modelcontextprotocol.io/docs/tools/inspector) is an interactive tool for testing MCP servers.

### Launch via `npx`

```bash
npx @modelcontextprotocol/inspector
```

The inspector will open in a browser window.

### Configure the Inspector

1. **Transport**: Select `Streamable HTTP`
2. **Server URL**: `http://localhost:8081/secured`
3. Go to the **Authentication** tab:

  * **Client ID**: `mcp-client`
  * **Scope**: `openid mcp-scope`
  * Click **Open Auth Settings**
  * Select **Quick OAuth flow**
  * Wait for all steps to complete
  * Copy the `access_token` value from the `Authentication Complete` step
4. Under the **Authorization** section (left panel), set:

 ```http
 Bearer <access_token>
 ```
5. Click **Connect**

---

## 7. Test the Secured Application

1. Go to the **Tools** tab
2. Click **List Tools**
3. Run `secured-tool`

If configured correctly, the username (e.g., `mcp-user`) will be returned.

## 8. Test Declarative Per-Tool Authorization

### `policy-protected-tool`

Run `policy-protected-tool` while logged in as `mcp-user` (created in step 4): the policy statement matches the
authenticated principal name, so the call succeeds.

### `role-protected-tool`

Run `role-protected-tool` while logged in as `mcp-user`: the call is denied with the generic JSON-RPC error
`Not authorized to call tool` (code `-32001`), because `mcp-user` does not have the `mcp-admin` role yet.

To grant access:

1. In the Keycloak admin console, go to **Users > mcp-user > Role mapping**.
2. Click **Assign role** and select the realm role `mcp-admin` (included in `mcp-realm.json`; create it manually
   under **Realm roles** if you built the realm with Option B instead of importing the file).
3. Get a new `access_token` from the inspector (tokens issued before the role was assigned do not carry it).
4. Run `role-protected-tool` again: the call now succeeds.

Either denial disappearing when you remove the `helidon4-extensions-mcp-security` dependency, or the ABAC provider,
is expected: without them, no `McpToolAuthorizer` can resolve, and both protected tools deny every call.
