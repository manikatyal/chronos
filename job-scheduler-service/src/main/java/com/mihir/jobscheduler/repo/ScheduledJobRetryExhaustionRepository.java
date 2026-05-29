package com.mihir.jobscheduler.repo;

import com.mihir.jobscheduler.entity.ScheduledJobRetryExhaustionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ScheduledJobRetryExhaustionRepository extends JpaRepository<ScheduledJobRetryExhaustionEntity, Long> {

    List<ScheduledJobRetryExhaustionEntity> findAllByJobIdInOrderByExhaustedAtDesc(List<Long> jobIds);

    List<ScheduledJobRetryExhaustionEntity> findAllByExhaustedAtGreaterThanEqualOrderByExhaustedAtDesc(Instant exhaustedAt);

    Page<ScheduledJobRetryExhaustionEntity> findAllByOrderByExhaustedAtDesc(Pageable pageable);

    List<ScheduledJobRetryExhaustionEntity> findAllByOrderByExhaustedAtDesc();
}
