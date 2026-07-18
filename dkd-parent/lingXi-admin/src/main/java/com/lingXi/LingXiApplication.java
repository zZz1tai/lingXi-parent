package com.lingXi;

import org.dromara.x.file.storage.spring.EnableFileStorage;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * 启动程序
 *
 * @author lingXi
 */
@EnableFileStorage
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@ComponentScan(basePackages = {"com.lingXi", "com.lingXi.manage", "com.lingXi.ai", "com.dkd.framework"})
@MapperScan({"com.lingXi.app.mapper", "com.lingXi.manage.mapper", "com.lingXi.ai.mapper", "com.lingXi.aiVedio.mapper"})
public class LingXiApplication
{
    public static void main(String[] args)
    {
        // System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication.run(LingXiApplication.class, args);
        System.out.println("(=①ω①=)灵犀启动成功(=①ω①=)");
        System.out.println("超级AI视频生成服务启动成功");
        System.out.println("666666666666666666666666666666666666\n666666666666666666666666666");
    }
}
