package com.taskflow.queue;

import com.taskflow.config.TaskflowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis list backed queue. The API pushes with LPUSH and the worker pops with BRPOP, so the
 * list is first-in-first-out.
 */
@Component
public class RedisJobQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisJobQueue.class);

    private final StringRedisTemplate redisTemplate;
    private final String queueKey;

    public RedisJobQueue(StringRedisTemplate redisTemplate, TaskflowProperties properties) {
        this.redisTemplate = redisTemplate;
        this.queueKey = properties.queue().key();
        log.info("Job queue backed by Redis list '{}'", queueKey);
    }

    public void enqueue(Long jobId) {
        try {
            redisTemplate.opsForList().leftPush(queueKey, jobId.toString());
            log.debug("Enqueued job {} on {}", jobId, queueKey);
        } catch (DataAccessException ex) {
            // Includes connection refused, timeout and auth failures against Redis.
            throw new QueueUnavailableException("Could not reach Redis to queue job " + jobId, ex);
        }
    }

    public String queueKey() {
        return queueKey;
    }
}
