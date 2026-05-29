package com.mihir.jobworker.dto;

import java.time.Instant;

public record SchedulerFailedJobResponse(
        Long jobId,
        String jobName,
        String scheduleType,
        String status,
        int maxRetries,
        String createdBy,
        Instant createdAt,
        String failureSource,
        String executionId,
        String logTag,
        String workerId,
        Integer retryCount,
        String failureReason,
        Instant exhaustedAt) {
}
