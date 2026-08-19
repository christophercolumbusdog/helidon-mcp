/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.extensions.mcp.tests.declarative;

import io.helidon.extensions.mcp.server.Mcp;

/**
 * This module does not depend on {@code helidon4-extensions-mcp-security}, so no {@code McpToolAuthorizer}
 * resolves for any of the protected tools below. Every protected tool call is therefore expected to be
 * denied, which proves that code generation correctly attached the declared authorization metadata to the
 * generated tool: an unrelated codegen bug that dropped the metadata would incorrectly permit these calls
 * instead.
 */
@Mcp.Server
@Mcp.Path("/toolAuthorization")
class McpToolAuthorizationServer {
    @Mcp.Tool("Open tool")
    String openTool() {
        return "open";
    }

    @Mcp.Tool("Role protected tool")
    @Mcp.RolesAllowed({"x-admin", "x-monitor"})
    String roleProtectedTool() {
        return "role-protected";
    }

    @Mcp.Tool("Policy protected tool")
    @Mcp.PolicyStatement("${subject.principal.name == 'monitor-user'}")
    String policyProtectedTool() {
        return "policy-protected";
    }

    @Mcp.Tool("Combined role and policy protected tool")
    @Mcp.RolesAllowed("x-admin")
    @Mcp.PolicyStatement("${true}")
    String combinedProtectedTool() {
        return "combined-protected";
    }
}
