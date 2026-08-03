package com.lingXi.web.core.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.lingXi.common.config.RuoYiConfig;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger接口配置
 * 
 * @author ruoyi
 */
@Configuration
@ConditionalOnProperty(name = "swagger.enabled", havingValue = "true")
public class SwaggerConfig
{
    /** 系统基础配置 */
    @Autowired
    private RuoYiConfig ruoyiConfig;

    /** 安全模式，这里指定token通过Authorization头请求头传递 */
    private static final String SECURITY_SCHEME_NAME = "Authorization";

    /**
     * 兼容旧的knife4j访问路径，/doc.html重定向到springdoc默认页面
     */
    @Bean
    public WebMvcConfigurer docRedirectConfigurer()
    {
        return new WebMvcConfigurer()
        {
            @Override
            public void addViewControllers(ViewControllerRegistry registry)
            {
                registry.addRedirectViewController("/doc.html", "/swagger-ui/index.html");
            }
        };
    }

    /**
     * 创建API
     */
    @Bean
    public OpenAPI customOpenAPI()
    {
        return new OpenAPI()
                // 添加该API的基本信息，展示在文档的页面中（自定义展示的信息）
                .info(apiInfo())
                // 安全模式，这里指定swagger可以访问token
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME, securityScheme()))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }

    /**
     * 安全模式，这里指定token通过Authorization头请求头传递
     */
    private SecurityScheme securityScheme()
    {
        return new SecurityScheme()
                .name(SECURITY_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");
    }

    /**
     * 添加摘要信息
     */
    private Info apiInfo()
    {
        return new Info()
                // 设置标题
                .title("标题：智能零售终端管理系统_接口文档")
                // 描述
                .description("描述：用于管理集团旗下公司的人员信息,具体包括XXX,XXX模块...")
                // 作者信息
                .contact(new Contact().name(ruoyiConfig.getName()))
                // 版本
                .version("版本号:" + ruoyiConfig.getVersion());
    }
}