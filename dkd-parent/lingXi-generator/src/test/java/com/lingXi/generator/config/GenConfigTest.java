package com.lingXi.generator.config;

import java.net.URL;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GenConfigTest
{
    private AnnotationConfigApplicationContext cleanContext()
    {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        // 模拟干净机器没有被 Git 忽略的本地配置，不读取开发机 generator.yml。
        context.setClassLoader(new ClassLoader(GenConfig.class.getClassLoader())
        {
            @Override
            public URL getResource(String name)
            {
                return "generator.yml".equals(name) ? null : super.getResource(name);
            }
        });
        context.register(GenConfig.class);
        return context;
    }

    @Test
    void startsWithoutLocalGeneratorConfiguration()
    {
        try (AnnotationConfigApplicationContext context = cleanContext())
        {
            context.refresh();
            assertEquals("LingXi", GenConfig.getAuthor());
            assertEquals("com.lingXi.system", GenConfig.getPackageName());
            assertEquals("sys_", GenConfig.getTablePrefix());
            assertFalse(GenConfig.getAutoRemovePre());
        }
    }

    @Test
    void preservesExplicitConfigurationOverrides()
    {
        try (AnnotationConfigApplicationContext context = cleanContext())
        {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "test-overrides", Map.of("author", "本地作者", "packageName", "com.example.local")));
            context.refresh();
            assertEquals("本地作者", GenConfig.getAuthor());
            assertEquals("com.example.local", GenConfig.getPackageName());
        }
    }
}
