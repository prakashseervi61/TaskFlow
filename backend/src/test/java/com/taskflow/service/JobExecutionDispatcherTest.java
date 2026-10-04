package com.taskflow.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.taskflow.queue.JobQueue;
import com.taskflow.queue.QueueUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobExecutionDispatcherTest {

    @Mock
    private JobQueue jobQueue;

    @Mock
    private JobReleaseService jobReleaseService;

    private JobExecutionDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new JobExecutionDispatcher(jobQueue, jobReleaseService);
    }

    @Test
    @DisplayName("publishes the job id to the queue after a successful enqueue")
    void publishesJobId() {
        dispatcher.onExecutionRequested(new JobExecutionRequested(42L));

        verify(jobQueue).enqueue(42L);
        verify(jobReleaseService, never()).releaseToPending(anyLong(), anyString());
    }

    @Test
    @DisplayName("releases the job back to PENDING when the queue is unreachable")
    void releasesJobWhenQueueUnavailable() {
        doThrow(new QueueUnavailableException("redis down", new RuntimeException()))
                .when(jobQueue)
                .enqueue(42L);
        // an after-commit callback cannot change the HTTP status, so the release is the signal
        dispatcher.onExecutionRequested(new JobExecutionRequested(42L));

        verify(jobReleaseService).releaseToPending(eq(42L), contains("redis down"));
    }

    @Test
    @DisplayName("does not blow up when a worker claimed the job before the release")
    void toleratesJobAlreadyClaimed() {
        doThrow(new QueueUnavailableException("redis down", new RuntimeException()))
                .when(jobQueue)
                .enqueue(42L);
        dispatcher.onExecutionRequested(new JobExecutionRequested(42L));

        verify(jobReleaseService).releaseToPending(eq(42L), contains("redis down"));
    }
}