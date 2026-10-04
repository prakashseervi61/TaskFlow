package com.taskflow.worker.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Worker-only settings, bound from {@code worker.*}. */
@ConfigurationProperties(prefix = "worker")
public record WorkerProperties(
        int concurrency,
        Duration pollTimeout,
        boolean recoverQueuedOnStartup,
        String failureMessage) {

    public WorkerProperties {
        concurrency = concurrency <= 0 ? 1 : concurrency;
        pollTimeout = pollTimeout == null ? Duration.ofSeconds(5) : pollTimeout;
        failureMessage =
                failureMessage == null || failureMessage.isBlank() ? "Job failed on purpose" : failureMessage;
    }
}