package com.taskflow.worker.job;

import com.taskflow.worker.config.WorkerProperties;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Long-running loop: poll the queue, hand each job id to the executor, repeat.
 *
 * <p>Redis being down is treated as an expected operational condition: the loop logs it, backs
 * off briefly and keeps polling, so the worker heals on its own when Redis returns and jobs
 * simply wait in the queue meanwhile.
 */
@Component
public class JobWorkerConsumer {

    private static final Logger log = LoggerFactory.getLogger(JobWorkerConsumer.class);

    private static final Duration REDIS_BACKOFF = Duration.ofSeconds(5);

    private final JobQueueReader queueReader;
    private final JobExecutor executor;
    private final Executor jobConsumerExecutor;
    private final Duration pollTimeout;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread loopThread;

    public JobWorkerConsumer(
            JobQueueReader queueReader,
            JobExecutor executor,
            @Qualifier("jobConsumerExecutor") Executor jobConsumerExecutor,
            WorkerProperties workerProperties) {
        this.queueReader = queueReader;
        this.executor = executor;
        this.jobConsumerExecutor = jobConsumerExecutor;
        this.pollTimeout = workerProperties.pollTimeout();

        this.loopThread = new Thread(this::consumeLoop, "job-consumer-loop");
        this.loopThread.setDaemon(false);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        loopThread.start();
        log.info("Worker consumer started, polling every {}", pollTimeout);
    }

    @PreDestroy
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Worker consumer stopping");
            loopThread.interrupt();
        }
    }

    private void consumeLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                Long jobId = queueReader.poll(pollTimeout);
                if (jobId != null) {
                    dispatch(jobId);
                }
            } catch (QueueUnavailableException ex) {
                log.warn("Job queue unavailable ({}), retrying in {}", ex.getMessage(), REDIS_BACKOFF);
                sleepQuietly(REDIS_BACKOFF);
            } catch (RuntimeException ex) {
                log.error("Unexpected error in the consume loop", ex);
                sleepQuietly(REDIS_BACKOFF);
            }
        }
    }

    private void dispatch(Long jobId) {
        jobConsumerExecutor.execute(() -> {
            try {
                executor.execute(jobId);
            } catch (RuntimeException ex) {
                // Last line of defence: a crash here must not take the consumer thread down.
                log.error("Job {} failed unexpectedly", jobId, ex);
            }
        });
    }

    private void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}