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

package io.helidon.extensions.mcp.server;

/**
 * Service Provider Interface used by the MCP server to evaluate {@link io.helidon.extensions.mcp.server.McpTool}
 * authorization metadata before a tool is invoked.
 * <p>
 * The server only consults this SPI for a tool that has authorization metadata; see
 * {@link io.helidon.extensions.mcp.server.McpTool#authorization()}. An {@link io.helidon.extensions.mcp.server.McpToolAuthorizer}
 * is resolved as follows:
 * <ol>
 *     <li>{@link io.helidon.extensions.mcp.server.McpServerConfig#toolAuthorizer()} when it is present.</li>
 *     <li>Otherwise, a single implementation discovered through {@link java.util.ServiceLoader}.</li>
 * </ol>
 * When a protected tool exists and more than one implementation is discovered through
 * {@link java.util.ServiceLoader}, server construction fails. The application must select one explicitly with
 * {@link io.helidon.extensions.mcp.server.McpServerConfig.Builder#toolAuthorizer(McpToolAuthorizer)}.
 * <p>
 * An implementation must fail closed: any exception thrown from {@link #authorize(McpTool, McpRequest)} is
 * treated as a denial by the server.
 */
@FunctionalInterface
public interface McpToolAuthorizer {
    /**
     * Evaluate whether the caller of the given request is authorized to call the given tool.
     *
     * @param tool the tool being called, including its authorization metadata
     * @param request the MCP request, including the request context used to access the caller's security state
     * @return {@code true} when the call is authorized, {@code false} otherwise
     */
    boolean authorize(McpTool tool, McpRequest request);
}
