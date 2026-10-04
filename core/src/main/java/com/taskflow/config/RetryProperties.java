package com.taskflow.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retry and backoff settings, bound from {@code taskflow.retry}.
 */
@ConfigurationProperties(prefix = "taskflow.retry")
public record RetryProperties(
        int defaultMaxAttempts,
        Duration baseDelay,
        Duration maxDelay) {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    public RetryProperties {
        defaultMaxAttempts = defaultMaxAttempts <= 0 ? DEFAULT_MAX_ATTEMPTS : defaultMaxAttempts;
        baseDelay = baseDelay == null || baseDelay.isNegative() ? Duration.ofSeconds(5) : baseDelay;
        maxDelay = maxDelay == null || maxDelay.isNegative() ? Duration.ofMinutes(5) : maxDelay;
    }
}