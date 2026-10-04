package com.taskflow.worker.job;

import java.time.Duration;

/** Reads queued job ids. Kept behind an interface so the transport can change independently. */
public interface JobQueueReader {

    /**
     * Blocks until a job id arrives or the poll timeout expires.
     *
     * @return the job id, or {@code null} when nothing arrived in time
     * @throws QueueUnavailableException when the queue cannot be reached
     */
    Long poll(Duration timeout);
}