package com.taskflow.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retry and backoff settings, bound from {@code taskflow.retry}. Defaults live in
 * {@code application.yml}.
 */
@ConfigurationProperties(prefix = "taskflow.retry")
public record RetryProperties(int defaultMaxAttempts, Duration baseDelay, Duration maxDelay) {
}