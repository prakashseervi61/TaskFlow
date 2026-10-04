package com.taskflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.taskflow.config.RetryProperties;
import com.taskflow.dto.CreateJobRequest;
import com.taskflow.dto.JobPageResponse;
import com.taskflow.dto.JobResponse;
import com.taskflow.dto.JobStatsResponse;
import com.taskflow.entity.Job;
import com.taskflow.entity.JobStatus;
import com.taskflow.entity.JobType;
import com.taskflow.exception.InvalidJobStateException;
import com.taskflow.exception.JobNotFoundException;
import com.taskflow.exception.UnsupportedJobTypeException;
import com.taskflow.repository.JobRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private JobService jobService;

    @Captor
    private ArgumentCaptor<Job> jobCaptor;

    @BeforeEach
    void setUp() {
        jobService =
                new JobService(jobRepository, eventPublisher, new RetryProperties(3, Duration.ofSeconds(5), Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("create() persists a PENDING job and trims input")
    void createPersistsPendingJob() {
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        JobResponse response =
                jobService.create(new CreateJobRequest("  Nightly report  ", "  Builds the report  ", null, null, null));

        verify(jobRepository).save(jobCaptor.capture());
        Job persisted = jobCaptor.getValue();

        assertThat(persisted.getName()).isEqualTo("Nightly report");
        assertThat(persisted.getDescription()).isEqualTo("Builds the report");
        assertThat(persisted.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(persisted.getAttemptCount()).isZero();
        assertThat(persisted.getJobType()).isEqualTo(JobType.SIMULATED);
        assertThat(persisted.getMaxAttempts()).isEqualTo(3);
        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("create() accepts a job type, payload and maxAttempts override")
    void createAcceptsTypeAndPayload() {
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        jobService.create(new CreateJobRequest("doomed", "always fails", "fail", java.util.Map.of("reason", "test"), 5));

        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getJobType()).isEqualTo(JobType.FAIL);
        assertThat(jobCaptor.getValue().getMaxAttempts()).isEqualTo(5);
        assertThat(jobCaptor.getValue().getPayload()).containsEntry("reason", "test");
    }

    @Test
    @DisplayName("create() rejects an unknown job type with 400-worthy error")
    void createRejectsUnknownType() {
        assertThatThrownBy(() -> jobService.create(new CreateJobRequest("x", "y", "NOPE", null, null)))
                .isInstanceOf(UnsupportedJobTypeException.class)
                .hasMessageContaining("NOPE")
                .hasMessageContaining("SIMULATED");

        verify(jobRepository, never()).save(any(Job.class));
    }

    @Test
    @DisplayName("findById() throws JobNotFoundException for an unknown id")
    void findByIdThrowsWhenMissing() {
        when(jobRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.findById(99L))
                .isInstanceOf(JobNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("delete() throws JobNotFoundException for an unknown id")
    void deleteThrowsWhenMissing() {
        when(jobRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.delete(404L)).isInstanceOf(JobNotFoundException.class);
        verify(jobRepository, never()).delete(any(Job.class));
    }

    @Test
    @DisplayName("execute() queues a PENDING job")
    void executeQueuesPendingJob() {
        // first read is the eligibility check, second read reflects the queued row
        when(jobRepository.findById(7L))
                .thenReturn(Optional.of(job(7L, JobStatus.PENDING)), Optional.of(job(7L, JobStatus.QUEUED)));
        when(jobRepository.claimForQueueing(
                        eq(7L), eq(JobStatus.PENDING), eq(JobStatus.FAILED), eq(JobStatus.QUEUED), any()))
                .thenReturn(1);

        JobResponse response = jobService.execute(7L);

        verify(eventPublisher).publishEvent(new JobExecutionRequested(7L));
        assertThat(response.status()).isEqualTo(JobStatus.QUEUED);
    }

    @Test
    @DisplayName("execute() re-queues a FAILED job that still has attempts left")
    void executeRetriesFailedJobWithAttemptsLeft() {
        when(jobRepository.findById(7L)).thenReturn(Optional.of(job(7L, JobStatus.FAILED)));
        when(jobRepository.claimForQueueing(
                        eq(7L), eq(JobStatus.PENDING), eq(JobStatus.FAILED), eq(JobStatus.QUEUED), any()))
                .thenReturn(1);

        assertThat(jobService.execute(7L)).isNotNull();

        verify(eventPublisher).publishEvent(new JobExecutionRequested(7L));
    }

    @Test
    @DisplayName("execute() rejects a job that cannot be queued")
    void executeRejectsIneligibleJob() {
        when(jobRepository.findById(8L)).thenReturn(Optional.of(job(8L, JobStatus.COMPLETED)));
        when(jobRepository.claimForQueueing(
                        eq(8L), eq(JobStatus.PENDING), eq(JobStatus.FAILED), eq(JobStatus.QUEUED), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> jobService.execute(8L))
                .isInstanceOf(InvalidJobStateException.class)
                .hasMessageContaining("COMPLETED");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("execute() throws 404-worthy error for a missing job")
    void executeRejectsMissingJob() {
        when(jobRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.execute(10L)).isInstanceOf(JobNotFoundException.class);
    }

    @Test
    @DisplayName("requeue() resets a terminally failed job to PENDING")
    void requeueResetsFailedJob() {
        when(jobRepository.findById(11L)).thenReturn(Optional.of(job(11L, JobStatus.FAILED)));
        when(jobRepository.requeueFailed(eq(11L), eq(JobStatus.FAILED), eq(JobStatus.PENDING)))
                .thenReturn(1);

        assertThat(jobService.requeue(11L)).isNotNull();

        verify(jobRepository).requeueFailed(11L, JobStatus.FAILED, JobStatus.PENDING);
    }

    @Test
    @DisplayName("requeue() rejects a job that is not FAILED")
    void requeueRejectsNonFailedJob() {
        when(jobRepository.findById(12L)).thenReturn(Optional.of(job(12L, JobStatus.COMPLETED)));
        when(jobRepository.requeueFailed(eq(12L), eq(JobStatus.FAILED), eq(JobStatus.PENDING)))
                .thenReturn(0);

        assertThatThrownBy(() -> jobService.requeue(12L)).isInstanceOf(InvalidJobStateException.class);
    }

    @Test
    @DisplayName("findPage() returns the first page without a cursor")
    void findPageFirstPage() {
        when(jobRepository.findAllByOrderByCreatedAtDescIdDesc(any(Pageable.class)))
                .thenReturn(List.of(job(3L, JobStatus.PENDING), job(2L, JobStatus.PENDING)));
        when(jobRepository.count()).thenReturn(2L);

        JobPageResponse page = jobService.findPage(null, 20);

        assertThat(page.items()).hasSize(2);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
        assertThat(page.total()).isEqualTo(2L);
    }

    @Test
    @DisplayName("findPage() reports a next page when an extra row came back")
    void findPageDetectsMoreRows() {
        // limit 2 + one extra row means another page exists
        when(jobRepository.findAllByOrderByCreatedAtDescIdDesc(any(Pageable.class)))
                .thenReturn(List.of(job(5L, JobStatus.PENDING), job(4L, JobStatus.PENDING), job(3L, JobStatus.PENDING)));
        when(jobRepository.count()).thenReturn(9L);

        JobPageResponse page = jobService.findPage(null, 2);

        assertThat(page.items()).hasSize(2);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isNotNull();
        assertThat(page.total()).isEqualTo(9L);
    }

    @Test
    @DisplayName("findPage() decodes the cursor and queries after it")
    void findPageUsesCursor() {
        // a full first page, so a cursor is issued
        when(jobRepository.findAllByOrderByCreatedAtDescIdDesc(any(Pageable.class)))
                .thenReturn(List.of(
                        job(5L, JobStatus.PENDING), job(4L, JobStatus.PENDING), job(3L, JobStatus.PENDING)));
        when(jobRepository.count()).thenReturn(9L);

        String cursor = jobService.findPage(null, 2).nextCursor();
        assertThat(cursor).as("a full page must expose a cursor to follow").isNotNull();

        when(jobRepository.findPageAfter(any(Instant.class), any(Long.class), any(Pageable.class)))
                .thenReturn(List.of(job(3L, JobStatus.PENDING)));

        JobPageResponse second = jobService.findPage(cursor, 2);

        verify(jobRepository).findPageAfter(any(Instant.class), any(Long.class), any(Pageable.class));
        assertThat(second.items()).hasSize(1);
    }
    @Test
    @DisplayName("findPage() ignores a malformed cursor and starts from the beginning")
    void findPageIgnoresMalformedCursor() {
        when(jobRepository.findAllByOrderByCreatedAtDescIdDesc(any(Pageable.class)))
                .thenReturn(List.of(job(3L, JobStatus.PENDING)));
        when(jobRepository.count()).thenReturn(1L);

        assertThat(jobService.findPage("not-a-cursor!!", 20).items()).hasSize(1);
        verify(jobRepository).findAllByOrderByCreatedAtDescIdDesc(any(Pageable.class));
    }

    @Test
    @DisplayName("findPage() clamps the page size to the documented maximum")
    void findPageClampsLimit() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(jobRepository.findAllByOrderByCreatedAtDescIdDesc(captor.capture())).thenReturn(List.of());
        when(jobRepository.count()).thenReturn(0L);

        jobService.findPage(null, 5000);

        // clamped to 100, plus one over-fetched row to detect another page
        assertThat(captor.getValue().getPageSize()).isEqualTo(101);
    }

    @Test
    @DisplayName("stats() counts every status and derives activeCount")
    void statsCountsByStatus() {
        when(jobRepository.count()).thenReturn(20L);
        when(jobRepository.countByStatus(JobStatus.PENDING)).thenReturn(5L);
        when(jobRepository.countByStatus(JobStatus.QUEUED)).thenReturn(3L);
        when(jobRepository.countByStatus(JobStatus.RUNNING)).thenReturn(2L);
        when(jobRepository.countByStatus(JobStatus.COMPLETED)).thenReturn(8L);
        when(jobRepository.countByStatus(JobStatus.FAILED)).thenReturn(2L);
        when(jobRepository.countByStatusAndNextAttemptAtIsNotNull(JobStatus.QUEUED)).thenReturn(1L);

        JobStatsResponse stats = jobService.stats();

        assertThat(stats.total()).isEqualTo(20L);
        assertThat(stats.pending()).isEqualTo(5L);
        assertThat(stats.queued()).isEqualTo(3L);
        assertThat(stats.running()).isEqualTo(2L);
        assertThat(stats.completed()).isEqualTo(8L);
        assertThat(stats.failed()).isEqualTo(2L);
        assertThat(stats.retrying()).isEqualTo(1L);
        assertThat(stats.activeCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("deadLettered() lists terminally failed jobs")
    void deadLetteredListsFailedJobs() {
        when(jobRepository.findByStatus(JobStatus.FAILED))
                .thenReturn(List.of(job(1L, JobStatus.FAILED), job(2L, JobStatus.FAILED)));

        assertThat(jobService.deadLettered()).hasSize(2);
        verify(jobRepository).findByStatus(eq(JobStatus.FAILED));
    }

    private Job job(Long id, JobStatus status) {
        Job job = new Job();
        job.setId(id);
        job.setName("job-" + id);
        job.setDescription("description-" + id);
        job.setStatus(status);
        job.setJobType(JobType.SIMULATED);
        job.setCreatedAt(Instant.now().minusSeconds(id));
        job.setAttemptCount(status == JobStatus.QUEUED ? 1 : 0);
        job.setMaxAttempts(3);
        return job;
    }
}