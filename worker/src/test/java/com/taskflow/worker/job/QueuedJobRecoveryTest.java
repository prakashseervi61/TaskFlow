package com.taskflow.worker.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.taskflow.entity.Job;
import com.taskflow.entity.JobStatus;
import com.taskflow.entity.JobType;
import com.taskflow.repository.JobRepository;
import com.taskflow.worker.config.WorkerProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class QueuedJobRecoveryTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobQueueWriter queueWriter;

    @InjectMocks
    private QueuedJobRecovery recovery;

    private WorkerProperties properties(boolean enabled) {
        return new WorkerProperties(1, Duration.ofSeconds(5), enabled, "boom");
    }

    @Test
    @DisplayName("re-publishes jobs stranded in QUEUED with no backoff pending")
    void republishesQueuedJobs() {
        when(jobRepository.findByStatus(JobStatus.QUEUED)).thenReturn(List.of(job(1L, null), job(2L, null)));
        when(queueWriter.enqueueSafely(anyLong())).thenReturn(true);
        recovery = new QueuedJobRecovery(jobRepository, queueWriter, properties(true));

        assertThat(recovery.recoverIfEnabled()).isEqualTo(2);

        verify(queueWriter).enqueueSafely(1L);
        verify(queueWriter).enqueueSafely(2L);
    }

    @Test
    @DisplayName("puts a job still inside its backoff window back in the schedule")
    void reschedulesWaitingRetries() {
        Job waiting = job(3L, Instant.now().plusSeconds(60));
        when(jobRepository.findByStatus(JobStatus.QUEUED)).thenReturn(List.of(waiting));
        recovery = new QueuedJobRecovery(jobRepository, queueWriter, properties(true));

        assertThat(recovery.recoverIfEnabled()).isEqualTo(1);

        verify(queueWriter).schedule(org.mockito.ArgumentMatchers.eq(3L), any(Instant.class));
        // it must not go straight onto the queue, or the backoff would be ignored
        verify(queueWriter, never()).enqueueSafely(3L);
    }

    @Test
    @DisplayName("does nothing when no jobs are queued")
    void doesNothingWhenQueueEmpty() {
        when(jobRepository.findByStatus(JobStatus.QUEUED)).thenReturn(List.of());
        recovery = new QueuedJobRecovery(jobRepository, queueWriter, properties(true));

        assertThat(recovery.recoverIfEnabled()).isZero();

        verify(queueWriter, never()).enqueueSafely(anyLong());
    }

    @Test
    @DisplayName("skips recovery when it is disabled")
    void respectsDisabledFlag() {
        recovery = new QueuedJobRecovery(jobRepository, queueWriter, properties(false));

        recovery.recoverIfEnabled();

        verify(jobRepository, never()).findByStatus(org.mockito.ArgumentMatchers.any());
        verify(queueWriter, never()).enqueueSafely(anyLong());
    }

    @Test
    @DisplayName("carries on when Redis refuses a job")
    void continuesWhenRedisUnavailable() {
        when(jobRepository.findByStatus(JobStatus.QUEUED)).thenReturn(List.of(job(1L, null), job(2L, null)));
        when(queueWriter.enqueueSafely(1L)).thenReturn(false);
        when(queueWriter.enqueueSafely(2L)).thenReturn(true);
        recovery = new QueuedJobRecovery(jobRepository, queueWriter, properties(true));

        assertThat(recovery.recoverIfEnabled()).isEqualTo(1);

        verify(queueWriter).enqueueSafely(2L);
    }

    private Job job(Long id, Instant nextAttemptAt) {
        Job job = new Job();
        job.setId(id);
        job.setName("job-" + id);
        job.setDescription("description");
        job.setStatus(JobStatus.QUEUED);
        job.setJobType(JobType.SIMULATED);
        job.setAttemptCount(1);
        job.setMaxAttempts(3);
        job.setCreatedAt(Instant.now());
        job.setNextAttemptAt(nextAttemptAt);
        return job;
    }
}