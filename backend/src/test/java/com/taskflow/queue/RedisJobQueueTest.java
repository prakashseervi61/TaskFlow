package com.taskflow.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.taskflow.config.TaskflowProperties;
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
class RedisJobQueueTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    private RedisJobQueue queue;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        queue = new RedisJobQueue(redisTemplate, properties("taskflow:jobs"));
    }

    @Test
    @DisplayName("enqueue() pushes the job id onto the configured list")
    void enqueuePushesJobId() {
        queue.enqueue(42L);

        verify(listOperations).leftPush("taskflow:jobs", "42");
        assertThat(queue.queueKey()).isEqualTo("taskflow:jobs");
    }

    @Test
    @DisplayName("enqueue() honours a custom queue key")
    void enqueueUsesConfiguredKey() {
        RedisJobQueue custom = new RedisJobQueue(redisTemplate, properties("taskflow:custom"));

        custom.enqueue(7L);

        verify(listOperations).leftPush("taskflow:custom", "7");
    }

    @Test
    @DisplayName("enqueue() falls back to the default key when none is configured")
    void enqueueFallsBackToDefaultKey() {
        RedisJobQueue fallback = new RedisJobQueue(redisTemplate, properties(null));

        fallback.enqueue(1L);

        verify(listOperations).leftPush("taskflow:jobs", "1");
    }

    @Test
    @DisplayName("enqueue() throws QueueUnavailableException when Redis errors")
    void enqueueThrowsWhenRedisIsDown() {
        when(listOperations.leftPush(anyString(), anyString()))
                .thenThrow(new QueryTimeoutException("connection refused"));

        assertThatThrownBy(() -> queue.enqueue(42L))
                .isInstanceOf(QueueUnavailableException.class)
                .hasMessageContaining("42");
    }

    private TaskflowProperties properties(String queueKey) {
        return new TaskflowProperties(
                new TaskflowProperties.Cors(List.of(), List.of()),
                new TaskflowProperties.Queue(queueKey),
                new TaskflowProperties.Execution(0L));
    }
}