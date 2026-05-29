package com.mihir.jobscheduler.dto;

import com.mihir.jobscheduler.model.FailureSource;
import com.mihir.jobscheduler.model.JobName;
import com.mihir.jobscheduler.model.JobStatus;
import com.mihir.jobscheduler.model.ScheduleType;

import java.time.Instant;

public record FailedJobResponse(
        Long jobId,
        JobName jobName,
        ScheduleType scheduleType,
        JobStatus status,
        int maxRetries,
        String createdBy,
        Instant createdAt,
        FailureSource failureSource,
        String executionId,
        String logTag,
        String workerId,
        Integer retryCount,
        String failureReason,
        Instant exhaustedAt) {
}
