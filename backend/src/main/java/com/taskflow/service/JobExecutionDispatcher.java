package com.taskflow.service;

import com.taskflow.queue.JobQueue;
import com.taskflow.queue.QueueUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reuses the existing {@link JobExecutionRequested} event to hand a job to the queue.
 *
 * <p>Listening with {@link TransactionPhase#AFTER_COMMIT} matters twice over: the job row is
 * guaranteed committed before it becomes visible in Redis, and a worker that picks it up
 * immediately always finds the row.
 *
 * <p>An after-commit callback cannot change the HTTP response, so a queue outage is made
 * visible in the data instead: the job returns to PENDING with a reason and the user can run
 * it again.
 */
@Component
public class JobExecutionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionDispatcher.class);

    private final JobQueue jobQueue;
    private final JobReleaseService jobReleaseService;

    public JobExecutionDispatcher(JobQueue jobQueue, JobReleaseService jobReleaseService) {
        this.jobQueue = jobQueue;
        this.jobReleaseService = jobReleaseService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExecutionRequested(JobExecutionRequested event) {
        Long jobId = event.jobId();
        try {
            jobQueue.enqueue(jobId);
            log.info("Job {} published to the queue", jobId);
        } catch (QueueUnavailableException ex) {
            jobReleaseService.releaseToPending(jobId, ex.getMessage());
        }
    }
}
