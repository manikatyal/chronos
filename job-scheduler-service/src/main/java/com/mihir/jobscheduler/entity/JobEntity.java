package com.mihir.jobscheduler.entity;

import com.mihir.jobscheduler.model.JobName;
import com.mihir.jobscheduler.model.JobStatus;
import com.mihir.jobscheduler.model.ScheduleType;
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
@Table(name = "scheduled_jobs", indexes = {
        @Index(name = "idx_jobs_status", columnList = "status"),
        @Index(name = "idx_jobs_created_at", columnList = "created_at"),
        @Index(name = "idx_jobs_created_by", columnList = "created_by")
	}, uniqueConstraints = {
	        @UniqueConstraint(
	                name = "uk_jobs_dedup",
	                columnNames = {"created_by", "job_name", "schedule_type", "schedule_json", "payload_json"}
	        )
	})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "job_name", nullable = false)
    private JobName jobName;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "schedule_type", nullable = false)
    private ScheduleType scheduleType;

    @Column(name = "schedule_json", nullable = false, columnDefinition = "TEXT")
    private String scheduleJson;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "status", nullable = false)
    private JobStatus status;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

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
