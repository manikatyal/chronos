package com.mihir.jobworker.dto;

import java.time.Instant;

public record WorkerFailureReportRequest(
        Long jobId,
        String executionId,
        String logTag,
        String workerId,
        String failureReason,
        Instant failedAt) {
}
