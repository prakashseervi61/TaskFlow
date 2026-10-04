package com.taskflow.entity;

/**
 * Selects which {@link com.taskflow.handler.JobHandler} executes a job.
 *
 * <p>An enum rather than a free-form string so the API can validate a type without depending
 * on the worker module that implements it.
 */
public enum JobType {

    /** Sleeps for {@code taskflow.execution.simulated-duration-ms}, then completes. */
    SIMULATED,

    /** Completes immediately. Useful for checking the pipeline without waiting. */
    PING,

    /** Always fails. Replaces the old "job name starts with fail:" convention. */
    FAIL
}