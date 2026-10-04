package com.taskflow.worker;

import com.taskflow.retry.RetryConfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Queue consumer service. It has no web layer: it only reads job ids from Redis and writes
 * results back to PostgreSQL.
 *
 * <p>Scanning is deliberately limited to {@code com.taskflow.worker} so the worker cannot
 * accidentally pick up REST components, while the core module's entities, repositories and
 * properties are wired in explicitly.
 */
@SpringBootApplication(scanBasePackages = "com.taskflow.worker")
@Import(RetryConfig.class)
@EnableJpaRepositories(basePackages = "com.taskflow.repository")
@EntityScan(basePackages = "com.taskflow.entity")
@EnableScheduling
@ConfigurationPropertiesScan({"com.taskflow.config", "com.taskflow.worker.config"})
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}