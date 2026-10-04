package com.taskflow.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TaskFlow specific configuration, bound from the {@code taskflow.*} namespace. Lives in
 * core so the API and the worker read the same keys.
 */
@ConfigurationProperties(prefix = "taskflow")
public record TaskflowProperties(Cors cors, Queue queue, Execution execution) {

    public TaskflowProperties {
        cors = cors == null ? new Cors(List.of(), List.of()) : cors;
        queue = queue == null ? new Queue("taskflow:jobs") : queue;
        execution = execution == null ? new Execution(2500L) : execution;
    }

    public record Cors(List<String> allowedOrigins, List<String> allowedMethods) {
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins;
            allowedMethods = allowedMethods == null ? List.of() : allowedMethods;
        }
    }

    /** Redis list that job ids are pushed to, and read from by the worker. */
    public record Queue(String key) {
        public Queue {
            key = key == null || key.isBlank() ? "taskflow:jobs" : key;
        }
    }

    public record Execution(long simulatedDurationMs) {
    }

}