package com.taskflow.dto;

import com.taskflow.entity.Job;
import com.taskflow.entity.JobStatus;
import com.taskflow.entity.JobType;
import java.time.Instant;
import java.util.Map;

public record JobResponse(
        Long id,
        String name,
        String description,
        JobStatus status,
        JobType jobType,
        Map<String, Object> payload,
        Instant createdAt,
        Instant queuedAt,
        Instant startedAt,
        Instant completedAt,
        Instant nextAttemptAt,
        int attemptCount,
        int maxAttempts,
        int attemptsLeft,
        String failureReason,
        String lastError) {

    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getId(),
                job.getName(),
                job.getDescription(),
                job.getStatus(),
                job.getJobType(),
                job.getPayload(),
                job.getCreatedAt(),
                job.getQueuedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getNextAttemptAt(),
                job.getAttemptCount(),
                job.getMaxAttempts(),
                Math.max(0, job.getMaxAttempts() - job.getAttemptCount()),
                job.getFailureReason(),
                job.getLastError());
    }
}