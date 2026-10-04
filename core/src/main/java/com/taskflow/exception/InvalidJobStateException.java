package com.taskflow.exception;

import com.taskflow.entity.JobStatus;

/**
 * Thrown when a job cannot move to the requested state, e.g. running a job that is
 * already RUNNING or COMPLETED. Mapped to HTTP 409.
 */
public class InvalidJobStateException extends RuntimeException {

    private final Long jobId;
    private final JobStatus currentStatus;
    private final JobStatus attemptedStatus;

    public InvalidJobStateException(Long jobId, JobStatus currentStatus, JobStatus attemptedStatus) {
        super("Job " + jobId + " is in state " + currentStatus + " and cannot transition to " + attemptedStatus);
        this.jobId = jobId;
        this.currentStatus = currentStatus;
        this.attemptedStatus = attemptedStatus;
    }

    public Long getJobId() {
        return jobId;
    }

    public JobStatus getCurrentStatus() {
        return currentStatus;
    }

    public JobStatus getAttemptedStatus() {
        return attemptedStatus;
    }
}