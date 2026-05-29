package com.mihir.jobscheduler.repo;

import com.mihir.jobscheduler.model.JobStatus;
import com.mihir.jobscheduler.model.JobName;
import com.mihir.jobscheduler.model.ScheduleType;
import com.mihir.jobscheduler.entity.JobEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<JobEntity, Long> {

    Optional<JobEntity> findFirstByCreatedByAndJobNameAndScheduleTypeAndScheduleJsonAndPayloadJson(
            String createdBy,
            JobName jobName,
            ScheduleType scheduleType,
            String scheduleJson,
            String payloadJson);

    Page<JobEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<JobEntity> findAllByCreatedByOrderByCreatedAtDesc(String createdBy, Pageable pageable);

    List<JobEntity> findAllByStatusOrderByCreatedAtDesc(JobStatus status);

    List<JobEntity> findAllByJobIdIn(List<Long> jobIds);
}
