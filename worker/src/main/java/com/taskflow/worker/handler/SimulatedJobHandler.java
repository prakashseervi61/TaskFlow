package com.taskflow.worker.handler;

import com.taskflow.entity.JobType;
import com.taskflow.handler.JobExecutionContext;
import com.taskflow.handler.JobHandler;

/** Sleeps for the configured simulated duration, then completes. */
public class SimulatedJobHandler implements JobHandler {

    private final long simulatedDurationMs;

    public SimulatedJobHandler(long simulatedDurationMs) {
        this.simulatedDurationMs = simulatedDurationMs;
    }

    @Override
    public JobType type() {
        return JobType.SIMULATED;
    }

    @Override
    public void execute(JobExecutionContext context) throws InterruptedException {
        if (simulatedDurationMs <= 0) {
            return;
        }
        Thread.sleep(simulatedDurationMs);
    }
}
