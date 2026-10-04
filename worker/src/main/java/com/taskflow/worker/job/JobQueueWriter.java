package com.taskflow.worker.job;

import com.taskflow.config.TaskflowProperties;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * The worker's write side of the queue.
 *
 * <p>Three Redis keys, mirroring the read side in {@link RedisJobQueueReader}:
 *
 * <ul>
 *   <li>{@code taskflow:jobs} — list the consumer pops from
 *   <li>{@code taskflow:jobs:scheduled} — sorted set scored by "eligible at" epoch millis, so a
 *       retry waits in Redis instead of occupying a consumer slot
 *   <li>{@code taskflow:jobs:dlq} — list of ids whose retries were exhausted
 * </ul>
 */
@Component
public class JobQueueWriter {

    private static final Logger log = LoggerFactory.getLogger(JobQueueWriter.class);

    private final StringRedisTemplate redisTemplate;
    private final String queueKey;
    private final String scheduledKey;
    private final String deadLetterKey;

    public JobQueueWriter(StringRedisTemplate redisTemplate, TaskflowProperties properties) {
        this.redisTemplate = redisTemplate;
        this.queueKey = properties.queue().key();
        this.scheduledKey = properties.queue().key() + ":scheduled";
        this.deadLetterKey = properties.queue().key() + ":dlq";
    }

    /**
     * Makes a job id available immediately.
     *
     * @return false when Redis was unreachable, so bulk callers can carry on
     */
    public boolean enqueueSafely(Long jobId) {
        try {
            redisTemplate.opsForList().leftPush(queueKey, jobId.toString());
            return true;
        } catch (DataAccessException ex) {
            log.error("Could not enqueue job {}: {}", jobId, ex.getMessage());
            return false;
        }
    }

    /**
     * Schedules a job id for later by scoring it in the sorted set.
     *
     * <p>Scoring by the eligible instant is what lets {@link RetryScheduler} ask Redis for
     * "everything due before now" in one call.
     */
    public void schedule(Long jobId, Instant runAt) {
        redisTemplate.opsForZSet().add(scheduledKey, jobId.toString(), runAt.toEpochMilli());
        log.debug("Scheduled job {} at {}", jobId, runAt);
    }

    public void markDeadLettered(Long jobId) {
        redisTemplate.opsForList().leftPush(deadLetterKey, jobId.toString());
        log.warn("Job {} moved to the dead letter queue", jobId);
    }

    /** Job ids whose retry has come due, oldest first. */
    public Set<String> dueScheduled(long nowEpochMillis, int limit) {
        Set<String> due = redisTemplate.opsForZSet().rangeByScore(scheduledKey, 0, nowEpochMillis, 0, limit);
        return due == null ? Set.of() : due;
    }

    /**
     * Moves a due job onto the main queue.
     *
     * <p>Removing from the schedule before pushing means a crash in between loses the retry, so
     * {@link QueuedJobRecovery} re-publishes overdue QUEUED rows and the database claim guard
     * makes the double-push harmless.
     */
    public boolean promoteScheduled(String jobId) {
        // ZSet.remove returns how many members were removed, so 0 means another worker (or a
        // previous run) already promoted this id.
        Long removed = redisTemplate.opsForZSet().remove(scheduledKey, jobId);
        if (removed == null || removed == 0L) {
            return false;
        }
        redisTemplate.opsForList().leftPush(queueKey, jobId);
        return true;
    }

    public void cancelScheduled(Long jobId) {
        redisTemplate.opsForZSet().remove(scheduledKey, jobId.toString());
    }

    /** Queue-side dead letters. The API's dead-letter view reads PostgreSQL instead. */
    public List<String> deadLetterEntries() {
        List<String> entries = redisTemplate.opsForList().range(deadLetterKey, 0, -1);
        return entries == null ? List.of() : entries;
    }

    public String queueKey() {
        return queueKey;
    }

    public String scheduledKey() {
        return scheduledKey;
    }

    public String deadLetterKey() {
        return deadLetterKey;
    }

    /** True when Redis is reachable; used to decide whether a publish is worth attempting. */
    public boolean isReachable() {
        try {
            redisTemplate.opsForList().size(queueKey);
            return true;
        } catch (DataAccessException ex) {
            return false;
        }
    }
}