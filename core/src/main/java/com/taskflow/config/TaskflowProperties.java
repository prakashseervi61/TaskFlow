package com.taskflow.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TaskFlow configuration, bound from {@code taskflow.*}. Defaults live in
 * {@code application.yml} as {@code ${VAR:default}}, so no null-coalescing is needed here.
 */
@ConfigurationProperties(prefix = "taskflow")
public record TaskflowProperties(Cors cors, Queue queue, Execution execution) {

    public record Cors(List<String> allowedOrigins) {
    }

    public record Queue(String key) {
    }

    public record Execution(long simulatedDurationMs) {
    }
}