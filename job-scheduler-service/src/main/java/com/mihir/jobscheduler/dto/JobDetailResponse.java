package com.mihir.jobscheduler.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.mihir.jobscheduler.model.JobName;
import com.mihir.jobscheduler.model.JobStatus;
import com.mihir.jobscheduler.model.ScheduleType;

import java.time.Instant;

public record JobDetailResponse(
        Long jobId,
        JobName jobName,
        ScheduleType scheduleType,
        JobStatus status,
        int maxRetries,
        String createdBy,
        Instant createdAt,
        JsonNode schedule,
        JsonNode payload) {
}
