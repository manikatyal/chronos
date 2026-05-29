package com.mihir.jobworker.entity;

import com.mihir.jobworker.model.ExecutionStatus;
import com.mihir.jobworker.model.JobName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "job_executions", indexes = {
        @Index(name = "idx_job_executions_job_id", columnList = "job_id"),
        @Index(name = "idx_job_executions_status", columnList = "status"),
        @Index(name = "idx_job_executions_processed_at", columnList = "processed_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_job_executions_execution_id", columnNames = "execution_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "execution_id", nullable = false, length = 128)
    private String executionId;

    @Column(name = "log_tag", nullable = false, length = 160)
    private String logTag;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_name", nullable = false, length = 64)
    private JobName jobName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ExecutionStatus status;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
