package com.taskflow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskflow.dto.CreateJobRequest;
import com.taskflow.dto.JobPageResponse;
import com.taskflow.dto.JobResponse;
import com.taskflow.dto.JobStatsResponse;
import com.taskflow.entity.JobStatus;
import com.taskflow.entity.JobType;
import com.taskflow.exception.InvalidJobStateException;
import com.taskflow.exception.JobNotFoundException;
import com.taskflow.exception.UnsupportedJobTypeException;
import com.taskflow.service.JobService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentMatchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer: status codes, page envelope, Location header and the error shape. */
@WebMvcTest(controllers = JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobService jobService;

    @Test
    @DisplayName("POST /api/jobs returns 201 with a Location header")
    void createReturns201() throws Exception {
        when(jobService.create(any(CreateJobRequest.class))).thenReturn(jobResponse(1L, JobStatus.PENDING));

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"nightly-report","description":"Builds the report"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/jobs/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.jobType").value("SIMULATED"))
                .andExpect(jsonPath("$.attemptCount").value(0))
                .andExpect(jsonPath("$.maxAttempts").value(3))
                .andExpect(jsonPath("$.attemptsLeft").value(3));
    }

    @Test
    @DisplayName("POST /api/jobs accepts jobType, payload and maxAttempts")
    void createAcceptsTypePayloadAndMaxAttempts() throws Exception {
        when(jobService.create(any(CreateJobRequest.class)))
                .thenReturn(jobResponse(2L, JobStatus.PENDING, JobType.FAIL, 5, Map.of("reason", "test")));

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"doomed","description":"always fails","jobType":"FAIL",
                                 "payload":{"reason":"test"},"maxAttempts":5}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobType").value("FAIL"))
                .andExpect(jsonPath("$.payload.reason").value("test"))
                .andExpect(jsonPath("$.maxAttempts").value(5));
    }

    @Test
    @DisplayName("POST /api/jobs rejects an unknown jobType with 400")
    void createRejectsUnknownJobType() throws Exception {
        when(jobService.create(any(CreateJobRequest.class))).thenThrow(new UnsupportedJobTypeException("NOPE"));

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"x","description":"y","jobType":"NOPE"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("NOPE")));
    }

    @Test
    @DisplayName("POST /api/jobs rejects a blank name with 400")
    void createRejectsInvalidBody() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  ","description":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));
    }

    @Test
    @DisplayName("GET /api/jobs returns a page envelope")
    void listReturnsPageEnvelope() throws Exception {
        when(jobService.findPage(isNull(), ArgumentMatchers.<Integer>any())).thenReturn(new JobPageResponse(
                List.of(jobResponse(2L, JobStatus.QUEUED), jobResponse(1L, JobStatus.PENDING)), "Y3Vyc29y", true, 42L));

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(2))
                .andExpect(jsonPath("$.items[0].status").value("QUEUED"))
                .andExpect(jsonPath("$.nextCursor").value("Y3Vyc29y"))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.total").value(42));
    }

    @Test
    @DisplayName("GET /api/jobs passes limit and cursor through")
    void listPassesPagingParams() throws Exception {
        when(jobService.findPage("abc", 5)).thenReturn(new JobPageResponse(List.of(), null, false, 0));

        mockMvc.perform(get("/api/jobs").param("limit", "5").param("cursor", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(false));

        verify(jobService).findPage("abc", 5);
    }

    @Test
    @DisplayName("GET /api/jobs/{id} returns 404 when the job does not exist")
    void getReturns404WhenMissing() throws Exception {
        when(jobService.findById(99L)).thenThrow(new JobNotFoundException(99L));

        mockMvc.perform(get("/api/jobs/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Job with id 99 was not found"))
                .andExpect(jsonPath("$.path").value("/api/jobs/99"));
    }

    @Test
    @DisplayName("GET /api/jobs/stats reports counts plus activeCount")
    void statsReturnsCounts() throws Exception {
        when(jobService.stats()).thenReturn(new JobStatsResponse(20L, 5L, 3L, 2L, 8L, 2L, 1L, 5L));

        mockMvc.perform(get("/api/jobs/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(20))
                .andExpect(jsonPath("$.queued").value(3))
                .andExpect(jsonPath("$.retrying").value(1))
                .andExpect(jsonPath("$.activeCount").value(5));
    }

    @Test
    @DisplayName("GET /api/jobs/dead-letter lists exhausted jobs")
    void deadLetterReturnsList() throws Exception {
        when(jobService.deadLettered()).thenReturn(List.of(jobResponse(4L, JobStatus.FAILED)));

        mockMvc.perform(get("/api/jobs/dead-letter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("FAILED"));
    }

    @Test
    @DisplayName("POST /api/jobs/{id}/execute returns the QUEUED job")
    void executeReturnsQueuedJob() throws Exception {
        when(jobService.execute(5L)).thenReturn(jobResponse(5L, JobStatus.QUEUED));

        mockMvc.perform(post("/api/jobs/5/execute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    @DisplayName("POST /api/jobs/{id}/execute returns 409 for an ineligible job")
    void executeReturns409ForInvalidState() throws Exception {
        when(jobService.execute(5L))
                .thenThrow(new InvalidJobStateException(5L, JobStatus.COMPLETED, JobStatus.QUEUED));

        mockMvc.perform(post("/api/jobs/5/execute"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("COMPLETED")));
    }

    @Test
    @DisplayName("POST /api/jobs/{id}/requeue resets a failed job")
    void requeueReturnsPendingJob() throws Exception {
        when(jobService.requeue(6L)).thenReturn(jobResponse(6L, JobStatus.PENDING));

        mockMvc.perform(post("/api/jobs/6/requeue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attemptCount").value(0));
    }

    @Test
    @DisplayName("POST /api/jobs/{id}/requeue returns 409 when the job is not FAILED")
    void requeueReturns409() throws Exception {
        when(jobService.requeue(6L))
                .thenThrow(new InvalidJobStateException(6L, JobStatus.RUNNING, JobStatus.PENDING));

        mockMvc.perform(post("/api/jobs/6/requeue"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("RUNNING")));
    }

    @Test
    @DisplayName("DELETE /api/jobs/{id} returns 204")
    void deleteReturns204() throws Exception {
        doNothing().when(jobService).delete(eq(3L));

        mockMvc.perform(delete("/api/jobs/3")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/jobs/{id} returns 404 when the job does not exist")
    void deleteReturns404WhenMissing() throws Exception {
        doThrow(new JobNotFoundException(3L)).when(jobService).delete(3L);

        mockMvc.perform(delete("/api/jobs/3"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Job with id 3 was not found"));
    }

    private JobResponse jobResponse(Long id, JobStatus status) {
        return jobResponse(id, status, JobType.SIMULATED, 3);
    }

    private JobResponse jobResponse(Long id, JobStatus status, JobType type, int maxAttempts) {
        return jobResponse(id, status, type, maxAttempts, Map.of());
    }

    private JobResponse jobResponse(Long id, JobStatus status, JobType type, int maxAttempts, Map<String, Object> payload) {
        int attemptCount = status == JobStatus.QUEUED || status == JobStatus.RUNNING ? 1 : 0;
        return new JobResponse(
                id,
                "job-" + id,
                "description-" + id,
                status,
                type,
                payload,
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:01Z"),
                status == JobStatus.RUNNING || status == JobStatus.COMPLETED
                        ? Instant.parse("2026-01-01T10:00:02Z")
                        : null,
                status == JobStatus.COMPLETED ? Instant.parse("2026-01-01T10:00:05Z") : null,
                null,
                attemptCount,
                maxAttempts,
                Math.max(0, maxAttempts - attemptCount),
                status == JobStatus.FAILED ? "boom" : null,
                status == JobStatus.FAILED ? "IllegalStateException: boom" : null);
    }
}