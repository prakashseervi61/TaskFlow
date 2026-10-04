package com.taskflow.worker.job;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Promotes retried jobs whose backoff has elapsed from the scheduled sorted set onto the main
 * list.
 *
 * <p>A job waiting out a backoff lives in Redis, not in a consumer slot, so a burst of failing
 * jobs with long delays costs nothing.
 */
@Component
public class RetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetryScheduler.class);

    private static final int MAX_PROMOTIONS_PER_TICK = 100;

    private final JobQueueWriter queueWriter;

    public RetryScheduler(JobQueueWriter queueWriter) {
        this.queueWriter = queueWriter;
    }

    /** Runs every second; Redis does the date arithmetic in {@code rangeByScore}. */
    @Scheduled(fixedDelayString = "${worker.retry-scan-interval-ms:1000}")
    public void promoteDueRetries() {
        try {
            Set<String> due = queueWriter.dueScheduled(System.currentTimeMillis(), MAX_PROMOTIONS_PER_TICK);
            for (String jobId : due) {
                if (queueWriter.promoteScheduled(jobId)) {
                    log.info("Retry for job {} is due, promoted to the queue", jobId);
                }
            }
        } catch (DataAccessException ex) {
            // Redis is down: the jobs are still recorded in PostgreSQL as QUEUED and
            // QueuedJobRecovery re-publishes them when the worker or Redis comes back.
            log.warn("Could not promote due retries ({}), will retry", ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Unexpected error while promoting retries", ex);
        }
    }
}