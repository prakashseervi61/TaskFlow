package com.taskflow.handler;

import com.taskflow.entity.JobStatus;
import com.taskflow.entity.JobType;
import java.util.Map;

/**
 * Read-only view of one attempt, handed to a {@link JobHandler}.
 *
 * @param jobId         the job being executed
 * @param jobType       handler already selected for this job
 * @param payload       free-form input from the create request, may be empty
 * @param attempt       1-based number of the attempt being made
 * @param maxAttempts   total attempts allowed before the job is dead-lettered
 * @param attemptsLeft  {@code maxAttempts - attempt}, floored at 0
 * @param status        status at the time the attempt started, always {@link JobStatus#RUNNING}
 */
public record JobExecutionContext(
        Long jobId,
        JobType jobType,
        Map<String, Object> payload,
        int attempt,
        int maxAttempts,
        int attemptsLeft,
        JobStatus status) {

    public static JobExecutionContext of(
            Long jobId,
            JobType jobType,
            Map<String, Object> payload,
            int attempt,
            int maxAttempts) {
        return new JobExecutionContext(
                jobId,
                jobType,
                payload == null ? Map.of() : payload,
                attempt,
                maxAttempts,
                Math.max(0, maxAttempts - attempt),
                JobStatus.RUNNING);
    }
}