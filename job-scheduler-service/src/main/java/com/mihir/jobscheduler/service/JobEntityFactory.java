package com.mihir.jobscheduler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mihir.jobscheduler.dto.JobRequest;
import com.mihir.jobscheduler.entity.JobEntity;
import com.mihir.jobscheduler.exception.JobValidationException;
import com.mihir.jobscheduler.model.JobStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobEntityFactory {

    private final ObjectMapper objectMapper;

    public JobEntity create(JobRequest request, String createdBy) {
        String scheduleJson = writeValue(request.schedule());
        String payloadJson = writeValue(request.payload());

        return JobEntity.builder()
                .createdBy(createdBy)
                .jobName(request.jobName())
                .scheduleType(request.scheduleType())
                .scheduleJson(scheduleJson)
                .payloadJson(payloadJson)
                .maxRetries(request.maxRetries())
                .status(JobStatus.SCHEDULED)
                .build();
    }

    public void apply(JobEntity jobEntity, JobRequest request) {
        jobEntity.setJobName(request.jobName());
        jobEntity.setScheduleType(request.scheduleType());
        jobEntity.setScheduleJson(writeValue(request.schedule()));
        jobEntity.setPayloadJson(writeValue(request.payload()));
        jobEntity.setMaxRetries(request.maxRetries());
        jobEntity.setStatus(JobStatus.SCHEDULED);
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new JobValidationException("Unable to serialize request content");
        }
    }
}
