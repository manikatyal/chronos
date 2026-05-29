package com.mihir.jobscheduler.manager;

import com.mihir.jobscheduler.entity.ScheduledJobRetryExhaustionEntity;
import com.mihir.jobscheduler.repo.ScheduledJobRetryExhaustionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ScheduledJobRetryExhaustionManager {

    private final ScheduledJobRetryExhaustionRepository retryExhaustionRepository;

    public ScheduledJobRetryExhaustionEntity save(ScheduledJobRetryExhaustionEntity entity) {
        return retryExhaustionRepository.save(entity);
    }

    public Map<Long, ScheduledJobRetryExhaustionEntity> findLatestByJobIds(List<Long> jobIds) {
        return retryExhaustionRepository.findAllByJobIdInOrderByExhaustedAtDesc(jobIds).stream()
                .collect(Collectors.toMap(
                        ScheduledJobRetryExhaustionEntity::getJobId,
                        Function.identity(),
                        (first, second) -> first));
    }

    public Map<Long, ScheduledJobRetryExhaustionEntity> findLatestAcrossAllJobs() {
        return retryExhaustionRepository.findAllByOrderByExhaustedAtDesc().stream()
                .collect(Collectors.toMap(
                        ScheduledJobRetryExhaustionEntity::getJobId,
                        Function.identity(),
                        (first, second) -> first));
    }

    public List<ScheduledJobRetryExhaustionEntity> findAllSince(Instant exhaustedAt) {
        return retryExhaustionRepository.findAllByExhaustedAtGreaterThanEqualOrderByExhaustedAtDesc(exhaustedAt);
    }

    public Page<ScheduledJobRetryExhaustionEntity> findPage(Pageable pageable) {
        return retryExhaustionRepository.findAllByOrderByExhaustedAtDesc(pageable);
    }
}
