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
    void agentConfigToStringDoesNotExposeServiceOrLlmKeys() {
        String serviceKey = "service-key-sentinel-4f239";
        String llmKey = "llm-key-sentinel-8b517";
        AgentConfig config = new AgentConfig();
        config.setServiceApiKey(serviceKey);
        config.setLlmApiKey(llmKey);

        String rendered = config.toString();

        assertFalse(rendered.contains(serviceKey));
        assertFalse(rendered.contains(llmKey));
        assertFalse(rendered.contains("serviceApiKey"));
        assertFalse(rendered.contains("llmApiKey"));
    }

    @Test
    void dashScopeConfigToStringDoesNotExposeApiKey() {
        String apiKey = "dashscope-key-sentinel-3c681";
        DashScopeConfig config = new DashScopeConfig();
        config.setApiKey(apiKey);

        String rendered = config.toString();

        assertFalse(rendered.contains(apiKey));
        assertFalse(rendered.contains("apiKey"));
    }
}
