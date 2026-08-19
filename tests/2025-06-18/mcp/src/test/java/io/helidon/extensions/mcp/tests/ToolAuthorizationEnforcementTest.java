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

import java.util.Map;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class ToolAuthorizationEnforcementTest extends AbstractMcpSdkTest {
    private final McpSyncClient client;

    ToolAuthorizationEnforcementTest(WebServer server) {
        client = McpClient.sync(streamable(server.port())).build();
        client.initialize();
    }

    @Override
    McpSyncClient client() {
        return client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        ToolAuthorizationServer.setUpRoute(builder);
    }

    @Test
    void testUnprotectedToolBehavesUnchanged() {
        int before = ToolAuthorizationServer.UNPROTECTED_CALLS.get();
        var result = client.callTool(McpSchema.CallToolRequest.builder()
                                              .name("unprotected")
                                              .arguments(Map.of())
                                              .build());
        assertThat(result.isError(), is(false));
        assertThat(ToolAuthorizationServer.UNPROTECTED_CALLS.get(), is(before + 1));
    }

    @Test
    void testPermittedCallInvokesToolExactlyOnce() {
        int before = ToolAuthorizationServer.GRANTED_CALLS.get();
        var result = client.callTool(McpSchema.CallToolRequest.builder()
                                              .name("granted")
                                              .arguments(Map.of())
                                              .build());
        assertThat(result.isError(), is(false));
        assertThat(ToolAuthorizationServer.GRANTED_CALLS.get(), is(before + 1));
    }

    @Test
    void testDeniedCallNeverInvokesToolAndReturnsGenericError() {
        int before = ToolAuthorizationServer.DENIED_CALLS.get();
        Exception e = assertThrows(Exception.class,
                                   () -> client.callTool(McpSchema.CallToolRequest.builder()
                                                                  .name("denied")
                                                                  .arguments(Map.of())
                                                                  .build()));
        assertThat(e.getMessage(), containsString("Not authorized to call tool"));
        assertThat(ToolAuthorizationServer.DENIED_CALLS.get(), is(before));
    }

    @Test
    void testAuthorizerExceptionNeverInvokesToolAndReturnsGenericError() {
        int before = ToolAuthorizationServer.THROWING_CALLS.get();
        Exception e = assertThrows(Exception.class,
                                   () -> client.callTool(McpSchema.CallToolRequest.builder()
                                                                  .name("throwing")
                                                                  .arguments(Map.of())
                                                                  .build()));
        // the authorizer's own exception message and role name must not leak to the client
        assertThat(e.getMessage(), containsString("Not authorized to call tool"));
        assertThat(ToolAuthorizationServer.THROWING_CALLS.get(), is(before));
    }

    @Test
    void testToolsListIncludesProtectedToolsWithoutFiltering() {
        var names = client.listTools().tools().stream().map(McpSchema.Tool::name).toList();
        assertThat(names, hasItems("unprotected", "granted", "denied", "throwing"));
    }
}
