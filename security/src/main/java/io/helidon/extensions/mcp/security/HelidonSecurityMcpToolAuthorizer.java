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

package io.helidon.extensions.mcp.security;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Optional;

import io.helidon.extensions.mcp.security.McpAuthorizationMarkerValidator.MarkerConfig;
import io.helidon.extensions.mcp.server.McpRequest;
import io.helidon.extensions.mcp.server.McpTool;
import io.helidon.extensions.mcp.server.McpToolAuthorization;
import io.helidon.extensions.mcp.server.McpToolAuthorizer;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.SecurityContext;
import io.helidon.security.abac.policy.PolicyValidator;
import io.helidon.security.abac.role.RoleValidator;

/**
 * {@link McpToolAuthorizer} that evaluates an {@link McpToolAuthorization} using Helidon Security. Roles are
 * evaluated with {@link RoleValidator} and a policy statement with {@link PolicyValidator}, both driven through
 * an attribute based access control (ABAC) {@code AbacProvider} configured by the application.
 * <p>
 * This authorizer fails closed: it denies whenever it cannot prove that the configured authorization provider
 * actually evaluated the tool's authorization metadata. See {@link McpAuthorizationMarkerValidator} for how that
 * proof is obtained.
 */
public final class HelidonSecurityMcpToolAuthorizer implements McpToolAuthorizer {
    private static final System.Logger LOGGER = System.getLogger(HelidonSecurityMcpToolAuthorizer.class.getName());

    @Override
    public boolean authorize(McpTool tool, McpRequest request) {
        Optional<SecurityContext> securityContext = request.requestContext().get(SecurityContext.class);
        if (securityContext.isEmpty()) {
            logDenial(tool, "no SecurityContext is available in the request context");
            return false;
        }

        SecurityContext context = securityContext.get();
        if (!context.isAuthenticated()) {
            logDenial(tool, "the request has no authenticated user");
            return false;
        }

        McpToolAuthorization authorization = tool.authorization()
                .orElseThrow(() -> new IllegalStateException("Tool \"" + tool.name() + "\" has no authorization metadata"));

        EndpointConfig originalEndpointConfig = context.endpointConfig();
        try {
            MarkerConfig marker = new MarkerConfig();
            context.endpointConfig(deriveEndpointConfig(originalEndpointConfig, authorization, marker));

            AuthorizationResponse response = context.authorize(tool);
            boolean permitted = response.isPermitted() && marker.evaluated();
            if (!permitted && LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG,
                          "Denying tool \"" + tool.name() + "\": permitted=" + response.isPermitted()
                                  + ", evaluatedByAbac=" + marker.evaluated());
            }
            return permitted;
        } finally {
            context.endpointConfig(originalEndpointConfig);
        }
    }

    private EndpointConfig deriveEndpointConfig(EndpointConfig originalEndpointConfig,
                                                McpToolAuthorization authorization,
                                                MarkerConfig marker) {
        EndpointConfig.Builder derived = originalEndpointConfig.derive();

        List<String> roles = authorization.roles();
        if (!roles.isEmpty()) {
            derived.customObject(RoleValidator.RoleConfig.class, RoleValidator.RoleConfig.create(roles));
        }
        authorization.policyStatement()
                .ifPresent(policyStatement -> derived.customObject(
                        PolicyValidator.PolicyConfig.class,
                        PolicyValidator.PolicyConfig.builder()
                                .statement(policyStatement)
                                .build()));
        derived.customObject(MarkerConfig.class, marker);

        return derived.build();
    }

    private void logDenial(McpTool tool, String reason) {
        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG, "Denying tool \"" + tool.name() + "\": " + reason);
        }
    }
}
