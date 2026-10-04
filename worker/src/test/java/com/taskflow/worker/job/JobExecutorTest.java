package com.taskflow.worker.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.taskflow.config.RetryProperties;
import com.taskflow.config.TaskflowProperties;
import com.taskflow.entity.Job;
import com.taskflow.entity.JobStatus;
import com.taskflow.entity.JobType;
import com.taskflow.handler.JobExecutionContext;
import com.taskflow.handler.JobHandler;
import com.taskflow.retry.BackoffPolicy;
import com.taskflow.repository.JobRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Worker side of the lifecycle: claim, run, then settle into COMPLETED / retry / dead letter. */
@ExtendWith(MockitoExtension.class)
class JobExecutorTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobQueueWriter queueWriter;

    private JobExecutor executor;
    private AtomicReference<JobExecutionContext> captured;

    @BeforeEach
    void setUp() {
        captured = new AtomicReference<>();
        // a succeeding handler for SIMULATED, a failing one for FAIL
        executor = executorFor(List.of(
                new StubHandler(JobType.SIMULATED, null),
                new StubHandler(JobType.FAIL, new IllegalStateException("boom"))));
    }

    private JobExecutor executorFor(List<JobHandler> handlers) {
        TaskflowProperties properties = new TaskflowProperties(
                new TaskflowProperties.Cors(List.of()),
                new TaskflowProperties.Queue("taskflow:jobs"),
                new TaskflowProperties.Execution(0L));
        BackoffPolicy backoff =
                new BackoffPolicy(new RetryProperties(3, Duration.ofSeconds(5), Duration.ofMinutes(5)));
        return new JobExecutor(jobRepository, handlers, backoff, queueWriter, properties);
    }

    @Test
    @DisplayName("execute() completes a job whose handler succeeds")
    void completesSuccessfulJob() {
        when(jobRepository.claimForExecution(eq(1L), eq(JobStatus.QUEUED), eq(JobStatus.RUNNING), any()))
                .thenReturn(1);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job(1L, JobType.SIMULATED, 1, 3)));
        when(jobRepository.markCompleted(eq(1L), eq(JobStatus.RUNNING), eq(JobStatus.COMPLETED), any()))
                .thenReturn(1);

        assertThat(executor.execute(1L)).isEqualTo(AttemptOutcome.COMPLETED);

        verify(jobRepository)
                .markCompleted(eq(1L), eq(JobStatus.RUNNING), eq(JobStatus.COMPLETED), any(Instant.class));
        verify(queueWriter, never()).markDeadLettered(anyLong());
    }

    @Test
    @DisplayName("execute() hands the attempt number and payload to the handler")
    void passesContextToHandler() {
        JobExecutor capturing = executorFor(List.of(new CapturingHandler(JobType.SIMULATED, captured)));

        Job job = job(1L, JobType.SIMULATED, 2, 3);
        job.setPayload(Map.of("region", "eu"));
        when(jobRepository.claimForExecution(eq(1L), eq(JobStatus.QUEUED), eq(JobStatus.RUNNING), any()))
                .thenReturn(1);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        capturing.execute(1L);

        JobExecutionContext context = captured.get();
        assertThat(context.attempt()).isEqualTo(2);
        assertThat(context.attemptsLeft()).isEqualTo(1);
        assertThat(context.maxAttempts()).isEqualTo(3);
        assertThat(context.payload()).containsEntry("region", "eu");
        assertThat(context.status()).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    @DisplayName("execute() schedules a retry while attempts remain")
    void schedulesRetryWhileAttemptsRemain() {
        when(jobRepository.claimForExecution(eq(2L), eq(JobStatus.QUEUED), eq(JobStatus.RUNNING), any()))
                .thenReturn(1);
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job(2L, JobType.FAIL, 1, 3)));
        when(jobRepository.scheduleRetry(
                        eq(2L),
                        eq(JobStatus.RUNNING),
                        eq(JobStatus.QUEUED),
                        contains("boom"),
                        any(Instant.class),
                        any(Instant.class)))
                .thenReturn(1);

        assertThat(executor.execute(2L)).isEqualTo(AttemptOutcome.RETRY_SCHEDULED);

        verify(queueWriter).schedule(eq(2L), any(Instant.class));
        verify(queueWriter, never()).markDeadLettered(anyLong());
        verify(jobRepository, never()).markFailed(anyLong(), any(), any(), anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("execute() dead-letters the job once attempts are exhausted")
    void deadLettersWhenAttemptsExhausted() {
        when(jobRepository.claimForExecution(eq(3L), eq(JobStatus.QUEUED), eq(JobStatus.RUNNING), any()))
                .thenReturn(1);
        when(jobRepository.findById(3L)).thenReturn(Optional.of(job(3L, JobType.FAIL, 3, 3)));
        when(jobRepository.markFailed(eq(3L), eq(JobStatus.RUNNING), eq(JobStatus.FAILED), contains("boom"), any()))
                .thenReturn(1);

        assertThat(executor.execute(3L)).isEqualTo(AttemptOutcome.DEAD_LETTERED);

        verify(queueWriter).markDeadLettered(3L);
        verify(queueWriter, never()).schedule(anyLong(), any(Instant.class));
    }

    @Test
    @DisplayName("execute() skips a job another worker already claimed (duplicate delivery)")
    void skipsAlreadyClaimedJob() {
        // claim returning 0 is what makes duplicate execution impossible
        when(jobRepository.claimForExecution(eq(4L), eq(JobStatus.QUEUED), eq(JobStatus.RUNNING), any()))
                .thenReturn(0);

        assertThat(executor.execute(4L)).isEqualTo(AttemptOutcome.SKIPPED);

        verify(jobRepository, never()).findById(anyLong());
        verify(jobRepository, never()).markCompleted(anyLong(), any(), any(), any(Instant.class));
    }

    @Test
    @DisplayName("execute() returns SKIPPED when the job vanished after being claimed")
    void skipsVanishedJob() {
        when(jobRepository.claimForExecution(eq(5L), eq(JobStatus.QUEUED), eq(JobStatus.RUNNING), any()))
                .thenReturn(1);
        when(jobRepository.findById(5L)).thenReturn(Optional.empty());

        assertThat(executor.execute(5L)).isEqualTo(AttemptOutcome.SKIPPED);
        verify(queueWriter, never()).markDeadLettered(anyLong());
    }

    @Test
    @DisplayName("execute() fails terminally on a job type with no registered handler")
    void failsTerminallyOnUnknownType() {
        // only SIMULATED and FAIL are registered, so PING has no handler
        when(jobRepository.claimForExecution(eq(6L), eq(JobStatus.QUEUED), eq(JobStatus.RUNNING), any()))
                .thenReturn(1);
        when(jobRepository.findById(6L)).thenReturn(Optional.of(job(6L, JobType.PING, 1, 3)));

        assertThat(executor.execute(6L)).isEqualTo(AttemptOutcome.DEAD_LETTERED);

        verify(jobRepository)
                .markFailed(eq(6L), eq(JobStatus.RUNNING), eq(JobStatus.FAILED), contains("No handler"), any());
        // a condition that cannot change must not be retried
        verify(jobRepository, never()).scheduleRetry(anyLong(), any(), any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("execute() still settles the job when Redis rejects the dead-letter push")
    void survivesRedisOutageOnDeadLetter() {
        when(jobRepository.claimForExecution(eq(7L), eq(JobStatus.QUEUED), eq(JobStatus.RUNNING), any()))
                .thenReturn(1);
        when(jobRepository.findById(7L)).thenReturn(Optional.of(job(7L, JobType.FAIL, 3, 3)));
        doThrow(new RuntimeException("redis down")).when(queueWriter).markDeadLettered(7L);

        assertThat(executor.execute(7L)).isEqualTo(AttemptOutcome.DEAD_LETTERED);

        verify(jobRepository).markFailed(eq(7L), eq(JobStatus.RUNNING), eq(JobStatus.FAILED), contains("boom"), any());
    }

    @Test
    @DisplayName("execute() still schedules the retry when Redis is unavailable")
    void survivesRedisOutageOnRetry() {
        when(jobRepository.claimForExecution(eq(8L), eq(JobStatus.QUEUED), eq(JobStatus.RUNNING), any()))
                .thenReturn(1);
        when(jobRepository.findById(8L)).thenReturn(Optional.of(job(8L, JobType.FAIL, 1, 3)));
        when(jobRepository.scheduleRetry(
                        eq(8L), eq(JobStatus.RUNNING), eq(JobStatus.QUEUED), contains("boom"), any(), any()))
                .thenReturn(1);
        doThrow(new RuntimeException("redis down")).when(queueWriter).schedule(eq(8L), any(Instant.class));

        assertThat(executor.execute(8L)).isEqualTo(AttemptOutcome.RETRY_SCHEDULED);

        verify(jobRepository).scheduleRetry(eq(8L), eq(JobStatus.RUNNING), eq(JobStatus.QUEUED), contains("boom"), any(), any());
    }

    private Job job(Long id, JobType type, int attemptCount, int maxAttempts) {
        Job job = new Job();
        job.setId(id);
        job.setName("job-" + id);
        job.setDescription("description");
        job.setStatus(JobStatus.RUNNING);
        job.setJobType(type);
        job.setAttemptCount(attemptCount);
        job.setMaxAttempts(maxAttempts);
        job.setCreatedAt(Instant.now());
        return job;
    }

    /** Handler with a fixed outcome, so the retry path can be driven deterministically. */
    private record StubHandler(JobType type, RuntimeException failure) implements JobHandler {

        @Override
        public void execute(JobExecutionContext context) {
            if (failure != null) {
                throw failure;
            }
        }
    }

    /** Records the context it was handed so the test can assert on it. */
    private record CapturingHandler(JobType type, AtomicReference<JobExecutionContext> sink) implements JobHandler {

        @Override
        public void execute(JobExecutionContext context) {
            sink.set(context);
        }
    }
}