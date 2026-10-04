package com.taskflow.exception;

/** Thrown when a job id does not exist. Mapped to HTTP 404. */
public class JobNotFoundException extends RuntimeException {

    private final Long jobId;

    public JobNotFoundException(Long jobId) {
        super("Job with id " + jobId + " was not found");
        this.jobId = jobId;
    }

    public Long getJobId() {
        return jobId;
    }
}