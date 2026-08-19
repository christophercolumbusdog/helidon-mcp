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

/**
 * Helidon Security integration for MCP per-tool authorization.
 */
module io.helidon.extensions.mcp.security {
    requires io.helidon.common.context;
    requires io.helidon.extensions.mcp.server;
    requires io.helidon.security;
    requires io.helidon.security.providers.abac;
    requires io.helidon.security.abac.role;
    requires io.helidon.security.abac.policy;

    provides io.helidon.extensions.mcp.server.McpToolAuthorizer
            with io.helidon.extensions.mcp.security.HelidonSecurityMcpToolAuthorizer;
    provides io.helidon.security.providers.abac.spi.AbacValidatorService
            with io.helidon.extensions.mcp.security.McpAuthorizationMarkerValidatorService;

    exports io.helidon.extensions.mcp.security;
}
