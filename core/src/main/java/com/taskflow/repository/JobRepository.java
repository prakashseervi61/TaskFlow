package com.taskflow.repository;

import com.taskflow.entity.Job;
import com.taskflow.entity.JobStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Note on the {@code @Modifying} methods below: a custom JPQL update is not covered by
 * {@code SimpleJpaRepository}'s default {@code @Transactional}, so each one declares its own
 * transaction. Without it, calling them outside a transaction (for example from an
 * after-commit callback) fails with {@code TransactionRequiredException}.
 *
 * <p>Every status change is a conditional UPDATE ("... and j.status = :expected"). That makes
 * each transition an atomic claim: concurrent callers race on one row and exactly one wins,
 * which is what keeps duplicate execution impossible.
 */
public interface JobRepository extends JpaRepository<Job, Long> {

    long countByStatus(JobStatus status);

    /** Queued jobs waiting on a retry backoff, i.e. not yet eligible for the worker. */
    long countByStatusAndNextAttemptAtIsNotNull(JobStatus status);

    List<Job> findByStatus(JobStatus status);

    /** First page of the newest-first listing. */
    List<Job> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    /**
     * Keyset page after a cursor. A cursor is the {@code (createdAt, id)} pair of the last row
     * of the previous page; the id tiebreaker keeps rows with identical timestamps from being
     * skipped or repeated when new jobs arrive between requests.
     */
    @Query("""
            select j from Job j
            where j.createdAt < :cursorCreatedAt
               or (j.createdAt = :cursorCreatedAt and j.id < :cursorId)
            order by j.createdAt desc, j.id desc
            """)
    List<Job> findPageAfter(
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    /**
     * Moves PENDING → QUEUED, or re-queues a FAILED job that still has attempts left. Two
     * concurrent Run requests race on this single UPDATE, so a job cannot be queued twice.
     *
     * @return 1 if this caller claimed the job, 0 if it was not eligible
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
               set j.status = :queued,
                   j.queuedAt = :queuedAt,
                   j.failureReason = null,
                   j.completedAt = null,
                   j.nextAttemptAt = null
             where j.id = :id
               and (j.status = :pending
                    or (j.status = :failed and j.attemptCount < j.maxAttempts))
            """)
    int claimForQueueing(
            @Param("id") Long id,
            @Param("pending") JobStatus pending,
            @Param("failed") JobStatus failed,
            @Param("queued") JobStatus queued,
            @Param("queuedAt") Instant queuedAt);

    /**
     * Moves QUEUED → RUNNING and counts the attempt. This is the worker's ownership handshake:
     * with several workers, or with Redis delivering an id twice, only the caller that updates
     * one row may execute it.
     *
     * @return 1 if this worker claimed the job, 0 if another worker already had it
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
               set j.status = :running,
                   j.startedAt = :startedAt,
                   j.attemptCount = j.attemptCount + 1
             where j.id = :id and j.status = :queued
            """)
    int claimForExecution(
            @Param("id") Long id,
            @Param("queued") JobStatus queued,
            @Param("running") JobStatus running,
            @Param("startedAt") Instant startedAt);

    /**
     * Settles a successful attempt.
     *
     * @return 1 if this worker still owned the job
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
               set j.status = :completed,
                   j.completedAt = :completedAt,
                   j.failureReason = null,
                   j.nextAttemptAt = null
             where j.id = :id and j.status = :running
            """)
    int markCompleted(
            @Param("id") Long id,
            @Param("running") JobStatus running,
            @Param("completed") JobStatus completed,
            @Param("completedAt") Instant completedAt);

    /**
     * Settles a failed attempt that still has attempts left: back to QUEUED, waiting for
     * {@code nextAttemptAt}. The caller decides the delay with the backoff policy.
     *
     * @return 1 if this worker still owned the job
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
               set j.status = :queued,
                   j.lastError = :error,
                   j.failureReason = :error,
                   j.completedAt = :completedAt,
                   j.nextAttemptAt = :nextAttemptAt
             where j.id = :id and j.status = :running
            """)
    int scheduleRetry(
            @Param("id") Long id,
            @Param("running") JobStatus running,
            @Param("queued") JobStatus queued,
            @Param("error") String error,
            @Param("completedAt") Instant completedAt,
            @Param("nextAttemptAt") Instant nextAttemptAt);

    /**
     * Settles a failed attempt with no attempts left: terminally FAILED.
     *
     * @return 1 if this worker still owned the job
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
               set j.status = :failed,
                   j.lastError = :error,
                   j.failureReason = :error,
                   j.completedAt = :completedAt,
                   j.nextAttemptAt = null
             where j.id = :id and j.status = :running
            """)
    int markFailed(
            @Param("id") Long id,
            @Param("running") JobStatus running,
            @Param("failed") JobStatus failed,
            @Param("error") String error,
            @Param("completedAt") Instant completedAt);

    /** Returns a job to PENDING after the queue publish failed, so the user can retry. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
               set j.status = :pending,
                   j.queuedAt = null,
                   j.failureReason = :reason,
                   j.nextAttemptAt = null
             where j.id = :id and j.status = :queued
            """)
    int releaseFromQueue(
            @Param("id") Long id,
            @Param("queued") JobStatus queued,
            @Param("pending") JobStatus pending,
            @Param("reason") String reason);

    /**
     * Puts a terminally failed job back to PENDING on request, giving it a fresh budget of
     * attempts.
     *
     * @return 1 if the job was FAILED and has been reset
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
               set j.status = :pending,
                   j.attemptCount = 0,
                   j.lastError = null,
                   j.failureReason = null,
                   j.completedAt = null,
                   j.nextAttemptAt = null
             where j.id = :id and j.status = :failed
            """)
    int requeueFailed(
            @Param("id") Long id,
            @Param("failed") JobStatus failed,
            @Param("pending") JobStatus pending);
}