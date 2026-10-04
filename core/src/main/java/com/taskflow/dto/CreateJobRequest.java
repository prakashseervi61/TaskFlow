package com.taskflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * @param maxAttempts optional override; defaults to {@code taskflow.retry.default-max-attempts}
 */
public record CreateJobRequest(
        @NotBlank(message = "name is required")
        @Size(max = 120, message = "name must be at most 120 characters")
        String name,

        @NotBlank(message = "description is required")
        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,

        /** Optional; must name a known {@code JobType}. Defaults to SIMULATED. */
        @Size(max = 40, message = "jobType must be at most 40 characters")
        String jobType,

        /** Optional free-form handler input. Validated as JSON by the DTO layer. */
        Map<String, Object> payload,

        @Min(value = 1, message = "maxAttempts must be at least 1")
        @Max(value = 10, message = "maxAttempts must be at most 10")
        Integer maxAttempts) {
}