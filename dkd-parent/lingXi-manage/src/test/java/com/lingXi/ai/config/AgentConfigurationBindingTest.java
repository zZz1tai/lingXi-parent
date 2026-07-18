package com.lingXi.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentConfigurationBindingTest {

    @Test
    void agentServiceAndLlmPropertiesBindToAgentConfig() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("agent.service-api-key", "service-key-sentinel");
        properties.put("agent.llm-api-key", "llm-key-sentinel");
        properties.put("agent.llm-model", "model-sentinel");
        properties.put("agent.llm-base-url", "https://provider.invalid/v1");

        AgentConfig config = new Binder(new MapConfigurationPropertySource(properties))
                .bind("agent", Bindable.of(AgentConfig.class))
                .get();

        assertEquals("service-key-sentinel", config.getServiceApiKey());
        assertEquals("llm-key-sentinel", config.getLlmApiKey());
        assertEquals("model-sentinel", config.getLlmModel());
        assertEquals("https://provider.invalid/v1", config.getLlmBaseUrl());
    }

    @Test
    void documentedEnvironmentAliasIsUsedWhenSpringPropertyIsAbsent() {
        AgentConfig config = new AgentConfig();
        config.setEnvironment(new MockEnvironment()
                .withProperty("AGENT_SERVICE_API_KEY", "environment-service-key"));

        assertEquals("environment-service-key", config.getServiceApiKey());
        assertFalse(config.toString().contains("environment-service-key"));

        config.setServiceApiKey("explicit-spring-property");
        assertEquals("explicit-spring-property", config.getServiceApiKey());
    }
}
