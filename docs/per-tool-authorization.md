# Declarative Per-Tool Authorization

## Summary

Add MCP-native authorization metadata to individual tools. Generated and
programmatic tools use the same metadata. The MCP server enforces the metadata
before it invokes the tool. A separate, optional Helidon Security integration
module evaluates roles and policy statements with the request's
`SecurityContext`.

This design does not add CDI interception to this repository. A Helidon MP
application can use the new MCP-native annotations, but it cannot reuse
`PolicyValidator.PolicyStatement` directly on an `@Mcp.Tool` method. The
application can reuse the policy expression text and its existing Helidon ABAC
policy executor configuration.

## Work item

- `ticket_id`: `d35ec6b6-64eb-4f9e-81ee-6314fbcf723b`
- `ticket_source`: `adhoc`
- `ticket_url`: https://github.com/helidon-io/helidon-mcp/issues/201
- `requester`: `christian.cygnus@imc.com`

## Product behavior

1. A declarative tool can require one or more user roles.
   - The user must have at least one listed role.
   - Role names are case-sensitive.
   - A blank role is invalid at compile time.
2. A declarative tool can require one Helidon ABAC policy statement.
   - The policy statement uses the application's configured Helidon policy
     executor.
   - A blank policy statement is invalid at compile time.
3. A tool can declare both roles and a policy statement.
   - The role check and the policy statement must both permit the call.
4. A programmatic `McpTool` can declare the same roles and policy statement.
   - `McpServerConfig.builder().addTool(...)` does not bypass authorization.
5. The server evaluates authorization after it resolves the requested tool and
   before it invokes `McpTool.tool()`.
6. A denied call returns this JSON-RPC error:

   ```json
   {
     "code": -32001,
     "message": "Not authorized to call tool"
   }
   ```

   - The response is a JSON-RPC error, not a successful tool result with
     `isError: true`.
   - The message does not include role names, policy text, subject details, or
     the reason for denial.
   - The tool function does not run.
7. `tools/list` returns all registered tools in v1.
   - It does not filter the list by caller.
   - Authorization metadata is server-only. It is not serialized into the MCP
     tool schema or MCP tool annotations.
   - Any caller that can reach `tools/list` can discover protected tool names,
     descriptions, and schemas. This includes unauthenticated callers when the
     application does not protect the MCP endpoint.
8. A tool without authorization metadata behaves exactly as it does today.
   - The server does not require Helidon Security for that tool.
   - Existing endpoint security continues to apply.
9. A tool with authorization metadata fails closed when authorization cannot be
   evaluated.
   - The Helidon Security integration is absent: deny.
   - A Helidon Security integration is present, but the request context has no
     `SecurityContext`: deny.
   - A `SecurityContext` exists, but it has no authenticated user: deny.
   - Helidon Security denies or abstains: deny.
   - The configured authorization provider does not evaluate the MCP
     authorization attributes: deny.
   - Policy evaluation throws, references an unavailable property, or cannot
     find a policy executor: deny.
   - An authorization integration throws any other exception: deny.

## Technical design

### Current execution path

`server/src/main/java/io/helidon/extensions/mcp/server/Mcp.java:138` defines
`@Mcp.Tool`.
`codegen/src/main/java/io/helidon/extensions/mcp/codegen/McpToolCodegen.java:61`
finds each tool method and generates a private `McpTool` implementation. The
same file generates a function that calls the server delegate directly at line
125.
`codegen/src/main/java/io/helidon/extensions/mcp/codegen/McpCodegen.java:169`
registers each generated tool with
`McpServerConfig.Builder.addTool(...)`.

`McpServerFeature.toolsCallRpc` resolves the tool at
`server/src/main/java/io/helidon/extensions/mcp/server/McpServerFeature.java:391`.
It constructs an `McpRequest` with the HTTP request context at
`server/src/main/java/io/helidon/extensions/mcp/server/McpServerFeature.java:416-424`.
It then invokes the tool function. This is the enforcement seam.

The generated server obtains its delegate from the Helidon Service Registry or
constructs it directly in
`codegen/src/main/java/io/helidon/extensions/mcp/codegen/McpCodegen.java:108-121`.
It does not obtain a CDI proxy. CDI and Jersey interceptors therefore do not
run around generated MCP tool calls.

### Annotation surface

Add these nested annotations to
`server/src/main/java/io/helidon/extensions/mcp/server/Mcp.java`:

```java
@Target(METHOD)
@Retention(RUNTIME)
public @interface RolesAllowed {
    String[] value();
}

@Target(METHOD)
@Retention(RUNTIME)
public @interface PolicyStatement {
    String value();
}
```

Rules:

- Both annotations target methods only.
- Both annotations have runtime retention to match `@Mcp.Tool`.
- `@Mcp.RolesAllowed` requires at least one non-blank value.
- Roles in one annotation use OR semantics.
- `@Mcp.PolicyStatement` requires one non-blank value.
- `@Mcp.PolicyStatement` is not repeatable in v1.
- When both annotations are present, their checks use AND semantics.
- There is no class-level inheritance in v1.
- Code generation reports invalid declarations as `CodegenException`.

Example:

```java
@Mcp.Tool("Read the monitor status")
@Mcp.RolesAllowed({"x-monitor", "x-admin"})
@Mcp.PolicyStatement(
        "${subject.principal.name == 'monitor-user'"
                + " && object.name == 'monitorStatus'}")
String monitorStatus() {
    return "ready";
}
```

Do not consume `jakarta.annotation.security.RolesAllowed` or
`PolicyValidator.PolicyStatement`. Those annotations depend on integration
layers that do not wrap the generated delegate invocation.

### Runtime metadata and programmatic API

Add an `McpToolAuthorization` generated prototype from a new
`McpToolAuthorizationBlueprint`:

```java
@Prototype.Blueprint
interface McpToolAuthorizationBlueprint {
    @Option.Singular
    List<String> roles();

    Optional<String> policyStatement();
}
```

Add this method to `McpToolBlueprint`:

```java
default Optional<McpToolAuthorization> authorization() {
    return Optional.empty();
}
```

The generated builder surface is:

```java
McpTool tool = McpTool.builder()
        .name("monitorStatus")
        .description("Read the monitor status")
        .schema("")
        .authorization(McpToolAuthorization.builder()
                .addRole("x-monitor")
                .addRole("x-admin")
                .policyStatement(
                        "${subject.principal.name == 'monitor-user'"
                                + " && object.name == 'monitorStatus'}")
                .build())
        .tool(request -> McpToolResult.builder()
                .addContent(McpToolContents.textContent("ready"))
                .build())
        .build();

McpServerConfig server = McpServerConfig.builder()
        .addTool(tool)
        .build();
```

An imperative `McpTool` implementation can override `authorization()`:

```java
@Override
public Optional<McpToolAuthorization> authorization() {
    return Optional.of(McpToolAuthorization.builder()
            .addRole("x-monitor")
            .addRole("x-admin")
            .policyStatement(
                    "${subject.principal.name == 'monitor-user'"
                            + " && object.name == 'monitorStatus'}")
            .build());
}
```

`McpToolAuthorization` validates the same invariants as code generation. Add
`McpDecorators.ToolAuthorizationDecorator` to reject metadata with neither a
role nor a policy statement, blank roles, and a present but blank policy
statement. Policy-only metadata is valid.

Add this core SPI:

```java
@FunctionalInterface
public interface McpToolAuthorizer {
    boolean authorize(McpTool tool, McpRequest request);
}
```

Add `Optional<McpToolAuthorizer> toolAuthorizer()` to
`McpServerConfigBlueprint`. Its builder setter is the explicit configuration
path for tests and custom authorization integrations.

Provider resolution follows these rules:

1. Use `McpServerConfig.toolAuthorizer()` when it is present.
2. Otherwise, load `McpToolAuthorizer` with `ServiceLoader`.
3. Use the provider when exactly one provider exists.
4. Keep no provider when no provider exists.
5. If a protected tool exists and multiple providers exist, fail server
   construction. The application must select one with
   `McpServerConfig.Builder.toolAuthorizer(...)`.
6. Do not resolve or require a provider when the server has no protected tools.

Add `uses io.helidon.extensions.mcp.server.McpToolAuthorizer` to the server
module descriptor.

### Code generation

Update `codegen/src/main/java/io/helidon/extensions/mcp/codegen/McpTypes.java`
with type names for:

- `Mcp.RolesAllowed`
- `Mcp.PolicyStatement`
- `McpToolAuthorization`
- `Optional<McpToolAuthorization>`

Update `McpToolCodegen.generate` in
`codegen/src/main/java/io/helidon/extensions/mcp/codegen/McpToolCodegen.java`:

1. Read both authorization annotations from each `@Mcp.Tool` method.
2. Validate the role list and policy statement.
3. Generate `authorization()` on the private `*__Tool` implementation.
4. Return `Optional.empty()` when the source method has no authorization
   annotation.
5. Otherwise, build one `McpToolAuthorization` with the declared metadata.

No registration branch is required. `McpCodegen.addRoutingMethod` continues to
register `new <InnerTool>()` at `McpCodegen.java:169-176`. The generated
`McpTool` now carries its authorization metadata through that existing path.

### Enforcement flow

Refactor `McpServerFeature.toolsCallRpc` at
`server/src/main/java/io/helidon/extensions/mcp/server/McpServerFeature.java:391-429`
to use this order:

1. Resolve the session.
2. Resolve the tool name.
3. Return the existing `INVALID_PARAMS` error when the tool does not exist.
4. Create `McpFeatures`.
5. Build one `McpRequest` with arguments, `_meta`, session context, and
   `req.context()`.
6. If `tool.authorization()` is empty, continue to invocation.
7. If authorization metadata exists and no authorizer exists, return `-32001`.
8. Call `McpToolAuthorizer.authorize(tool, mcpRequest)`.
9. If the result is `false`, return `-32001`.
10. If the authorizer throws, log the failure for operators and return
    `-32001`. Do not place the exception or policy text in the JSON-RPC
    response.
11. Call `session.beforeFeatureRequest(...)`.
12. Invoke `tool.tool().apply(mcpRequest)`.
13. Call `session.afterFeatureRequest(...)`.
14. Serialize and send the successful tool result.

Authorization denial does not call `beforeFeatureRequest`,
`afterFeatureRequest`, or the tool function.

Define `AUTHORIZATION_DENIED_CODE = -32001` in `McpServerFeature`. The MCP
2024-11-05, 2025-03-26, and 2025-06-18 tool specifications distinguish
protocol errors from tool execution errors, but none defines an authorization
error code. JSON-RPC 2.0 reserves `-32000` through `-32099` for
implementation-defined server errors. This repository already uses `-32002`
for resource-not-found. `-32001` is therefore the adjacent Helidon MCP
server-error code.

### Optional Helidon Security integration

Add a module with these identifiers:

- Maven artifact:
  `io.helidon.extensions.mcp:helidon4-extensions-mcp-security`
- JPMS module: `io.helidon.extensions.mcp.security`

Add the module to the reactor in `pom.xml` and to dependency management in
`bom/pom.xml`.

Apply `helidon-services-plugin` in the security module. The plugin must generate
`META-INF/services` entries for both `provides` declarations so discovery works
when the modular JAR is placed on the classpath. The JPMS `provides` directives
cover module-path execution.

The security module has normal dependencies on:

- `helidon4-extensions-mcp-server`
- `io.helidon.security:helidon-security`
- `io.helidon.security.providers:helidon-security-providers-abac`
- `io.helidon.security.abac:helidon-security-abac-role`
- `io.helidon.security.abac:helidon-security-abac-policy`

The server module does not depend on any of these security artifacts.
Applications opt in by adding:

```xml
<dependency>
    <groupId>io.helidon.extensions.mcp</groupId>
    <artifactId>helidon4-extensions-mcp-security</artifactId>
</dependency>
```

The security module descriptor contains:

```java
module io.helidon.extensions.mcp.security {
    requires io.helidon.extensions.mcp.server;
    requires io.helidon.security;
    requires io.helidon.security.providers.abac;
    requires io.helidon.security.abac.role;
    requires io.helidon.security.abac.policy;

    provides io.helidon.extensions.mcp.server.McpToolAuthorizer
            with io.helidon.extensions.mcp.security.HelidonSecurityMcpToolAuthorizer;
    provides io.helidon.security.providers.abac.spi.AbacValidatorService
            with io.helidon.extensions.mcp.security.McpAuthorizationMarkerValidatorService;
}
```

The integration performs these steps:

1. Read `SecurityContext` from `McpRequest.requestContext()`.
2. Deny when the context is absent.
3. Check `SecurityContext.isAuthenticated()`.
4. Deny when no user is authenticated.
5. Save `SecurityContext.endpointConfig()`.
6. Derive an endpoint configuration from the saved configuration.
7. For roles, add a `RoleValidator.RoleConfig` custom object.
8. For a policy statement, add a
   `PolicyValidator.PolicyConfig` custom object.
9. Add a private marker config custom object.
10. Set the derived endpoint configuration on the context.
11. Call `SecurityContext.authorize(tool)`.
12. Permit only when the response is permitted and the marker validator ran.
13. Restore the original endpoint configuration in a `finally` block.

The marker is required for fail-closed behavior. In Helidon Security 4.3.1,
`AuthorizationClientImpl.submit()` returns `AuthorizationResponse.permit()` if
no authorization provider resolves. A permitted response alone does not prove
that ABAC evaluated the MCP metadata. The private marker validator proves that
the configured ABAC provider processed the derived endpoint configuration.

The application must configure an ABAC authorization provider. OIDC
authentication alone is insufficient. Existing policy executor dependencies
and `policy-validator` configuration remain in the application. The MCP
security module does not select or replace the application's policy executor.

`requires static io.helidon.security` in the server module is rejected. Core
server bytecode would still contain security type references, and protected
calls could fail during class resolution when the module is absent. Reflection
is rejected because JPMS access and API signatures would be fragile. A separate
module plus a core `ServiceLoader` SPI keeps all security linkage outside the
server module and preserves a stable extension point.

### Policy expression context

Policy-expression support is included in v1.

The Helidon ABAC context has these meanings:

- `subject`: the authenticated user from the request `SecurityContext`.
- `service`: the authenticated service, when present.
- `env`: the existing HTTP security environment.
- `object`: the selected runtime `McpTool` passed to
  `SecurityContext.authorize(tool)`.

`object` is tool metadata, not a JAX-RS domain entity and not the decoded tool
arguments. The supported metadata properties are the public `McpTool`
properties, including `name`, `title`, `description`, `schema`, `annotations`,
`outputSchema`, and `authorization`.

This policy can run in v1:

```java
@Mcp.PolicyStatement(
        "${subject.principal.name == 'monitor-user'"
                + " && object.name == 'monitorStatus'}")
```

This policy cannot run in v1 because `account` is neither tool metadata nor a
bound domain object:

```java
@Mcp.PolicyStatement(
        "${object.account.owner == subject.principal.name}")
```

An unavailable property, unsupported expression, missing executor, or any
other evaluation error denies the call. The integration must not skip the
policy or treat the error as permit.

For a policy that needs a decoded domain object, perform the supported
programmatic check inside the tool until typed authorization argument binding
is designed:

```java
SecurityContext securityContext = request.requestContext()
        .get(SecurityContext.class)
        .orElseThrow();
if (!securityContext.authorize(account).isPermitted()) {
    throw new SecurityException("Not authorized");
}
```

Do not add `@Mcp.PolicyStatement` with an `object.account` expression to that
tool. The declarative check runs before the method can decode and bind the
domain object.

### `tools/list`

`McpServerFeature.toolsListRpc` at `McpServerFeature.java:378-388` remains
unchanged. The runtime `McpTool.authorization()` metadata is available for a
future filtering design without changing the annotations, generated tools, or
programmatic builder API.

Filtering is deferred because it requires a separate contract for:

- per-session list contents;
- pagination over caller-specific results;
- `listChanged` notifications;
- client caches after a subject or authorization decision changes; and
- behavior when authorization evaluation fails during listing.

### Helidon MP behavior

This repository contains Helidon SE WebServer integration and Helidon Service
Registry annotation code generation. It does not contain a CDI extension or a
Weld integration. `@Features.Flavor({SE, MP})` in
`server/src/main/java/module-info.java:26` does not create CDI interception.

A Helidon MP user gets:

- declarative per-tool roles through `@Mcp.RolesAllowed`;
- declarative per-tool policy expressions through `@Mcp.PolicyStatement`;
- reuse of existing policy expression text;
- reuse of the configured ABAC provider and policy executor;
- the authenticated `SecurityContext` forwarded by WebServer security; and
- the same behavior for generated and programmatic tools.

A Helidon MP user does not get:

- interception of `@PolicyValidator.PolicyStatement` on an `@Mcp.Tool` method;
- automatic reuse of JAX-RS class-level security annotations;
- a CDI-managed MCP server delegate; or
- policy access to a JAX-RS domain object or raw tool arguments.

The user must add the optional MCP security artifact, protect the MCP endpoint
with WebServer security, configure an ABAC authorization provider, and use the
new MCP-native annotations. The policy string can be the same string used by a
JAX-RS `PolicyValidator.PolicyStatement` when it depends only on the supported
context.

A future true MP/CDI module requires all of the following:

1. A separate MicroProfile/CDI artifact.
2. CDI bean discovery for `@Mcp.Server` classes.
3. Generated code that obtains a CDI contextual reference and invokes the CDI
   proxy instead of `GlobalServiceRegistry.get(...)` or `new`.
4. An interceptor or metadata bridge for MP and Helidon Security annotations.
5. Defined inheritance between application, class, and tool method security.
6. MP integration tests under Weld and the Helidon MP server.

## Decisions

### MCP-native annotations

- Selected: `@Mcp.RolesAllowed` and `@Mcp.PolicyStatement`.
  - Advantages: matches the existing `Mcp` annotation family; the APT can
    validate and preserve the metadata; no CDI dependency.
  - Disadvantages: users must add or replace annotations on MCP methods.
- Rejected: consume Jakarta `@RolesAllowed`.
  - It implies integration behavior that this code path does not provide.
- Rejected: consume Helidon `PolicyValidator.PolicyStatement` directly.
  - It is designed for Helidon Security integration layers and is not invoked
    around the generated delegate call.

### Policy support in v1

- Selected: include one policy expression per tool.
  - Advantages: addresses the reporter's ABAC requirement; reuses the existing
    policy executor and expression text.
  - Disadvantages: only the pre-invocation MCP context is available.
- Rejected: roles-only v1.
  - It would not solve the primary use case in issue 201.

### Optional security packaging

- Selected: a separate security module and a `ServiceLoader` SPI.
  - Advantages: no new required server dependency; clean JPMS linkage; custom
    authorizers remain possible.
  - Disadvantages: secured applications add one dependency.
- Rejected: optional Maven dependency plus `requires static`.
  - Direct core references still create runtime class-linkage risk.
- Rejected: reflection from the server module.
  - JPMS access and version compatibility would be brittle.

### Authorization error

- Selected: JSON-RPC server error `-32001` with
  `Not authorized to call tool`.
  - Advantages: the tool did not execute; it does not expose policy details;
    it works in every MCP revision supported by the repository.
  - Disadvantages: the code is Helidon-specific because MCP defines no
    per-tool authorization error.
- Rejected: `McpToolResult.isError`.
  - It represents a tool execution failure, but authorization stops execution.
- Rejected: HTTP 403 from `tools/call`.
  - The HTTP request reached an authenticated MCP JSON-RPC endpoint. The
    per-tool decision belongs to the JSON-RPC operation.

### Tool discovery

- Selected: do not filter `tools/list` in v1.
  - Advantages: no per-session pagination, notification, or cache contract is
    needed.
  - Disadvantages: protected tool metadata remains discoverable.
- Deferred: caller-specific filtering.
  - The runtime metadata is retained so filtering can be added without an API
    break.

### Authorization object

- Selected: bind `object` to the selected `McpTool`.
  - Advantages: deterministic before invocation; policies can restrict a tool
    by name; no unvalidated argument data enters the policy engine.
  - Disadvantages: JAX-RS policies that expect a domain entity need a
    programmatic check.
- Rejected: bind raw tool arguments.
  - Schema validation ordering is not defined at this seam. Raw argument
    binding expands the policy engine's attack surface.

## Assumptions

- Helidon 4.3.1 remains the implementation baseline.
- WebServer security places `SecurityContext` in the HTTP request context when
  the application protects the MCP endpoint.
- Applications that use policy expressions already provide a compatible policy
  executor, such as `helidon-security-abac-policy-el`.
- One policy statement per tool is sufficient for v1 and mirrors Helidon's
  existing `PolicyStatement` annotation shape.
- Application operators can inspect internal logs and Helidon Security audit
  events for denial diagnostics. Clients receive only the generic error.

## Out of scope

- CDI interception or a new Helidon MP module.
- Direct support for Jakarta or Helidon Security annotations.
- Class-level or server-level MCP authorization annotations.
- Caller-specific `tools/list` filtering.
- Declarative authorization for prompts, resources, resource templates,
  subscriptions, or completions.
- Policy access to raw arguments or decoded domain objects.
- Changes to MCP OAuth discovery, token acquisition, or endpoint
  authentication.

Prompts, resources, and completions already construct `McpRequest` with the
same HTTP request context before invoking user code. A later change can add the
same metadata-plus-authorizer seam to those component interfaces. They are not
included now because they need their own annotation and builder surfaces, error
contracts, and listing behavior.

## Compatibility and migration

This feature is additive.

- Existing tools have `Optional.empty()` authorization metadata.
- Existing generated source remains valid.
- Existing `McpServerConfig.builder().addTool(...)` calls remain valid.
- Servers without protected tools do not load or require an authorizer.
- Endpoint-level authentication and authorization configuration is unchanged.
- Applications that adopt per-tool authorization add the security integration
  dependency and authorization metadata to selected tools.

For the issue 201 use case, migrate a method by copying the existing policy
expression into `@Mcp.PolicyStatement`, not by copying the Helidon annotation.
Keep a programmatic `SecurityContext.authorize(domainObject)` check for any
policy that requires the JAX-RS domain object.

## Validation criteria

1. Core metadata and builder tests pass.
   - Add `McpToolAuthorizationTest` under `server/src/test/java`.
   - Verify unprotected defaults, role OR metadata, policy metadata, combined
     metadata, and builder validation.
   - Run:

     ```bash
     mvn -pl server -am test
     ```

2. Code generation tests pass.
   - Add `McpToolAuthorizationCodegenTest` under
     `tests/codegen/src/test/java`.
   - Compile tools with roles, a policy, both annotations, and neither.
   - Inspect or instantiate generated tools and assert exact
     `authorization()` metadata.
   - Assert compile failures for an empty role array, blank roles, and a blank
     policy.
   - Run:

     ```bash
     mvn -pl tests/codegen -am test
     ```

3. Server enforcement tests pass.
   - Add tests under `server/src/test/java` that exercise `tools/call`.
   - Verify permit invokes the function once.
   - Verify deny, no provider, and provider exception invoke it zero times.
   - Verify every denial returns code `-32001` and the exact generic message.
   - Verify `tools/list` still includes the protected tool and does not
     serialize authorization metadata.
   - Verify an unprotected tool behaves unchanged when no authorizer exists.
   - Run:

     ```bash
     mvn -pl server -am test
     ```

4. Helidon Security integration tests pass.
   - Add `HelidonSecurityMcpToolAuthorizerTest` under the new security module.
   - Use real Helidon 4.3.1 `SecurityContext` and ABAC components.
   - Verify an allowed role permits.
   - Verify a missing role denies.
   - Verify an allowed policy permits.
   - Verify a denied policy denies.
   - Verify combined role and policy use AND semantics.
   - Verify no `SecurityContext` denies.
   - Verify an unauthenticated context denies.
   - Verify no ABAC provider denies even though Helidon returns permit when no
     authorizer resolves.
   - Verify a missing policy executor denies.
   - Verify an unavailable `object` property and every evaluation exception
     deny.
   - Verify the original `EndpointConfig` is restored after permit, deny, and
     exception.
   - Run:

     ```bash
     mvn -pl security -am test
     ```

5. Current protocol integration tests pass.
   - Add a declarative authorization fixture under
     `tests/2025-06-18/declarative`.
   - Add a programmatic builder fixture under `tests/2025-06-18/mcp`.
   - Verify permitted and denied SDK calls and the JSON-RPC error contract.
   - Run:

     ```bash
     mvn -pl tests/2025-06-18/declarative,tests/2025-06-18/mcp -am verify
     ```

6. Protocol compatibility tests pass.
   - Verify the implementation-defined error is accepted by the 2024-11-05
     and 2025-03-26 clients.
   - Run:

     ```bash
     mvn -Pcompatibility verify
     ```

7. The secured example demonstrates the feature.
   - Add the MCP security dependency, ABAC provider, role validator, policy
     validator, and policy executor dependencies to
     `examples/secured-server/pom.xml`.
   - Configure ABAC in
     `examples/secured-server/src/main/resources/application.yaml`.
   - Demonstrate one role-protected tool and one policy-protected tool.
   - Update the Keycloak realm and README with the required role.
   - Keep endpoint authentication enabled.
   - Run:

     ```bash
     mvn -pl examples/secured-server -am verify
     ```

8. The complete build and repository checks pass.
   - Run:

     ```bash
     mvn verify
     ```
