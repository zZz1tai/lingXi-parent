package com.dkd.framework.config.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.util.pattern.PathPatternParser;

class PermitAllUrlPropertiesTest
{
    @Test
    void normalizesUrlsWhenPathPatternParserIsActive()
    {
        RequestMappingInfo.BuilderConfiguration options =
                new RequestMappingInfo.BuilderConfiguration();
        options.setPatternParser(new PathPatternParser());
        RequestMappingInfo info = RequestMappingInfo
                .paths("/public/{id}", "/health")
                .options(options)
                .build();

        assertEquals(
                Set.of("/public/*", "/health"),
                new HashSet<>(PermitAllUrlProperties.normalizePatterns(info)));
    }
}
