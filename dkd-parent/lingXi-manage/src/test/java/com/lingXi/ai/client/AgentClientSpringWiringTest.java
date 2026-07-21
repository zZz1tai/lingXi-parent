package com.lingXi.ai.client;

import com.lingXi.ai.config.AgentConfig;
import com.lingXi.aiVedio.config.AiVideoModelConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class AgentClientSpringWiringTest {

    @Test
    void springSelectsTheProductionConstructorWhenTestConstructorAlsoExists() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.registerBean(AgentConfig.class, AgentConfig::new);
            context.getBeanFactory().registerSingleton(
                    "aiVideoModelConfigService", mock(AiVideoModelConfigService.class));
            context.registerBean(AgentClient.class);
            context.refresh();

            assertNotNull(context.getBean(AgentClient.class));
        }
    }
}
