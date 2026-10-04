package com.taskflow.service;

import com.taskflow.config.RetryProperties;
import com.taskflow.dto.CreateJobRequest;
import com.taskflow.dto.JobPageResponse;
import com.taskflow.dto.JobResponse;
import com.taskflow.dto.JobStatsResponse;
import com.taskflow.entity.Job;
import com.taskflow.entity.JobStatus;
import com.taskflow.entity.JobType;
import com.taskflow.exception.InvalidJobStateException;
import com.taskflow.exception.JobNotFoundException;
import com.taskflow.exception.UnsupportedJobTypeException;
import com.taskflow.repository.JobRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    /** Row requested per page, plus one to detect whether a further page exists. */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 100;

    private final JobRepository jobRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RetryProperties retryProperties;

    public JobService(
            JobRepository jobRepository,
            ApplicationEventPublisher eventPublisher,
            RetryProperties retryProperties) {
        this.jobRepository = jobRepository;
        this.eventPublisher = eventPublisher;
        this.retryProperties = retryProperties;
    }

    @Transactional
    public JobResponse create(CreateJobRequest request) {
        Job job = new Job();
        job.setName(request.name().trim());
        job.setDescription(request.description().trim());
        job.setJobType(parseJobType(request.jobType()));
        job.setPayload(request.payload());
        job.setMaxAttempts(request.maxAttempts() == null
                ? retryProperties.defaultMaxAttempts()
                : request.maxAttempts());
        job.setStatus(JobStatus.PENDING);
        job.setAttemptCount(0);
        job.setCreatedAt(Instant.now());

        Job saved = jobRepository.save(job);
        log.info("Created job {} ({}, type={}, maxAttempts={})",
                saved.getId(), saved.getStatus(), saved.getJobType(), saved.getMaxAttempts());
        return JobResponse.from(saved);
    }

    /**
     * Newest-first keyset page.
     *
     * @param cursor opaque value from a previous page's {@code nextCursor}; null for the first
     *     page
     */
    @Transactional(readOnly = true)
    public JobPageResponse findPage(String cursor, Integer limit) {
        int pageSize = clampPageSize(limit);
        PageCursor pageCursor = PageCursor.decode(cursor);

        // One extra row tells us whether a next page exists without a second count query.
        List<Job> rows = pageCursor == null
                ? jobRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, pageSize + 1))
                : jobRepository.findPageAfter(
                        pageCursor.createdAt(), pageCursor.id(), PageRequest.of(0, pageSize + 1));

        boolean hasMore = rows.size() > pageSize;
        List<JobResponse> items = rows.stream().limit(pageSize).map(JobResponse::from).toList();

        String nextCursor = hasMore
                ? PageCursor.of(rows.get(pageSize - 1).getCreatedAt(), rows.get(pageSize - 1).getId()).encode()
                : null;

        return JobPageResponse.of(items, nextCursor, hasMore, jobRepository.count());
    }

    @Transactional(readOnly = true)
    public JobResponse findById(Long id) {
        return JobResponse.from(requireJob(id));
    }

    @Transactional(readOnly = true)
    public JobStatsResponse stats() {
        long pending = jobRepository.countByStatus(JobStatus.PENDING);
        long queued = jobRepository.countByStatus(JobStatus.QUEUED);
        long running = jobRepository.countByStatus(JobStatus.RUNNING);
        long retrying = jobRepository.countByStatusAndNextAttemptAtIsNotNull(JobStatus.QUEUED);

        return new JobStatsResponse(
                jobRepository.count(),
                pending,
                queued,
                running,
                jobRepository.countByStatus(JobStatus.COMPLETED),
                jobRepository.countByStatus(JobStatus.FAILED),
                retrying,
                queued + running);
    }

    /**
     * Moves a job to QUEUED and publishes its id to the queue once this transaction commits.
     * The worker performs the remaining QUEUED → RUNNING → COMPLETED/FAILED steps.
     *
     * <p>Accepts a PENDING job, or a FAILED one that still has attempts left — that is what
     * makes the Retry button in the UI work.
     */
    @Transactional
    public JobResponse execute(Long id) {
        Job job = requireJob(id);

        int claimed = jobRepository.claimForQueueing(
                id, JobStatus.PENDING, JobStatus.FAILED, JobStatus.QUEUED, Instant.now());
        if (claimed == 0) {
            throw new InvalidJobStateException(id, job.getStatus(), JobStatus.QUEUED);
        }

        // Handled by JobExecutionDispatcher, which only publishes after the commit above.
        eventPublisher.publishEvent(new JobExecutionRequested(id));
        log.info("Job {} queued from status {}", id, job.getStatus());

        return JobResponse.from(requireJob(id));
    }

    /**
     * Puts a terminally failed job back to PENDING with a fresh budget of attempts, as an
     * alternative to letting it sit in the dead-letter queue.
     */
    @Transactional
    public JobResponse requeue(Long id) {
        Job job = requireJob(id);

        int reset = jobRepository.requeueFailed(id, JobStatus.FAILED, JobStatus.PENDING);
        if (reset == 0) {
            throw new InvalidJobStateException(id, job.getStatus(), JobStatus.PENDING);
        }

        log.info("Job {} re-queued with a fresh attempt budget", id);
        return JobResponse.from(requireJob(id));
    }

    /** Terminally failed jobs, i.e. those whose retries were exhausted. */
    @Transactional(readOnly = true)
    public List<JobResponse> deadLettered() {
        return jobRepository.findByStatus(JobStatus.FAILED).stream()
                .map(JobResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long id) {
        Job job = requireJob(id);
        jobRepository.delete(job);
        log.info("Deleted job {}", id);
    }

    private Job requireJob(Long id) {
        return jobRepository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    private JobType parseJobType(String raw) {
        if (raw == null || raw.isBlank()) {
            return JobType.SIMULATED;
        }
        try {
            return JobType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new UnsupportedJobTypeException(raw);
        }
    }

    private int clampPageSize(Integer requested) {
        if (requested == null || requested < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requested, MAX_PAGE_SIZE);
    }
}