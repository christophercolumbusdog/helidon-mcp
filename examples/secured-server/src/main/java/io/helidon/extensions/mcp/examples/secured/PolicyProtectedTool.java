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
 * A tool guarded by an ABAC policy statement instead of a role. The policy is evaluated by the optional
 * {@code helidon4-extensions-mcp-security} integration, using the {@code abac} authorization provider and the
 * {@code helidon-security-abac-policy-el} policy executor configured for this application.
 */
final class PolicyProtectedTool implements McpTool {

    @Override
    public String name() {
        return "policy-protected-tool";
    }

    @Override
    public String description() {
        return "A tool that requires the mcp-user principal.";
    }

    @Override
    public String schema() {
        return "";
    }

    @Override
    public Optional<McpToolAuthorization> authorization() {
        return Optional.of(McpToolAuthorization.builder()
                                    .policyStatement("${subject.principal.name == 'mcp-user'}")
                                    .build());
    }

    @Override
    public Function<McpRequest, McpToolResult> tool() {
        return request -> McpToolResult.builder()
                .addContent(McpToolContents.textContent("Access granted to the mcp-user principal."))
                .build();
    }
}
