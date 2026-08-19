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

import io.helidon.common.config.Config;
import io.helidon.security.abac.policy.spi.PolicyExecutor;
import io.helidon.security.abac.policy.spi.PolicyExecutorService;

/**
 * {@link java.util.ServiceLoader} registration for {@link TestPolicyExecutor}, discovered only on this
 * module's test classpath.
 */
public final class TestPolicyExecutorService implements PolicyExecutorService {
    @Override
    public String configKey() {
        return "test-policy-executor";
    }

    @Override
    public PolicyExecutor instantiate(Config config) {
        return new TestPolicyExecutor();
    }
}
