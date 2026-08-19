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

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;

import io.helidon.common.Errors;
import io.helidon.common.config.Config;
import io.helidon.security.EndpointConfig;
import io.helidon.security.ProviderRequest;
import io.helidon.security.providers.abac.AbacValidatorConfig;
import io.helidon.security.providers.abac.spi.AbacValidator;

/**
 * A private ABAC validator that proves an {@code AbacProvider} actually evaluated the derived endpoint
 * configuration built by {@link HelidonSecurityMcpToolAuthorizer}.
 * <p>
 * Helidon Security 4.3.1's {@code AuthorizationClientImpl.submit()} returns {@code AuthorizationResponse.permit()}
 * when no authorization provider resolves at all. A permitted response alone therefore does not prove that ABAC
 * processed the role or policy custom objects added for a protected tool. This validator is always attached to
 * the derived endpoint configuration alongside the role and/or policy custom objects; {@link #validate} runs, and
 * therefore marks the {@link MarkerConfig} instance as evaluated, if and only if an {@code AbacProvider} actually
 * consulted the derived endpoint configuration.
 */
final class McpAuthorizationMarkerValidator implements AbacValidator<McpAuthorizationMarkerValidator.MarkerConfig> {
    static final String CONFIG_KEY = "mcp-authorization-marker";

    @Override
    public Collection<Class<? extends Annotation>> supportedAnnotations() {
        return List.of();
    }

    @Override
    public Class<MarkerConfig> configClass() {
        return MarkerConfig.class;
    }

    @Override
    public String configKey() {
        return CONFIG_KEY;
    }

    @Override
    public MarkerConfig fromConfig(Config config) {
        // Never used: the marker is always attached as an explicit custom object, never derived from
        // configuration or annotations.
        return new MarkerConfig();
    }

    @Override
    public MarkerConfig fromAnnotations(EndpointConfig endpointConfig) {
        return new MarkerConfig();
    }

    @Override
    public void validate(MarkerConfig config, Errors.Collector collector, ProviderRequest request) {
        config.markEvaluated();
    }

    /**
     * Mutable marker propagated to Helidon Security as a custom object. The same instance is passed back into
     * {@link #validate(MarkerConfig, Errors.Collector, ProviderRequest)} by {@code AbacProvider}, so
     * {@link HelidonSecurityMcpToolAuthorizer} can inspect it after {@code SecurityContext.authorize(Object...)}
     * returns.
     */
    static final class MarkerConfig implements AbacValidatorConfig {
        private volatile boolean evaluated;

        void markEvaluated() {
            evaluated = true;
        }

        boolean evaluated() {
            return evaluated;
        }
    }
}
