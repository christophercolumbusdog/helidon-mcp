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

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpToolAuthorizationTest {

    @Test
    void testUnprotectedToolHasNoAuthorization() {
        McpTool tool = McpTool.builder()
                .name("name")
                .description("description")
                .schema("")
                .tool(request -> null)
                .build();
        assertThat(tool.authorization().isEmpty(), is(true));
    }

    @Test
    void testRoleOnlyMetadata() {
        McpToolAuthorization authorization = McpToolAuthorization.builder()
                .addRole("x-admin")
                .addRole("x-monitor")
                .build();
        assertThat(authorization.roles(), contains("x-admin", "x-monitor"));
        assertThat(authorization.policyStatement().isEmpty(), is(true));
    }

    @Test
    void testPolicyOnlyMetadata() {
        McpToolAuthorization authorization = McpToolAuthorization.builder()
                .policyStatement("${subject.principal.name == 'monitor-user'}")
                .build();
        assertThat(authorization.roles(), empty());
        assertThat(authorization.policyStatement().orElse(""), is("${subject.principal.name == 'monitor-user'}"));
    }

    @Test
    void testCombinedRoleAndPolicyMetadata() {
        McpToolAuthorization authorization = McpToolAuthorization.builder()
                .addRole("x-monitor")
                .policyStatement("${object.name == 'monitorStatus'}")
                .build();
        assertThat(authorization.roles(), contains("x-monitor"));
        assertThat(authorization.policyStatement().orElse(""), is("${object.name == 'monitorStatus'}"));
    }

    @Test
    void testToolCarriesAuthorizationMetadata() {
        McpToolAuthorization authorization = McpToolAuthorization.builder()
                .addRole("x-admin")
                .build();
        McpTool tool = McpTool.builder()
                .name("name")
                .description("description")
                .schema("")
                .authorization(authorization)
                .tool(request -> null)
                .build();
        assertThat(tool.authorization().orElseThrow(), is(authorization));
    }

    @Test
    void testValidationRejectsNeitherRoleNorPolicy() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                                  () -> McpToolAuthorization.builder().build());
        assertThat(e.getMessage(), is("McpToolAuthorization requires at least one role or a policy statement"));
    }

    @Test
    void testValidationRejectsBlankRole() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                                  () -> McpToolAuthorization.builder()
                                                          .addRole("x-admin")
                                                          .addRole(" ")
                                                          .build());
        assertThat(e.getMessage(), is("McpToolAuthorization roles must not be blank"));
    }

    @Test
    void testValidationRejectsBlankPolicyStatement() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                                  () -> McpToolAuthorization.builder()
                                                          .policyStatement(" ")
                                                          .build());
        assertThat(e.getMessage(), is("McpToolAuthorization policy statement must not be blank"));
    }
}
