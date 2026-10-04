package com.taskflow.worker.job;

import com.taskflow.config.TaskflowProperties;
import com.taskflow.entity.Job;
import com.taskflow.entity.JobStatus;
import com.taskflow.handler.JobExecutionContext;
import com.taskflow.handler.JobHandler;
import com.taskflow.retry.BackoffPolicy;
import com.taskflow.repository.JobRepository;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes one attempt of a queued job and decides what happens next.
 *
 * <pre>
 * QUEUED ──claim(attempt+1)──▶ RUNNING ──┬─▶ COMPLETED
 *                                        └─▶ FAILED, then either
 *                                             ├─ attempts left  → QUEUED + backoff
 *                                             └─ exhausted      → FAILED + dead letter
 * </pre>
 *
 * <p>Still a simulation — a handler stands in for real work — but the attempt accounting, the
 * claim handshake and the transaction boundaries are the real thing.
 */
@Service
public class JobExecutor {

    private static final Logger log = LoggerFactory.getLogger(JobExecutor.class);

    private static final int MAX_ERROR_LENGTH = 1000;

    private final JobRepository jobRepository;
    private final Map<com.taskflow.entity.JobType, JobHandler> handlers;
    private final BackoffPolicy backoffPolicy;
    private final JobQueueWriter queueWriter;

    public JobExecutor(
            JobRepository jobRepository,
            List<JobHandler> jobHandlers,
            BackoffPolicy backoffPolicy,
            JobQueueWriter queueWriter,
            TaskflowProperties properties) {
        this.jobRepository = jobRepository;
        this.backoffPolicy = backoffPolicy;
        this.queueWriter = queueWriter;

        Map<com.taskflow.entity.JobType, JobHandler> registry = new EnumMap<>(com.taskflow.entity.JobType.class);
        for (JobHandler handler : jobHandlers) {
            registry.put(handler.type(), handler);
        }
        this.handlers = Map.copyOf(registry);

        log.info(
                "Worker job handlers: {}{}",
                registry.keySet(),
                properties.execution().simulatedDurationMs() >= 0 ? "" : "");
    }

    /**
     * Runs one attempt.
     *
     * @return the outcome, so the caller can decide whether a retry needs promoting
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AttemptOutcome execute(Long jobId) {
        if (!claim(jobId)) {
            log.info("Job {} was not claimed (already running, finished or gone) — skipping", jobId);
            return AttemptOutcome.SKIPPED;
        }

        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Job {} vanished right after being claimed", jobId);
            return AttemptOutcome.SKIPPED;
        }

        JobHandler handler = handlers.get(job.getJobType());
        if (handler == null) {
            // Only reachable for a row written by a newer build; fail terminally rather than
            // retrying forever on a condition that will not change.
            String error = "No handler registered for job type " + job.getJobType();
            log.error("Job {}: {}", jobId, error);
            jobRepository.markFailed(jobId, JobStatus.RUNNING, JobStatus.FAILED, error, Instant.now());
            return AttemptOutcome.DEAD_LETTERED;
        }

        JobExecutionContext context = JobExecutionContext.of(
                jobId, job.getJobType(), job.getPayload(), job.getAttemptCount(), job.getMaxAttempts());

        try {
            handler.execute(context);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failAndMaybeRetry(job, "Execution interrupted", context.attempt());
        } catch (Exception ex) {
            return failAndMaybeRetry(job, describe(ex), context.attempt());
        }

        int updated = jobRepository.markCompleted(
                jobId, JobStatus.RUNNING, JobStatus.COMPLETED, Instant.now());
        if (updated == 0) {
            log.warn("Job {} completed but was no longer RUNNING — ignoring", jobId);
            return AttemptOutcome.SKIPPED;
        }
        log.info("Job {} completed on attempt {}", jobId, context.attempt());
        return AttemptOutcome.COMPLETED;
    }

    /**
     * Settles a failed attempt. While attempts remain the job goes back to QUEUED with a backoff
     * gate and is scheduled in Redis; once they are exhausted it becomes terminally FAILED and is
     * dead-lettered.
     */
    private AttemptOutcome failAndMaybeRetry(Job job, String error, int attempt) {
        Long jobId = job.getId();

        if (attempt < job.getMaxAttempts()) {
            Instant nextAttemptAt = backoffPolicy.nextAttemptAt(attempt);
            int updated = jobRepository.scheduleRetry(
                    jobId,
                    JobStatus.RUNNING,
                    JobStatus.QUEUED,
                    error,
                    Instant.now(),
                    nextAttemptAt);
            if (updated == 0) {
                log.warn("Job {} failed but was no longer RUNNING — ignoring", jobId);
                return AttemptOutcome.SKIPPED;
            }
            // Outside the transaction on purpose: the row is committed by the time this runs.
            scheduleRetry(jobId, nextAttemptAt);
            log.warn(
                    "Job {} attempt {}/{} failed: {} — retrying at {}",
                    jobId,
                    attempt,
                    job.getMaxAttempts(),
                    error,
                    nextAttemptAt);
            return AttemptOutcome.RETRY_SCHEDULED;
        }

        jobRepository.markFailed(jobId, JobStatus.RUNNING, JobStatus.FAILED, error, Instant.now());
        deadLetter(jobId);
        log.error("Job {} failed permanently after {} attempt(s): {}", jobId, attempt, error);
        return AttemptOutcome.DEAD_LETTERED;
    }

    private void scheduleRetry(Long jobId, Instant nextAttemptAt) {
        try {
            queueWriter.schedule(jobId, nextAttemptAt);
        } catch (RuntimeException ex) {
            // The row is QUEUED with a backoff gate; QueuedJobRecovery will pick it up.
            log.error("Could not schedule retry for job {} in Redis: {}", jobId, ex.getMessage());
        }
    }

    private void deadLetter(Long jobId) {
        try {
            queueWriter.markDeadLettered(jobId);
        } catch (RuntimeException ex) {
            // The row is already terminally FAILED and the API lists dead letters from
            // PostgreSQL, so only the Redis-side view is affected.
            log.error("Could not record job {} in the dead letter queue: {}", jobId, ex.getMessage());
        }
    }

    /**
     * Ownership handshake. A single conditional UPDATE decides which worker runs the job and
     * counts the attempt, so duplicate queue entries and multiple workers cannot execute it twice.
     */
    private boolean claim(Long jobId) {
        int claimed =
                jobRepository.claimForExecution(jobId, JobStatus.QUEUED, JobStatus.RUNNING, Instant.now());
        if (claimed == 0) {
            return false;
        }
        log.info("Job {} claimed: QUEUED -> RUNNING", jobId);
        return true;
    }

    private static String describe(Exception ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        String full = ex.getClass().getSimpleName() + ": " + message;
        return full.length() <= MAX_ERROR_LENGTH ? full : full.substring(0, MAX_ERROR_LENGTH);
    }
}