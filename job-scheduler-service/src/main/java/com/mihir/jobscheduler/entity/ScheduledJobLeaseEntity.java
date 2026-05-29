package com.mihir.jobscheduler.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "scheduled_job_leases", indexes = {
        @Index(name = "idx_next_run", columnList = "next_run_at"),
        @Index(name = "idx_lock_expiry", columnList = "locked_until")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledJobLeaseEntity {

    @Id
    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, foreignKey = @ForeignKey(name = "fk_scheduled_job_leases_job"))
    private JobEntity job;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "first_claimed_at")
    private Instant firstClaimedAt;

    @Column(name = "locked_by", length = 64)
    private String lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

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
