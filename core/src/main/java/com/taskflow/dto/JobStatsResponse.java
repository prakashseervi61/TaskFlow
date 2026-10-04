package com.taskflow.dto;

/**
 * Counts for the dashboard.
 *
 * @param activeCount QUEUED + RUNNING, so the UI knows whether polling for status changes is
 *     worthwhile without loading a page of jobs
 */
public record JobStatsResponse(
        long total,
        long pending,
        long queued,
        long running,
        long completed,
        long failed,
        long retrying,
        long activeCount) {
}