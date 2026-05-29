package com.mihir.jobscheduler.repo;

import com.mihir.jobscheduler.entity.ScheduledJobLeaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ScheduledJobLeaseRepository extends JpaRepository<ScheduledJobLeaseEntity, Long> {

    List<ScheduledJobLeaseEntity> findAllByJobIdIn(List<Long> jobIds);

    @Modifying
    @Query("""
            update ScheduledJobLeaseEntity lease
            set lease.nextRunAt = :nextRunAt,
                lease.retryCount = 0,
                lease.firstClaimedAt = coalesce(lease.firstClaimedAt, current_timestamp),
                lease.lockedBy = null,
                lease.lockedAt = null,
                lease.lockedUntil = null
            where lease.jobId = :jobId and lease.lockedBy = :workerId
            """)
    int markSuccess(@Param("jobId") Long jobId, @Param("workerId") String workerId, @Param("nextRunAt") Instant nextRunAt);

    @Modifying
    @Query("""
            update ScheduledJobLeaseEntity lease
            set lease.nextRunAt = :nextRunAt,
                lease.retryCount = lease.retryCount + 1,
                lease.firstClaimedAt = coalesce(lease.firstClaimedAt, current_timestamp),
                lease.lockedBy = null,
                lease.lockedAt = null,
                lease.lockedUntil = null
            where lease.jobId = :jobId and lease.lockedBy = :workerId
            """)
    int markRetry(@Param("jobId") Long jobId, @Param("workerId") String workerId, @Param("nextRunAt") Instant nextRunAt);

    @Modifying
    @Query("""
            update ScheduledJobLeaseEntity lease
            set lease.nextRunAt = null,
                lease.retryCount = lease.retryCount + 1,
                lease.firstClaimedAt = coalesce(lease.firstClaimedAt, current_timestamp),
                lease.lockedBy = null,
                lease.lockedAt = null,
                lease.lockedUntil = null
            where lease.jobId = :jobId and lease.lockedBy = :workerId
            """)
    int markExhausted(@Param("jobId") Long jobId, @Param("workerId") String workerId);

    void deleteByJobId(Long jobId);
}
