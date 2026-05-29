package com.mihir.jobscheduler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record WorkerFailureReportRequest(
        @NotNull Long jobId,
        @NotBlank String executionId,
        @NotBlank String logTag,
        @NotBlank String workerId,
        @NotBlank String failureReason,
        @NotNull Instant failedAt) {
}
