package com.taskflow.worker.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

@ExtendWith(MockitoExtension.class)
class RetrySchedulerTest {

    @Mock
    private JobQueueWriter queueWriter;

    private RetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new RetryScheduler(queueWriter);
    }

    @Test
    @DisplayName("promotes every retry that is due")
    void promotesDueRetries() {
        when(queueWriter.dueScheduled(org.mockito.ArgumentMatchers.anyLong(), eq(100)))
                .thenReturn(Set.of("11", "12"));

        scheduler.promoteDueRetries();

        verify(queueWriter).promoteScheduled("11");
        verify(queueWriter).promoteScheduled("12");
    }

    @Test
    @DisplayName("does nothing when nothing is due")
    void doesNothingWhenNothingDue() {
        when(queueWriter.dueScheduled(org.mockito.ArgumentMatchers.anyLong(), eq(100)))
                .thenReturn(Set.of());

        scheduler.promoteDueRetries();

        verify(queueWriter, never()).promoteScheduled(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("survives Redis being unavailable so the consumer keeps running")
    void survivesRedisOutage() {
        when(queueWriter.dueScheduled(org.mockito.ArgumentMatchers.anyLong(), eq(100)))
                .thenThrow(new QueryTimeoutException("connection refused"));

        scheduler.promoteDueRetries();
    }

    @Test
    @DisplayName("tolerates a job another worker already promoted")
    void toleratesAlreadyPromoted() {
        when(queueWriter.dueScheduled(org.mockito.ArgumentMatchers.anyLong(), eq(100)))
                .thenReturn(Set.of("11"));
        when(queueWriter.promoteScheduled("11")).thenReturn(false);

        scheduler.promoteDueRetries();

        verify(queueWriter).promoteScheduled("11");
    }

    @Test
    @DisplayName("uses the current clock so backoff is honoured")
    void usesCurrentClock() {
        long before = System.currentTimeMillis();
        when(queueWriter.dueScheduled(org.mockito.ArgumentMatchers.anyLong(), eq(100))).thenReturn(Set.of());

        scheduler.promoteDueRetries();

        org.mockito.ArgumentCaptor<Long> captor = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(queueWriter).dueScheduled(captor.capture(), eq(100));
        assertThat(captor.getValue()).isGreaterThanOrEqualTo(before);
    }
}