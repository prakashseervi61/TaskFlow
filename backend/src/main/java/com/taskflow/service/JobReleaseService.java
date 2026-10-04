package com.taskflow.service;

import com.taskflow.entity.JobStatus;
import com.taskflow.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Releases a job that could not be handed to the queue.
 *
 * <p>Lives in its own bean on purpose: the call happens from an after-commit callback, and the
 * new transaction has to be opened through the Spring proxy, which self-invocation would skip.
 */
@Service
public class JobReleaseService {

    private static final Logger log = LoggerFactory.getLogger(JobReleaseService.class);

    private final JobRepository jobRepository;

    public JobReleaseService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /**
     * Moves QUEUED back to PENDING so the job can be run again.
     *
     * @return true if the job was released, false if a worker had already claimed it
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean releaseToPending(Long jobId, String reason) {
        int released = jobRepository.releaseFromQueue(
                jobId, JobStatus.QUEUED, JobStatus.PENDING, "Queue unavailable: " + reason);

        if (released == 1) {
            log.error("Job {} released back to PENDING, queue unreachable: {}", jobId, reason);
        } else {
            // A worker claimed it before the publish failed; that is a fine outcome.
            log.warn("Job {} left as-is (no longer QUEUED), queue unreachable: {}", jobId, reason);
        }
        return released == 1;
    }
}