package com.mihir.jobscheduler.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record JobSchedule(
        @Min(1) Long delaySeconds,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant runAt,
        @Size(max = 64) String cronExpression) {
}
