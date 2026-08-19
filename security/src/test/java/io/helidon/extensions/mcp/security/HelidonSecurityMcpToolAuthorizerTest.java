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

import java.util.List;

import io.helidon.common.context.Context;
import io.helidon.extensions.mcp.server.McpFeatures;
import io.helidon.extensions.mcp.server.McpParameters;
import io.helidon.extensions.mcp.server.McpRequest;
import io.helidon.extensions.mcp.server.McpTool;
import io.helidon.extensions.mcp.server.McpToolAuthorization;
import io.helidon.extensions.mcp.server.McpToolResult;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Principal;
import io.helidon.security.Role;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.Subject;
import io.helidon.security.providers.abac.AbacProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HelidonSecurityMcpToolAuthorizerTest {
    private static final HelidonSecurityMcpToolAuthorizer AUTHORIZER = new HelidonSecurityMcpToolAuthorizer();

    @Test
    void permitsAllowedRole() {
        assertThat(authorizeAs(subject("x-admin"), roleTool("x-admin"), abacSecurity()), is(true));
    }

    @Test
    void deniesMissingRole() {
        assertThat(authorizeAs(subject("x-user"), roleTool("x-admin"), abacSecurity()), is(false));
    }

    @Test
    void permitsAllowedPolicy() {
        assertThat(authorizeAs(subject("x-user"), policyTool(TestPolicyExecutor.PERMIT), abacSecurity()), is(true));
    }

    @Test
    void deniesDeniedPolicy() {
        assertThat(authorizeAs(subject("x-user"), policyTool(TestPolicyExecutor.DENY), abacSecurity()), is(false));
    }

    @Test
    void combinedRoleAndPolicyUseAndSemantics() {
        Security security = abacSecurity();
        Subject admin = subject("x-admin");

        McpTool bothPass = combinedTool("x-admin", TestPolicyExecutor.PERMIT);
        McpTool policyFails = combinedTool("x-admin", TestPolicyExecutor.DENY);
        McpTool roleFails = combinedTool("x-nobody", TestPolicyExecutor.PERMIT);

        assertThat(authorizeAs(admin, bothPass, security), is(true));
        assertThat(authorizeAs(admin, policyFails, security), is(false));
        assertThat(authorizeAs(admin, roleFails, security), is(false));
    }

    @Test
    void deniesWhenNoSecurityContext() {
        McpRequest request = requestWithContext(Context.create());
        assertThat(AUTHORIZER.authorize(roleTool("x-admin"), request), is(false));
    }

    @Test
    void deniesUnauthenticatedContext() {
        SecurityContext context = abacSecurity().contextBuilder("test").build();
        McpRequest request = requestWithContext(contextWith(context));
        // no runAs(): context has no authenticated user
        assertThat(AUTHORIZER.authorize(roleTool("x-admin"), request), is(false));
    }

    @Test
    void deniesWhenNoAbacProviderResolves() {
        // AuthorizationClientImpl.submit() returns AuthorizationResponse.permit() when no provider resolves;
        // the marker must still force a denial.
        Security security = Security.builder().build();
        assertThat(authorizeAs(subject("x-admin"), roleTool("x-admin"), security), is(false));
    }

    @Test
    void deniesMissingPolicyExecutor() {
        // HelidonSecurityMcpToolAuthorizer does not itself catch evaluation exceptions: McpServerFeature
        // treats any exception thrown from McpToolAuthorizer.authorize(...) as a denial (spec enforcement
        // flow step 10). A missing policy executor surfaces as a propagated SecurityException.
        assertThrows(SecurityException.class,
                    () -> authorizeAs(subject("x-user"), policyTool("unsupported-policy-statement"), abacSecurity()));
    }

    @Test
    void deniesPolicyEvaluationException() {
        assertThrows(IllegalStateException.class,
                    () -> authorizeAs(subject("x-user"), policyTool(TestPolicyExecutor.THROW), abacSecurity()));
    }

    @Test
    void restoresOriginalEndpointConfigAfterPermitDenyAndException() {
        Security security = abacSecurity();
        SecurityContext context = security.contextBuilder("test").build();
        EndpointConfig original = context.endpointConfig();
        Subject admin = subject("x-admin");

        runAsAndAuthorize(context, admin, roleTool("x-admin"));
        assertThat(context.endpointConfig(), sameInstance(original));

        runAsAndAuthorize(context, admin, roleTool("x-nobody"));
        assertThat(context.endpointConfig(), sameInstance(original));

        McpRequest request = requestWithContext(contextWith(context));
        assertThrows(IllegalStateException.class,
                    () -> context.runAs(admin, () -> AUTHORIZER.authorize(policyTool(TestPolicyExecutor.THROW), request)));
        assertThat(context.endpointConfig(), sameInstance(original));
    }

    private static void runAsAndAuthorize(SecurityContext context, Subject subject, McpTool tool) {
        McpRequest request = requestWithContext(contextWith(context));
        context.runAs(subject, () -> AUTHORIZER.authorize(tool, request));
    }

    private static boolean authorizeAs(Subject subject, McpTool tool, Security security) {
        SecurityContext context = security.contextBuilder("test").build();
        McpRequest request = requestWithContext(contextWith(context));
        boolean[] result = new boolean[1];
        context.runAs(subject, () -> result[0] = AUTHORIZER.authorize(tool, request));
        return result[0];
    }

    private static Security abacSecurity() {
        return Security.builder()
                .addProvider(AbacProvider.create())
                .build();
    }

    private static Subject subject(String role) {
        return Subject.builder()
                .principal(Principal.create("test-user"))
                .addGrant(Role.create(role))
                .build();
    }

    private static Context contextWith(SecurityContext securityContext) {
        Context context = Context.create();
        context.register(securityContext);
        return context;
    }

    private static McpTool roleTool(String role) {
        return McpTool.builder()
                .name("roleTool")
                .description("role protected tool")
                .schema("")
                .authorization(McpToolAuthorization.builder().addRole(role).build())
                .tool(request -> McpToolResult.builder().contents(List.of()).build())
                .build();
    }

    private static McpTool policyTool(String policyStatement) {
        return McpTool.builder()
                .name("policyTool")
                .description("policy protected tool")
                .schema("")
                .authorization(McpToolAuthorization.builder().policyStatement(policyStatement).build())
                .tool(request -> McpToolResult.builder().contents(List.of()).build())
                .build();
    }

    private static McpTool combinedTool(String role, String policyStatement) {
        return McpTool.builder()
                .name("combinedTool")
                .description("role and policy protected tool")
                .schema("")
                .authorization(McpToolAuthorization.builder()
                                       .addRole(role)
                                       .policyStatement(policyStatement)
                                       .build())
                .tool(request -> McpToolResult.builder().contents(List.of()).build())
                .build();
    }

    private static McpRequest requestWithContext(Context requestContext) {
        return new McpRequest() {
            @Override
            public McpParameters parameters() {
                throw new UnsupportedOperationException();
            }

            @Override
            public McpParameters meta() {
                throw new UnsupportedOperationException();
            }

            @Override
            public McpFeatures features() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String protocolVersion() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Context sessionContext() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Context requestContext() {
                return requestContext;
            }
        };
    }
}
