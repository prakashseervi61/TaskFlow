package com.taskflow.service;

/**
 * Published inside the execute transaction; the runner only reacts to it once that
 * transaction has committed, so the worker can never read a stale status.
 */
public record JobExecutionRequested(Long jobId) {
}