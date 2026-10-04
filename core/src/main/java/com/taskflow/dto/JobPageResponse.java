package com.taskflow.dto;

import java.util.List;

/**
 * One page of the job list.
 *
 * @param nextCursor opaque cursor for the following page, or null at the end of the list
 */
public record JobPageResponse(List<JobResponse> items, String nextCursor, boolean hasMore, long total) {

    public static JobPageResponse of(List<JobResponse> items, String nextCursor, boolean hasMore, long total) {
        return new JobPageResponse(items, nextCursor, hasMore, total);
    }
}