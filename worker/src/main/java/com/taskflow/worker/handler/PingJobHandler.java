package com.taskflow.worker.handler;

import com.taskflow.entity.JobType;
import com.taskflow.handler.JobExecutionContext;
import com.taskflow.handler.JobHandler;

/** Completes immediately. Useful for checking the pipeline without waiting. */
public class PingJobHandler implements JobHandler {

    @Override
    public JobType type() {
        return JobType.PING;
    }

    @Override
    public void execute(JobExecutionContext context) {
        // nothing to do
    }
}
