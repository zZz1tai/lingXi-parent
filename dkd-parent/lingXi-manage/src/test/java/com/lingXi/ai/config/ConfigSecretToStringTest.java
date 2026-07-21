package com.lingXi.ai.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConfigSecretToStringTest {

    @Test
    void agentStreamTimeoutDefaultsBeyondPythonStreamLimit() {
        assertEquals(Long.valueOf(310_000L), new AgentConfig().getStreamTimeout());
    }

    @Test
    void agentConfigToStringDoesNotExposeServiceKey() {
        String serviceKey = "service-key-sentinel-4f239";
        AgentConfig config = new AgentConfig();
        config.setServiceApiKey(serviceKey);

        String rendered = config.toString();

        assertFalse(rendered.contains(serviceKey));
        assertFalse(rendered.contains("serviceApiKey"));
    }
}
