package com.taskflow.entity;

/**
 * Lifecycle of a job.
 *
 * <pre>
 * PENDING ──execute──▶ QUEUED ──worker claims──▶ RUNNING ──▶ COMPLETED
 *                          │                        │
 *                          │                        └──────▶ FAILED
 *                          │
 *                          └──(publish failed)──▶ PENDING
 * </pre>
 *
 * <p>{@code QUEUED} means the job id has been pushed to the Redis queue and the worker has
 * not picked it up yet, so it can legitimately sit in this state while a worker is down.
 */
public enum JobStatus {
    PENDING,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED
}