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

import io.helidon.common.Errors;
import io.helidon.security.ProviderRequest;
import io.helidon.security.abac.policy.spi.PolicyExecutor;

/**
 * A trivial policy executor used only by tests, so the module does not need a real EL executor dependency
 * to exercise the ABAC policy path. Statements are plain string literals rather than expressions.
 */
final class TestPolicyExecutor implements PolicyExecutor {
    static final String PERMIT = "test:permit";
    static final String DENY = "test:deny";
    static final String THROW = "test:throw";

    @Override
    public boolean supports(String policyStatement, ProviderRequest request) {
        return policyStatement.startsWith("test:");
    }

    @Override
    public void executePolicy(String policyStatement, Errors.Collector collector, ProviderRequest request) {
        switch (policyStatement) {
            case PERMIT -> {
            }
            case DENY -> collector.fatal(this, "denied by test policy statement");
            case THROW -> throw new IllegalStateException("simulated policy evaluation failure");
            default -> collector.fatal(this, "unknown test policy statement: " + policyStatement);
        }
    }
}
