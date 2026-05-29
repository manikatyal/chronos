package com.mihir.jobscheduler.service;

import com.mihir.jobscheduler.config.ChronosSchedulerProperties;
import com.mihir.jobscheduler.model.FailureSource;
import com.mihir.jobscheduler.entity.JobEntity;
import com.mihir.jobscheduler.entity.ScheduledJobLeaseEntity;
import com.mihir.jobscheduler.entity.ScheduledJobRetryExhaustionEntity;
import com.mihir.jobscheduler.manager.JobManager;
import com.mihir.jobscheduler.manager.ScheduledJobLeaseManager;
import com.mihir.jobscheduler.manager.ScheduledJobRetryExhaustionManager;
import com.mihir.jobscheduler.model.ScheduleType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class JobExecutionStateService {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionStateService.class);
    private final JobManager jobManager;
    private final ScheduledJobLeaseManager scheduledJobLeaseManager;
    private final ScheduledJobRetryExhaustionManager retryExhaustionManager;
    private final JobSchedulePlanner schedulePlanner;
    private final ChronosSchedulerProperties properties;

    @Transactional
    public void markPublished(JobEntity job, String workerId, String executionId, String logTag) {
        Instant nextRunAt;
        if (job.getScheduleType() == com.mihir.jobscheduler.model.ScheduleType.CRON) {
            nextRunAt = schedulePlanner.nextRunAt(job);
            jobManager.markScheduled(job);
        } else {
            nextRunAt = null;
            jobManager.markCompleted(job);
        }
        scheduledJobLeaseManager.markSuccess(job.getJobId(), workerId, nextRunAt);
        log.info("Dispatch completed logTag={} jobId={} executionId={} workerId={} nextRunAt={}",
                logTag, job.getJobId(), executionId, workerId, nextRunAt);
    }

    @Transactional
    public void triggerRetry(JobEntity job, String workerId, String executionId, String logTag, Throwable failure) {
        ScheduledJobLeaseEntity lease = scheduledJobLeaseManager.findByJobId(job.getJobId())
                .orElseThrow(() -> new IllegalStateException("Lease not found for job: " + job.getJobId()));
        int nextRetryCount = lease.getRetryCount() + 1;
        if (nextRetryCount >= job.getMaxRetries()) {
            jobManager.markFailed(job);
            scheduledJobLeaseManager.markExhausted(job.getJobId(), workerId);
            retryExhaustionManager.save(ScheduledJobRetryExhaustionEntity.builder()
                    .jobId(job.getJobId())
                    .workerId(workerId)
                    .failureSource(FailureSource.SCHEDULER)
                    .executionId(executionId)
                    .logTag(logTag)
                    .retryCount(nextRetryCount)
                    .failureReason(trimReason(failure))
                    .exhaustedAt(Instant.now())
                    .build());
            log.error("Retry Exhausted logTag={} jobId={} executionId={} workerId={} retryCount={} reason={}",
                    logTag, job.getJobId(), executionId, workerId, nextRetryCount, failure.getMessage());
            return;
        }

        Instant retryAt = Instant.now().plus(retryGapFor(job.getScheduleType()));
        scheduledJobLeaseManager.markRetry(job.getJobId(), workerId, retryAt);
        log.warn("Retry Triggered logTag={} jobId={} executionId={} workerId={} retryAt={} reason={}",
                logTag, job.getJobId(), executionId, workerId, retryAt, failure.getMessage());
    }

    private java.time.Duration retryGapFor(ScheduleType scheduleType) {
        return scheduleType == ScheduleType.CRON ? properties.cronRetryGap() : properties.retryGap();
    }

    private String trimReason(Throwable failure) {
        if (failure == null || failure.getMessage() == null) {
            return "Unknown failure";
        }
        String message = failure.getMessage();
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
