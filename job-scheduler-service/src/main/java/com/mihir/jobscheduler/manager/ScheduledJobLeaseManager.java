package com.mihir.jobscheduler.manager;

import com.mihir.jobscheduler.entity.JobEntity;
import com.mihir.jobscheduler.entity.ScheduledJobLeaseEntity;
import com.mihir.jobscheduler.repo.ScheduledJobLeaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ScheduledJobLeaseManager {

    private final ScheduledJobLeaseRepository scheduledJobLeaseRepository;

    public ScheduledJobLeaseEntity createLease(JobEntity job, Instant nextRunAt) {
        return scheduledJobLeaseRepository.save(ScheduledJobLeaseEntity.builder()
                .job(job)
                .nextRunAt(nextRunAt)
                .retryCount(0)
                .build());
    }

    public int markSuccess(Long jobId, String workerId, Instant nextRunAt) {
        return scheduledJobLeaseRepository.markSuccess(jobId, workerId, nextRunAt);
    }

    public int markRetry(Long jobId, String workerId, Instant nextRunAt) {
        return scheduledJobLeaseRepository.markRetry(jobId, workerId, nextRunAt);
    }

    public int markExhausted(Long jobId, String workerId) {
        return scheduledJobLeaseRepository.markExhausted(jobId, workerId);
    }

    public java.util.Optional<ScheduledJobLeaseEntity> findByJobId(Long jobId) {
        return scheduledJobLeaseRepository.findById(jobId);
    }

    public java.util.List<ScheduledJobLeaseEntity> findAllByJobIds(java.util.List<Long> jobIds) {
        return scheduledJobLeaseRepository.findAllByJobIdIn(jobIds);
    }

    public ScheduledJobLeaseEntity save(ScheduledJobLeaseEntity leaseEntity) {
        return scheduledJobLeaseRepository.save(leaseEntity);
    }

    public void deleteByJobId(Long jobId) {
        scheduledJobLeaseRepository.deleteByJobId(jobId);
    }
}
