package com.taskflow.worker.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.taskflow.config.TaskflowProperties;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RedisJobQueueReaderTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    private RedisJobQueueReader reader;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        reader = new RedisJobQueueReader(redisTemplate, properties("taskflow:jobs"), redisProperties(Duration.ofSeconds(30)));
    }

    @Test
    @DisplayName("poll() blocks on the configured list and returns the job id")
    void pollReturnsJobId() {
        when(listOperations.rightPop("taskflow:jobs", Duration.ofSeconds(5))).thenReturn("42");

        Long jobId = reader.poll(Duration.ofSeconds(5));

        assertThat(jobId).isEqualTo(42L);
    }

    @Test
    @DisplayName("poll() returns null when nothing arrives before the timeout")
    void pollReturnsNullOnTimeout() {
        when(listOperations.rightPop(anyString(), any(Duration.class))).thenReturn(null);

        assertThat(reader.poll(Duration.ofSeconds(5))).isNull();
    }

    @Test
    @DisplayName("poll() throws QueueUnavailableException when Redis errors")
    void pollThrowsWhenRedisIsDown() {
        when(listOperations.rightPop(anyString(), any(Duration.class)))
                .thenThrow(new QueryTimeoutException("connection refused"));

        assertThatThrownBy(() -> reader.poll(Duration.ofSeconds(5)))
                .isInstanceOf(QueueUnavailableException.class)
                .hasMessageContaining("taskflow:jobs");
    }

    @Test
    @DisplayName("poll() rejects a malformed queue entry instead of looping on it")
    void pollRejectsMalformedEntry() {
        when(listOperations.rightPop(anyString(), any(Duration.class))).thenReturn("not-a-number");

        assertThatThrownBy(() -> reader.poll(Duration.ofSeconds(5)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("poll() honours a custom queue key")
    void pollUsesConfiguredKey() {
        RedisJobQueueReader custom =
                new RedisJobQueueReader(redisTemplate, properties("taskflow:other"), redisProperties(Duration.ofSeconds(30)));
        when(listOperations.rightPop("taskflow:other", Duration.ofSeconds(2))).thenReturn("9");

        assertThat(custom.poll(Duration.ofSeconds(2))).isEqualTo(9L);
    }

    @Test
    @DisplayName("poll() shortens a poll timeout that would outlast the Redis read timeout")
    void capsPollTimeoutToReadTimeout() {
        // read timeout 2s with 1s margin leaves 1s of usable blocking time
        RedisJobQueueReader capped =
                new RedisJobQueueReader(redisTemplate, properties("taskflow:jobs"), redisProperties(Duration.ofSeconds(2)));

        assertThat(capped.capTimeout(Duration.ofSeconds(30))).isEqualTo(Duration.ofSeconds(1));
        assertThat(capped.capTimeout(Duration.ofMillis(500))).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    @DisplayName("poll() uses the capped timeout for the blocking read")
    void pollUsesCappedTimeout() {
        RedisJobQueueReader capped =
                new RedisJobQueueReader(redisTemplate, properties("taskflow:jobs"), redisProperties(Duration.ofSeconds(2)));
        when(listOperations.rightPop("taskflow:jobs", Duration.ofSeconds(1))).thenReturn("3");

        assertThat(capped.poll(Duration.ofSeconds(30))).isEqualTo(3L);
    }

    private RedisProperties redisProperties(Duration readTimeout) {
        RedisProperties props = new RedisProperties();
        props.setTimeout(readTimeout);
        return props;
    }

    private TaskflowProperties properties(String queueKey) {
        return new TaskflowProperties(
                new TaskflowProperties.Cors(List.of()),
                new TaskflowProperties.Queue(queueKey),
                new TaskflowProperties.Execution(0L));
    }
}