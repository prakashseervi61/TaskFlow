package com.taskflow.worker.job;

/** What one attempt did, so the caller knows whether a scheduled retry needs promoting. */
public enum AttemptOutcome {
    /** This worker ran the job and it succeeded. */
    COMPLETED,

    /** The handler failed but attempts remain; the job is QUEUED with a backoff gate. */
    RETRY_SCHEDULED,

    /** Attempts were exhausted; the job is FAILED and dead-lettered. */
    DEAD_LETTERED,

    /** Another worker owned it, or the job was no longer in a claimable state. */
    SKIPPED
}