package com.taskflow.controller;

import com.taskflow.dto.CreateJobRequest;
import com.taskflow.dto.JobPageResponse;
import com.taskflow.dto.JobResponse;
import com.taskflow.dto.JobStatsResponse;
import com.taskflow.service.JobService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /** POST /api/jobs → 201 Created */
    @PostMapping
    public ResponseEntity<JobResponse> create(@Valid @RequestBody CreateJobRequest request) {
        JobResponse created = jobService.create(request);
        return ResponseEntity.created(URI.create("/api/jobs/" + created.id())).body(created);
    }

    /**
     * GET /api/jobs → 200 OK, newest first.
     *
     * <p>Breaking change in v0.3: the response is now a page envelope rather than a bare array,
     * because a cursor needs somewhere to live.
     *
     * @param limit  1–100, default 20
     * @param cursor {@code nextCursor} from a previous page; omit for the first page
     */
    @GetMapping
    public ResponseEntity<JobPageResponse> list(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok(jobService.findPage(cursor, limit));
    }

    /** GET /api/jobs/{id} → 200 OK, 404 if missing */
    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.findById(id));
    }

    /** GET /api/jobs/stats → 200 OK, counts per status */
    @GetMapping("/stats")
    public ResponseEntity<JobStatsResponse> stats() {
        return ResponseEntity.ok(jobService.stats());
    }

    /** GET /api/jobs/dead-letter → 200 OK, jobs whose retries were exhausted */
    @GetMapping("/dead-letter")
    public ResponseEntity<List<JobResponse>> deadLetter() {
        return ResponseEntity.ok(jobService.deadLettered());
    }

    /** POST /api/jobs/{id}/execute → 200 OK (QUEUED), 404/409 */
    @PostMapping("/{id}/execute")
    public ResponseEntity<JobResponse> execute(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.execute(id));
    }

    /** POST /api/jobs/{id}/requeue → 200 OK (PENDING), 404/409 */
    @PostMapping("/{id}/requeue")
    public ResponseEntity<JobResponse> requeue(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.requeue(id));
    }

    /** DELETE /api/jobs/{id} → 204 No Content, 404 if missing */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        jobService.delete(id);
        return ResponseEntity.noContent().build();
    }
}