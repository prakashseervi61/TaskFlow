package com.taskflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * REST API. Component scanning starts at {@code com.taskflow} so the domain classes that live
 * in the core module (entities, repositories, properties) are picked up too.
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.taskflow.config")
public class TaskflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskflowApplication.class, args);
    }
}