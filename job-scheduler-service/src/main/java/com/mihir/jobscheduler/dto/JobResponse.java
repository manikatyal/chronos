package com.mihir.jobscheduler.dto;

import com.mihir.jobscheduler.model.JobName;
import com.mihir.jobscheduler.model.JobStatus;
import com.mihir.jobscheduler.model.ScheduleType;

import java.time.Instant;
import java.util.List;

public record JobResponse(
        Long jobId,
        JobName jobName,
        ScheduleType scheduleType,
        JobStatus status,
        int maxRetries,
        String createdBy,
        Instant createdAt,
        boolean alreadyExists,
        List<JobFieldMismatch> mismatches) {
}
