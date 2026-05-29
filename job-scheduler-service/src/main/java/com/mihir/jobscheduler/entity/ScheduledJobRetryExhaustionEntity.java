package com.mihir.jobscheduler.entity;

import com.mihir.jobscheduler.model.FailureSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "scheduled_job_retry_exhaustions", indexes = {
        @Index(name = "idx_retry_exhaustions_job_id", columnList = "job_id"),
        @Index(name = "idx_retry_exhaustions_exhausted_at", columnList = "exhausted_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledJobRetryExhaustionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "worker_id", nullable = false, length = 64)
    private String workerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_source", nullable = false, length = 32)
    private FailureSource failureSource;

    @Column(name = "execution_id", length = 128)
    private String executionId;

    @Column(name = "log_tag", length = 160)
    private String logTag;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "exhausted_at", nullable = false)
    private Instant exhaustedAt;
}
