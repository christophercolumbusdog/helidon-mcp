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

package io.helidon.extensions.mcp.examples.secured;

import java.util.Optional;
import java.util.function.Function;

import io.helidon.extensions.mcp.server.McpRequest;
import io.helidon.extensions.mcp.server.McpTool;
import io.helidon.extensions.mcp.server.McpToolAuthorization;
import io.helidon.extensions.mcp.server.McpToolContents;
import io.helidon.extensions.mcp.server.McpToolResult;

/**
 * A tool that requires the {@code mcp-admin} role. The role is evaluated by the optional
 * {@code helidon4-extensions-mcp-security} integration, using the {@code abac} authorization provider
 * configured in {@code application.yaml}.
 */
final class RoleProtectedTool implements McpTool {

    @Override
    public String name() {
        return "role-protected-tool";
    }

    @Override
    public String description() {
        return "A tool that requires the mcp-admin role.";
    }

    @Override
    public String schema() {
        return "";
    }

    @Override
    public Optional<McpToolAuthorization> authorization() {
        return Optional.of(McpToolAuthorization.builder()
                                    .addRole("mcp-admin")
                                    .build());
    }

    @Override
    public Function<McpRequest, McpToolResult> tool() {
        return request -> McpToolResult.builder()
                .addContent(McpToolContents.textContent("Access granted to mcp-admin role holder."))
                .build();
    }
}
