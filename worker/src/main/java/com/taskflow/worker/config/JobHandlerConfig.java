package com.taskflow.worker.config;

import com.taskflow.config.TaskflowProperties;
import com.taskflow.entity.JobType;
import com.taskflow.handler.JobHandler;
import com.taskflow.worker.handler.FailingJobHandler;
import com.taskflow.worker.handler.PingJobHandler;
import com.taskflow.worker.handler.SimulatedJobHandler;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the {@link JobHandler} beans the worker dispatches on. */
@Configuration(proxyBeanMethods = false)
public class JobHandlerConfig {

    @Bean
    public JobHandler simulatedJobHandler(TaskflowProperties properties) {
        return new SimulatedJobHandler(properties.execution().simulatedDurationMs());
    }

    @Bean
    public JobHandler pingJobHandler() {
        return new PingJobHandler();
    }

    @Bean
    public JobHandler failingJobHandler(WorkerProperties properties) {
        return new FailingJobHandler(properties.failureMessage());
    }

    /** Exposed for documentation/tests: the types the API will accept. */
    public static List<JobType> supportedTypes() {
        return List.of(JobType.values());
    }
}