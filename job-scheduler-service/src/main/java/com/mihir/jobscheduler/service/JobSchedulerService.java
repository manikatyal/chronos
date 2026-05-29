package com.mihir.jobscheduler.service;

import com.mihir.jobscheduler.config.ChronosSchedulerProperties;
import com.mihir.jobscheduler.kafka.JobExecutionPublisher;
import com.mihir.jobscheduler.manager.ScheduledJobLeaseClaimManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(JobSchedulerService.class);

    private final ScheduledJobLeaseClaimManager claimManager;
    private final JobExecutionPublisher executionPublisher;
    private final ChronosSchedulerProperties properties;
    private final WorkerIdentity workerIdentity;
    private final TaskExecutor schedulerTaskExecutor;

    @Scheduled(fixedDelayString = "${chronos.scheduler.poll-interval-ms:1000}")
    public void pollAndDispatch() {
        List<Long> claimedJobIds = claimManager.claimDueJobs(
                workerIdentity.value(),
                properties.leaseDuration(),
                properties.batchSize());

        if (claimedJobIds.isEmpty()) {
            return;
        }

        for (Long jobId : claimedJobIds) {
            log.info("Job Claimed jobId={} workerId={}", jobId, workerIdentity.value());
            schedulerTaskExecutor.execute(() -> executionPublisher.publish(jobId, workerIdentity.value()));
        }
    }
}
