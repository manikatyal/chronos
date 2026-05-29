package com.mihir.jobscheduler.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.mihir.jobscheduler.annotation.ValidJobRequest;
import com.mihir.jobscheduler.model.JobName;
import com.mihir.jobscheduler.model.ScheduleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@ValidJobRequest
public record JobRequest(
        @NotNull JobName jobName,
        @NotNull ScheduleType scheduleType,
        @Valid @NotNull JobSchedule schedule,
        @NotNull JsonNode payload,
        @Min(0) @Max(10) Integer maxRetries
        ) {

    public JobRequest {
        maxRetries = maxRetries == null ? 3 : maxRetries;
    }

}
