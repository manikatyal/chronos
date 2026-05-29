package com.mihir.jobworker.repo;

import com.mihir.jobworker.entity.JobExecutionEntity;
import com.mihir.jobworker.model.ExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface JobExecutionRepository extends JpaRepository<JobExecutionEntity, Long> {

    Optional<JobExecutionEntity> findByExecutionId(String executionId);

    Optional<JobExecutionEntity> findTopByJobIdOrderByCreatedAtDesc(Long jobId);

    List<JobExecutionEntity> findAllByStatusOrderByProcessedAtDesc(ExecutionStatus status);

    List<JobExecutionEntity> findAllByStatusAndProcessedAtGreaterThanEqualOrderByProcessedAtDesc(
            ExecutionStatus status,
            Instant processedAt);

    List<JobExecutionEntity> findAllByOrderByCreatedAtDesc();
}
