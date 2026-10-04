package com.taskflow.queue;

/**
 * Where queued job ids go. The API only enqueues work that is due now; the worker owns the
 * retry schedule and the dead-letter queue. Keeping the interface this small means a different
 * broker can be introduced without touching the REST layer.
 */
public interface JobQueue {

    /**
     * Makes a job id available to a worker.
     *
     * @throws QueueUnavailableException when the backing queue cannot be reached
     */
    void enqueue(Long jobId);
}
