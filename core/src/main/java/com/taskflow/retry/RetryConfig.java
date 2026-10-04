package com.taskflow.retry;

import com.taskflow.config.RetryProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares {@link BackoffPolicy} as a bean.
 *
 * <p>It lives in core but is only used by the worker, and the worker deliberately limits its
 * component scan to its own package — so the bean is wired explicitly rather than relying on
 * {@code @Component} scanning.
 */
@Configuration(proxyBeanMethods = false)
public class RetryConfig {

    @Bean
    public BackoffPolicy backoffPolicy(RetryProperties properties) {
        return new BackoffPolicy(properties);
    }
}