package com.taskflow.queue;

/**
 * The queue could not be reached, so the job could not be handed to a worker. The API turns
 * this into a "released back to PENDING" outcome instead of a 500.
 */
public class QueueUnavailableException extends RuntimeException {

    public QueueUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}