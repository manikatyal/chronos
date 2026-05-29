package com.mihir.jobscheduler.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "chronos.scheduler")
public record ChronosSchedulerProperties(
        @Min(1) int batchSize,
        @NotNull Duration leaseDuration,
        @Min(1) long pollIntervalMs,
        @NotNull Duration retryGap,
        @NotNull Duration cronRetryGap) {
}
