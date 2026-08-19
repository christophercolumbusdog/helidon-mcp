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

package io.helidon.extensions.mcp.tests.declarative;

import java.util.Map;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.testing.junit5.ServerTest;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class McpSdkToolAuthorizationServerTest {
    private final McpSyncClient client;

    McpSdkToolAuthorizationServerTest(WebServer server) {
        client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + server.port())
                                        .endpoint("/toolAuthorization")
                                        .build())
                .build();
        client.initialize();
    }

    @Test
    void testOpenToolIsUnaffected() {
        var result = client.callTool(McpSchema.CallToolRequest.builder()
                                              .name("openTool")
                                              .arguments(Map.of())
                                              .build());
        assertThat(result.isError(), is(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"roleProtectedTool", "policyProtectedTool", "combinedProtectedTool"})
    void testProtectedToolIsDeniedWhenNoAuthorizerResolves(String name) {
        // No McpToolAuthorizer is discoverable in this module: the generic denial proves the generated
        // tool actually carries the declared authorization metadata (an unrelated codegen bug that dropped
        // the metadata would incorrectly permit the call instead).
        Exception e = assertThrows(Exception.class,
                                   () -> client.callTool(McpSchema.CallToolRequest.builder()
                                                                  .name(name)
                                                                  .arguments(Map.of())
                                                                  .build()));
        assertThat(e.getMessage(), containsString("Not authorized to call tool"));
    }

    @Test
    void testToolsListIncludesProtectedToolsWithoutFiltering() {
        var names = client.listTools().tools().stream().map(McpSchema.Tool::name).toList();
        assertThat(names, hasItems("openTool", "roleProtectedTool", "policyProtectedTool", "combinedProtectedTool"));
    }
}
