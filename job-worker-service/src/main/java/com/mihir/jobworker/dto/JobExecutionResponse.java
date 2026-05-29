package com.mihir.jobworker.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.mihir.jobworker.model.ExecutionStatus;
import com.mihir.jobworker.model.JobName;

import java.time.Instant;

public record JobExecutionResponse(
        Long jobId,
        String executionId,
        String logTag,
        Instant scheduledAt,
        JobName jobName,
        ExecutionStatus status,
        JsonNode payload,
        JsonNode result,
        String failureReason,
        Instant processedAt,
        Instant createdAt,
        Instant updatedAt) {
}
