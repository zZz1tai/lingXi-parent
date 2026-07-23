package com.lingXi.aiVedio.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** AI 视频工作流专用异步执行器。 */
@Configuration
@EnableAsync
@EnableScheduling
public class AiVideoAsyncConfig implements SchedulingConfigurer
{
    @Value("${aivideo.scheduler.pool-size}")
    private int schedulerPoolSize;

    /**
     * 创建AI视频工作流专用异步执行器线程池。
     *
     * @return 异步执行器
     */
    @Bean("aiVideoExecutor")
    public Executor aiVideoExecutor()
    {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("ai-video-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 创建AI视频定时任务调度器线程池。
     *
     * @return 任务调度器
     */
    @Bean("aiVideoTaskScheduler")
    public ThreadPoolTaskScheduler aiVideoTaskScheduler()
    {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(schedulerPoolSize);
        scheduler.setThreadNamePrefix("ai-video-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }

    /**
     * 配置定时任务注册器，绑定AI视频调度器。
     *
     * @param taskRegistrar 任务注册器
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar)
    {
        taskRegistrar.setTaskScheduler(aiVideoTaskScheduler());
    }
}
