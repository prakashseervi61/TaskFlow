package com.taskflow.handler;

import com.taskflow.entity.JobType;
import java.util.Map;

/**
 * Does the actual work for one {@link JobType}.
 *
 * <p>Implementations live in the worker module; the interface lives in core so both the API
 * (which validates the requested type) and the worker (which dispatches on it) agree.
 *
 * <p>Throwing is how a handler reports failure — the caller decides whether that attempt is
 * retried or the job is dead-lettered.
 */
public interface JobHandler {

    JobType type();

    /**
     * Executes the job.
     *
     * @param context everything the handler is allowed to know about the attempt
     * @throws Exception to signal failure; the message becomes the job's last error
     */
    void execute(JobExecutionContext context) throws Exception;
}