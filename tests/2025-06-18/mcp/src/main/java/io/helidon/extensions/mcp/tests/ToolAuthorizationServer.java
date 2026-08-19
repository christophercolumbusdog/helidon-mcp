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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.extensions.mcp.server.McpServerFeature;
import io.helidon.extensions.mcp.server.McpTool;
import io.helidon.extensions.mcp.server.McpToolAuthorization;
import io.helidon.extensions.mcp.server.McpToolResult;
import io.helidon.webserver.http.HttpRouting;

import static io.helidon.extensions.mcp.server.McpToolContents.textContent;

/**
 * Fixture exercising the per-tool authorization enforcement flow with a custom, in-process
 * {@code McpToolAuthorizer} (not the Helidon Security integration, which is covered by the security module's
 * own tests).
 */
final class ToolAuthorizationServer {
    static final String GRANTED_ROLE = "grant";
    static final String DENIED_ROLE = "deny";
    static final String THROWING_ROLE = "throw";

    static final AtomicInteger UNPROTECTED_CALLS = new AtomicInteger();
    static final AtomicInteger GRANTED_CALLS = new AtomicInteger();
    static final AtomicInteger DENIED_CALLS = new AtomicInteger();
    static final AtomicInteger THROWING_CALLS = new AtomicInteger();

    private ToolAuthorizationServer() {
    }

    static void setUpRoute(HttpRouting.Builder builder) {
        builder.addFeature(McpServerFeature.builder()
                                   .path("/")
                                   .toolAuthorizer(ToolAuthorizationServer::authorize)
                                   .addTool(tool -> tool.name("unprotected")
                                           .description("Tool without authorization metadata")
                                           .schema("")
                                           .tool(request -> {
                                               UNPROTECTED_CALLS.incrementAndGet();
                                               return McpToolResult.builder().addContent(textContent("ok")).build();
                                           }))
                                   .addTool(tool -> tool.name("granted")
                                           .description("Role protected tool that the test authorizer always grants")
                                           .schema("")
                                           .authorization(McpToolAuthorization.builder().addRole(GRANTED_ROLE).build())
                                           .tool(request -> {
                                               GRANTED_CALLS.incrementAndGet();
                                               return McpToolResult.builder().addContent(textContent("ok")).build();
                                           }))
                                   .addTool(tool -> tool.name("denied")
                                           .description("Role protected tool that the test authorizer always denies")
                                           .schema("")
                                           .authorization(McpToolAuthorization.builder().addRole(DENIED_ROLE).build())
                                           .tool(request -> {
                                               DENIED_CALLS.incrementAndGet();
                                               return McpToolResult.builder().addContent(textContent("ok")).build();
                                           }))
                                   .addTool(tool -> tool.name("throwing")
                                           .description("Role protected tool whose authorizer throws")
                                           .schema("")
                                           .authorization(McpToolAuthorization.builder().addRole(THROWING_ROLE).build())
                                           .tool(request -> {
                                               THROWING_CALLS.incrementAndGet();
                                               return McpToolResult.builder().addContent(textContent("ok")).build();
                                           })));
    }

    private static boolean authorize(McpTool tool, io.helidon.extensions.mcp.server.McpRequest request) {
        List<String> roles = tool.authorization().map(McpToolAuthorization::roles).orElse(List.of());
        if (roles.contains(THROWING_ROLE)) {
            throw new RuntimeException("simulated authorizer failure");
        }
        return roles.contains(GRANTED_ROLE);
    }
}
