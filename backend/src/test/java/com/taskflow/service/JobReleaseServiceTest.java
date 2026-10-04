package com.taskflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.taskflow.entity.JobStatus;
import com.taskflow.repository.JobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobReleaseServiceTest {

    @Mock
    private JobRepository jobRepository;

    @InjectMocks
    private JobReleaseService releaseService;

    @Test
    @DisplayName("releaseToPending() moves a QUEUED job back to PENDING with a reason")
    void releasesQueuedJob() {
        when(jobRepository.releaseFromQueue(eq(7L), eq(JobStatus.QUEUED), eq(JobStatus.PENDING), contains("redis down")))
                .thenReturn(1);

        boolean released = releaseService.releaseToPending(7L, "redis down");

        assertThat(released).isTrue();
        verify(jobRepository)
                .releaseFromQueue(eq(7L), eq(JobStatus.QUEUED), eq(JobStatus.PENDING), contains("Queue unavailable"));
    }

    @Test
    @DisplayName("releaseToPending() reports false when a worker already claimed the job")
    void reportsFalseWhenAlreadyClaimed() {
        when(jobRepository.releaseFromQueue(eq(7L), eq(JobStatus.QUEUED), eq(JobStatus.PENDING), contains("redis down")))
                .thenReturn(0);

        assertThat(releaseService.releaseToPending(7L, "redis down")).isFalse();
    }
}
