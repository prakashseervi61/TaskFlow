package com.taskflow.worker.job;

import com.taskflow.config.TaskflowProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads the same Redis list the API writes to, using a blocking pop so an idle worker costs
 * nothing.
 */
@Component
public class RedisJobQueueReader implements JobQueueReader {

    private static final Logger log = LoggerFactory.getLogger(RedisJobQueueReader.class);

    /** Headroom kept between the read timeout and the blocking read it has to outlast. */
    private static final Duration TIMEOUT_MARGIN = Duration.ofSeconds(1);

    private final StringRedisTemplate redisTemplate;
    private final String queueKey;
    private final Duration maxPollTimeout;

    public RedisJobQueueReader(
            StringRedisTemplate redisTemplate,
            TaskflowProperties properties,
            RedisProperties redisProperties) {
        this.redisTemplate = redisTemplate;
        this.queueKey = properties.queue().key();

        // A blocking pop must return before the connection read timeout fires, otherwise every
        // idle poll looks like a Redis outage.
        Duration readTimeout = redisProperties.getTimeout();
        this.maxPollTimeout =
                readTimeout == null || !readTimeout.isNegative()
                        ? readTimeout.minus(TIMEOUT_MARGIN)
                        : Duration.ofSeconds(5);

        log.info("Worker consuming from Redis list '{}' (max poll {})", queueKey, maxPollTimeout);
    }

    @Override
    public Long poll(Duration timeout) {
        Duration effective = capTimeout(timeout);
        try {
            String popped = redisTemplate.opsForList().rightPop(queueKey, effective);
            if (popped == null || popped.isBlank()) {
                return null;
            }
            return Long.valueOf(popped);
        } catch (DataAccessException ex) {
            throw new QueueUnavailableException("Could not reach Redis to read " + queueKey, ex);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Queue entry is not a job id: " + ex.getMessage(), ex);
        }
    }

    /** Keeps a misconfigured poll timeout from turning every poll into a fake outage. */
    Duration capTimeout(Duration requested) {
        if (!maxPollTimeout.isNegative() && requested.compareTo(maxPollTimeout) > 0) {
            return maxPollTimeout.isNegative() || maxPollTimeout.isZero()
                    ? Duration.ofMillis(500)
                    : maxPollTimeout;
        }
        return requested;
    }

    public String queueKey() {
        return queueKey;
    }
}