package com.taskflow.retry;

import com.taskflow.config.RetryProperties;
import java.time.Duration;
import java.time.Instant;

/**
 * Exponential backoff for job retries.
 *
 * <p>Delay grows as {@code base * 2^(attempt-1)} and is clamped to {@code maxDelay}, so a
 * flaky dependency is retried quickly at first and then backed off instead of being hammered.
 */
public class BackoffPolicy {

    private final Duration baseDelay;
    private final Duration maxDelay;

    public BackoffPolicy(RetryProperties properties) {
        this.baseDelay = properties.baseDelay();
        this.maxDelay = properties.maxDelay();
    }

    /**
     * How long to wait before attempt number {@code attempt + 1}.
     *
     * @param attempt 1-based number of the attempt that just failed
     */
    public Duration delayAfter(int attempt) {
        if (attempt < 1) {
            return baseDelay;
        }
        long baseMillis = baseDelay.toMillis();
        // Cap the shift before it can overflow; maxDelay already bounds the result.
        int shift = Math.min(attempt - 1, 32);
        long scaled = baseMillis << shift;
        if (scaled < 0 || scaled > maxDelay.toMillis()) {
            return maxDelay;
        }
        return Duration.ofMillis(scaled);
    }

    /** The instant a retry of {@code attempt} becomes eligible. */
    public Instant nextAttemptAt(int attempt) {
        return Instant.now().plus(delayAfter(attempt));
    }
}