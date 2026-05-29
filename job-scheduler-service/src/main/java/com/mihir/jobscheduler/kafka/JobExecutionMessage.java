package com.mihir.jobscheduler.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.mihir.jobscheduler.model.JobName;
import com.mihir.jobscheduler.model.ScheduleType;

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
