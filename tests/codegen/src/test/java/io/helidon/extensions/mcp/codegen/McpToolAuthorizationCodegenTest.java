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

package io.helidon.extensions.mcp.codegen;

import java.util.List;
import java.util.Map;

import io.helidon.codegen.CodegenException;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the role and policy statement validation performed by {@link McpToolCodegen} while generating the
 * {@code authorization()} method of a declarative tool. This test calls the validation methods directly
 * (rather than running full annotation processing), since the repository has no in-process compile-testing
 * infrastructure for asserting {@link CodegenException} on invalid source.
 */
class McpToolAuthorizationCodegenTest {
    private static final TypeName ROLES_ALLOWED = TypeName.create("io.helidon.extensions.mcp.server.Mcp.RolesAllowed");
    private static final TypeName POLICY_STATEMENT = TypeName.create("io.helidon.extensions.mcp.server.Mcp.PolicyStatement");

    private final McpToolCodegen codegen = new McpToolCodegen(new McpRecorder());

    @Test
    void testValidRoles() {
        List<String> roles = codegen.validateRoles(element(), rolesAnnotation(List.of("x-admin", "x-monitor")));
        assertThat(roles, contains("x-admin", "x-monitor"));
    }

    @Test
    void testEmptyRoleArrayIsRejected() {
        CodegenException e = assertThrows(CodegenException.class,
                                          () -> codegen.validateRoles(element(), rolesAnnotation(List.of())));
        assertThat(e.getMessage(), is("@Mcp.RolesAllowed on method \"tool\" requires at least one non-blank role"));
    }

    @Test
    void testBlankRoleIsRejected() {
        CodegenException e = assertThrows(CodegenException.class,
                                          () -> codegen.validateRoles(element(), rolesAnnotation(List.of("x-admin", " "))));
        assertThat(e.getMessage(), is("@Mcp.RolesAllowed on method \"tool\" must not contain a blank role"));
    }

    @Test
    void testValidPolicyStatement() {
        String policy = codegen.validatePolicyStatement(element(), policyAnnotation("${true}"));
        assertThat(policy, is("${true}"));
    }

    @Test
    void testBlankPolicyStatementIsRejected() {
        CodegenException e = assertThrows(CodegenException.class,
                                          () -> codegen.validatePolicyStatement(element(), policyAnnotation(" ")));
        assertThat(e.getMessage(), is("@Mcp.PolicyStatement on method \"tool\" requires a non-blank policy statement"));
    }

    private static TypedElementInfo element() {
        return TypedElementInfo.builder()
                .typeName(TypeNames.STRING)
                .elementName("tool")
                .kind(ElementKind.METHOD)
                .accessModifier(AccessModifier.PUBLIC)
                .originatingElement(new Object())
                .build();
    }

    private static Annotation rolesAnnotation(List<String> roles) {
        return Annotation.create(ROLES_ALLOWED, Map.of("value", roles));
    }

    private static Annotation policyAnnotation(String policy) {
        return Annotation.create(POLICY_STATEMENT, Map.of("value", policy));
    }
}
