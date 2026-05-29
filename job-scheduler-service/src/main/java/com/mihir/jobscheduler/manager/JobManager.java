package com.mihir.jobscheduler.manager;

import com.mihir.jobscheduler.model.JobName;
import com.mihir.jobscheduler.model.JobStatus;
import com.mihir.jobscheduler.model.ScheduleType;
import com.mihir.jobscheduler.entity.JobEntity;
import com.mihir.jobscheduler.repo.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JobManager {

    private final JobRepository jobRepository;

    public Optional<JobEntity> findDuplicate(
            String createdBy,
            JobName jobName,
            ScheduleType scheduleType,
            String scheduleJson,
            String payloadJson) {
        return jobRepository.findFirstByCreatedByAndJobNameAndScheduleTypeAndScheduleJsonAndPayloadJson(
                createdBy, jobName, scheduleType, scheduleJson, payloadJson);
    }

    public JobEntity save(JobEntity jobEntity) {
        return jobRepository.save(jobEntity);
    }

    public Optional<JobEntity> findById(Long jobId) {
        return jobRepository.findById(jobId);
    }

    public List<JobEntity> findFailedJobs() {
        return jobRepository.findAllByStatusOrderByCreatedAtDesc(JobStatus.FAILED);
    }

    public Page<JobEntity> findPageForAll(Pageable pageable) {
        return jobRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Page<JobEntity> findPageForUser(String createdBy, Pageable pageable) {
        return jobRepository.findAllByCreatedByOrderByCreatedAtDesc(createdBy, pageable);
    }

    public List<JobEntity> findAllByIds(List<Long> jobIds) {
        return jobRepository.findAllByJobIdIn(jobIds);
    }

    public JobEntity markFailed(JobEntity jobEntity) {
        jobEntity.setStatus(JobStatus.FAILED);
        return jobRepository.save(jobEntity);
    }

    public JobEntity markCompleted(JobEntity jobEntity) {
        jobEntity.setStatus(JobStatus.COMPLETED);
        return jobRepository.save(jobEntity);
    }

    public JobEntity markScheduled(JobEntity jobEntity) {
        jobEntity.setStatus(JobStatus.SCHEDULED);
        return jobRepository.save(jobEntity);
    }

    public void delete(JobEntity jobEntity) {
        jobRepository.delete(jobEntity);
    }
}
