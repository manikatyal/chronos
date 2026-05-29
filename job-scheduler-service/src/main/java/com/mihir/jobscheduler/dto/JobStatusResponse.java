package com.mihir.jobscheduler.dto;

import com.mihir.jobscheduler.model.JobName;
import com.mihir.jobscheduler.model.JobStatus;
import com.mihir.jobscheduler.model.ScheduleType;

import java.time.Instant;

public record JobStatusResponse(
        Long jobId,
        JobName jobName,
        ScheduleType scheduleType,
        JobStatus status,
        int maxRetries,
        String createdBy,
        Instant createdAt,
        Instant nextRunAt,
        int retryCount,
        Instant firstClaimedAt,
        String lockedBy,
        Instant lockedUntil,
        boolean editable,
        boolean deletable) {
}
