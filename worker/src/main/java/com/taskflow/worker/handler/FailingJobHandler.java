package com.taskflow.worker.handler;

import com.taskflow.entity.JobType;
import com.taskflow.handler.JobExecutionContext;
import com.taskflow.handler.JobHandler;

/**
 * Always throws, so the retry path and the dead-letter queue can be exercised deliberately.
 * Replaces the v0.1 "job name starts with fail:" convention.
 */
public class FailingJobHandler implements JobHandler {

    private final String message;

    public FailingJobHandler(String message) {
        this.message = message;
    }

    @Override
    public JobType type() {
        return JobType.FAIL;
    }

    @Override
    public void execute(JobExecutionContext context) {
        throw new IllegalStateException(message + " (attempt " + context.attempt() + " of "
                + context.maxAttempts() + ")");
    }
}
