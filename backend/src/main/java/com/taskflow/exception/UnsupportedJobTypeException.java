package com.taskflow.exception;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Thrown when a create request names a job type no handler implements. Mapped to HTTP 400. */
public class UnsupportedJobTypeException extends RuntimeException {

    private final String requestedType;

    public UnsupportedJobTypeException(String requestedType) {
        super("Unknown jobType '" + requestedType + "'. Supported types: " + supportedTypes());
        this.requestedType = requestedType;
    }

    public String getRequestedType() {
        return requestedType;
    }

    private static String supportedTypes() {
        return Arrays.stream(com.taskflow.entity.JobType.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }
}