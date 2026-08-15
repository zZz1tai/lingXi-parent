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
    void agentServicePropertiesBindToAgentConfig() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("agent.service-api-key", "service-key-sentinel");
        properties.put("agent.novel-sync-read-timeout", "960000");
        properties.put("agent.novel-sync-provider-timeout-seconds", "300");

        AgentConfig config = new Binder(new MapConfigurationPropertySource(properties))
                .bind("agent", Bindable.of(AgentConfig.class))
                .get();

        assertEquals("service-key-sentinel", config.getServiceApiKey());
        assertEquals(960000, config.getNovelSyncReadTimeout());
        assertEquals(300, config.getNovelSyncProviderTimeoutSeconds());
    }

    @Test
    void novelSyncTimeoutsUseDocumentedDefaultsWhenUnset() {
        AgentConfig config = new AgentConfig();
        assertEquals(960_000, config.getNovelSyncReadTimeout());
        assertEquals(300, config.getNovelSyncProviderTimeoutSeconds());
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
