package com.taskflow.worker.job;

/** Redis (or whatever backs the queue) could not be reached. The consumer keeps running. */
public class QueueUnavailableException extends RuntimeException {

    public QueueUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}