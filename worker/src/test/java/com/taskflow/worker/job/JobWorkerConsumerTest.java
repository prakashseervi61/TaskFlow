package com.taskflow.worker.job;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.taskflow.worker.config.WorkerProperties;
import java.time.Duration;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobWorkerConsumerTest {

    private final RedisJobQueueReader queueReader = mock(RedisJobQueueReader.class);
    private final JobExecutor simulator = mock(JobExecutor.class);

    /** Runs work on the calling thread so assertions stay deterministic. */
    private final Executor directExecutor = Runnable::run;

    private JobWorkerConsumer consumer(WorkerProperties properties) {
        return new JobWorkerConsumer(queueReader, simulator, directExecutor, properties);
    }

    private WorkerProperties properties(Duration timeout) {
        return new WorkerProperties(1, timeout, false, "boom");
    }

    @Test
    @DisplayName("dispatches every polled job id to the executor")
    void dispatchesPolledJobs() throws Exception {
        when(queueReader.poll(any()))
                .thenReturn(1L)
                .thenReturn(2L)
                .thenReturn(null);
        JobWorkerConsumer consumer = consumer(properties(Duration.ofMillis(20)));

        consumer.start();
        try {
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(simulator, times(2)).execute(anyLong()));
            verify(simulator).execute(1L);
            verify(simulator).execute(2L);
        } finally {
            consumer.stop();
        }
    }

    @Test
    @DisplayName("keeps polling and survives when the queue is unavailable")
    void survivesQueueOutage() throws Exception {
        when(queueReader.poll(any()))
                .thenThrow(new QueueUnavailableException("redis down", new RuntimeException()))
                .thenReturn(9L)
                .thenReturn(null);
        JobWorkerConsumer consumer = consumer(properties(Duration.ofMillis(20)));

        consumer.start();
        try {
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> verify(simulator).execute(9L));
        } finally {
            consumer.stop();
        }
    }

    @Test
    @DisplayName("a crash while executing one job does not stop the consumer")
    void survivesExecutorCrash() throws Exception {
        doThrow(new IllegalStateException("boom")).when(simulator).execute(1L);
        when(queueReader.poll(any()))
                .thenReturn(1L)
                .thenReturn(2L)
                .thenReturn(null);
        JobWorkerConsumer consumer = consumer(properties(Duration.ofMillis(20)));

        consumer.start();
        try {
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(simulator).execute(2L));
        } finally {
            consumer.stop();
        }
    }

    @Test
    @DisplayName("stop() ends the consume loop")
    void stopEndsLoop() throws Exception {
        when(queueReader.poll(any())).thenReturn(null);
        JobWorkerConsumer consumer = consumer(properties(Duration.ofMillis(10)));

        consumer.start();
        // let the loop poll at least once before shutting it down
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> verify(queueReader, atLeastOnce()).poll(any(Duration.class)));
        consumer.stop();

        verify(simulator, never()).execute(anyLong());
    }
}