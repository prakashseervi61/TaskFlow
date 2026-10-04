package com.taskflow.retry;

import static org.assertj.core.api.Assertions.assertThat;

import com.taskflow.config.RetryProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BackoffPolicyTest {

    private BackoffPolicy policy(Duration base, Duration max) {
        return new BackoffPolicy(new RetryProperties(3, base, max));
    }

    @Test
    @DisplayName("delay doubles per attempt")
    void doublesPerAttempt() {
        BackoffPolicy policy = policy(Duration.ofSeconds(5), Duration.ofMinutes(5));

        assertThat(policy.delayAfter(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.delayAfter(2)).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.delayAfter(3)).isEqualTo(Duration.ofSeconds(20));
        assertThat(policy.delayAfter(4)).isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    @DisplayName("delay is clamped to maxDelay")
    void clampsToMax() {
        BackoffPolicy policy = policy(Duration.ofSeconds(5), Duration.ofMinutes(1));

        assertThat(policy.delayAfter(10)).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.delayAfter(60)).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("a huge attempt number cannot overflow into a negative delay")
    void resistsOverflow() {
        BackoffPolicy policy = policy(Duration.ofSeconds(1), Duration.ofMinutes(5));

        assertThat(policy.delayAfter(Integer.MAX_VALUE)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("attempt 0 or negative falls back to the base delay")
    void handlesNonPositiveAttempt() {
        BackoffPolicy policy = policy(Duration.ofSeconds(5), Duration.ofMinutes(5));

        assertThat(policy.delayAfter(0)).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.delayAfter(-3)).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("nextAttemptAt is in the future by the computed delay")
    void nextAttemptAtIsFuture() {
        BackoffPolicy policy = policy(Duration.ofSeconds(5), Duration.ofMinutes(5));

        long before = System.currentTimeMillis() + Duration.ofSeconds(4).toMillis();
        long target = policy.nextAttemptAt(1).toEpochMilli();
        long after = System.currentTimeMillis() + Duration.ofSeconds(6).toMillis();

        assertThat(target).isBetween(before, after);
    }
}