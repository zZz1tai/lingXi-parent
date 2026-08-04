package com.lingXi.app.config;

import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigInteger;

@Configuration
public class BaseConfig {

    @Bean
    public JsonMapperBuilderCustomizer jacksonJsonMapperBuilderCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("lingXiLongToStringModule");
            module.addSerializer(Long.class, ToStringSerializer.instance)
                    .addSerializer(Long.TYPE, ToStringSerializer.instance)
                    .addSerializer(BigInteger.class, ToStringSerializer.instance);
            builder.addModule(module);
        };
    }

}