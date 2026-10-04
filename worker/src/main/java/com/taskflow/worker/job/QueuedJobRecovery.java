package com.taskflow.worker.job;

import com.taskflow.entity.Job;
import com.taskflow.entity.JobStatus;
import com.taskflow.repository.JobRepository;
import com.taskflow.worker.config.WorkerProperties;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Re-publishes work that PostgreSQL says is waiting but Redis does not.
 *
 * <p>Two ways a job can end up in that state:
 *
 * <ul>
 *   <li>A blocking pop removed the id before the work was done and the worker then died, so
 *       nothing points at the job any more.
 *   <li>A scheduled retry was promoted off the sorted set and the worker died before the push
 *       landed.
 * </ul>
 *
 * <p>Re-publishing is safe because the worker's conditional claim lets exactly one worker act
 * on a given id.
 */
@Component
public class QueuedJobRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(QueuedJobRecovery.class);

    private final JobRepository jobRepository;
    private final JobQueueWriter queueWriter;
    private final WorkerProperties properties;

    public QueuedJobRecovery(JobRepository jobRepository, JobQueueWriter queueWriter, WorkerProperties properties) {
        this.jobRepository = jobRepository;
        this.queueWriter = queueWriter;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        recoverIfEnabled();
    }

    /** @return how many jobs were re-published, or -1 when recovery is disabled */
    public int recoverIfEnabled() {
        if (!properties.recoverQueuedOnStartup()) {
            log.info("Startup recovery of QUEUED jobs is disabled");
            return -1;
        }
        return recover();
    }

    /** @return how many jobs were re-published */
    public int recover() {
        List<Job> queued = jobRepository.findByStatus(JobStatus.QUEUED);
        if (queued.isEmpty()) {
            return 0;
        }

        int requeued = 0;
        int deferred = 0;
        for (Job job : queued) {
            if (job.isWaitingForRetry()) {
                // Still inside its backoff window: put it back in the schedule, not the queue.
                deferred += reschedule(job) ? 1 : 0;
            } else if (queueWriter.enqueueSafely(job.getId())) {
                requeued++;
            }
        }

        log.info(
                "Startup recovery: re-published {} job(s), rescheduled {} waiting on backoff",
                requeued,
                deferred);
        return requeued + deferred;
    }

    /** Re-adds a job to the sorted set at its recorded retry instant. */
    private boolean reschedule(Job job) {
        Instant runAt = job.getNextAttemptAt() == null ? Instant.now() : job.getNextAttemptAt();
        try {
            queueWriter.schedule(job.getId(), runAt);
            return true;
        } catch (RuntimeException ex) {
            log.error("Could not reschedule job {}: {}", job.getId(), ex.getMessage());
            return false;
        }
    }
}