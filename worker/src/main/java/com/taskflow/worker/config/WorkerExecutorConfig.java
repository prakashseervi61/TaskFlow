package com.taskflow.worker.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Consumer threads. {@code worker.concurrency} decides how many jobs one worker instance can
 * execute at a time; scaling out is then just running more instances against the same queue.
 */
@Configuration(proxyBeanMethods = false)
public class WorkerExecutorConfig {

    @Bean(name = "jobConsumerExecutor")
    public Executor jobConsumerExecutor(WorkerProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.concurrency());
        executor.setMaxPoolSize(properties.concurrency());
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("job-consumer-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}