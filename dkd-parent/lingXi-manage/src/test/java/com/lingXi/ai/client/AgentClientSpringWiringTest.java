package com.lingXi.ai.client;

import com.lingXi.ai.config.AgentConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentClientSpringWiringTest {

    @Test
    void springSelectsTheProductionConstructorWhenTestConstructorAlsoExists() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.registerBean(AgentConfig.class, AgentConfig::new);
            context.registerBean(AgentClient.class);
            context.refresh();

            assertNotNull(context.getBean(AgentClient.class));
        }
    }
}
