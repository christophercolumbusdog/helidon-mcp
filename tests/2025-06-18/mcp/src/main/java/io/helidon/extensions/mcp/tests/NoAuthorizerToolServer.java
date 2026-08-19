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

package io.helidon.extensions.mcp.tests;

import io.helidon.extensions.mcp.server.McpServerFeature;
import io.helidon.extensions.mcp.server.McpToolAuthorization;
import io.helidon.extensions.mcp.server.McpToolResult;
import io.helidon.webserver.http.HttpRouting;

import static io.helidon.extensions.mcp.server.McpToolContents.textContent;

/**
 * Fixture with a protected tool and no {@code McpToolAuthorizer} configured (this module does not depend on
 * the security module, so no provider is discoverable through {@link java.util.ServiceLoader} either).
 * Exercises the fail-closed path when a protected tool exists but no authorizer resolves.
 */
final class NoAuthorizerToolServer {
    private NoAuthorizerToolServer() {
    }

    static void setUpRoute(HttpRouting.Builder builder) {
        builder.addFeature(McpServerFeature.builder()
                                   .path("/")
                                   .addTool(tool -> tool.name("protected")
                                           .description("Role protected tool with no authorizer configured")
                                           .schema("")
                                           .authorization(McpToolAuthorization.builder().addRole("x-admin").build())
                                           .tool(request -> McpToolResult.builder()
                                                   .addContent(textContent("ok"))
                                                   .build())));
    }
}
