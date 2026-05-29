package com.mihir.jobworker.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.mihir.jobworker.model.JobName;
import com.mihir.jobworker.model.ScheduleType;

import java.time.Instant;

public record JobExecutionMessage(
        Long jobId,
        String executionId,
        String logTag,
        Instant scheduledAt,
        JobName jobName,
        ScheduleType scheduleType,
        JsonNode payload) {
}
