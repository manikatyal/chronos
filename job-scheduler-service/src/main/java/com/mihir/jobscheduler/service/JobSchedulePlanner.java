package com.mihir.jobscheduler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mihir.jobscheduler.dto.JobSchedule;
import com.mihir.jobscheduler.entity.JobEntity;
import com.mihir.jobscheduler.exception.JobValidationException;
import com.mihir.jobscheduler.model.ScheduleType;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Component
public class JobSchedulePlanner {

    private final ObjectMapper objectMapper;

    public JobSchedulePlanner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Instant initialNextRunAt(com.mihir.jobscheduler.dto.JobRequest request) {
        return calculateNextRun(request.scheduleType(), request.schedule());
    }

    public Instant nextRunAt(JobEntity job) {
        JobSchedule schedule = readSchedule(job.getScheduleJson());
        return calculateNextRun(job.getScheduleType(), schedule);
    }

    private Instant calculateNextRun(ScheduleType scheduleType, JobSchedule schedule) {
        return switch (scheduleType) {
            case DELAYED -> Instant.now().plusSeconds(schedule.delaySeconds());
            case ONE_TIME -> schedule.runAt();
            case CRON -> nextCronRun(schedule.cronExpression());
        };
    }

    private Instant nextCronRun(String cronExpression) {
        String normalized = normalizeCron(cronExpression);
        ZonedDateTime next = CronExpression.parse(normalized).next(ZonedDateTime.now(ZoneOffset.UTC));
        if (next == null) {
            throw new JobValidationException("schedule.cronExpression never fires");
        }
        return next.toInstant();
    }

    private String normalizeCron(String expression) {
        String trimmed = expression.trim();
        String[] fields = trimmed.split("\\s+");
        return fields.length == 5 ? "0 " + trimmed : trimmed;
    }

    private JobSchedule readSchedule(String scheduleJson) {
        try {
            return objectMapper.readValue(scheduleJson, JobSchedule.class);
        } catch (JsonProcessingException exception) {
            throw new JobValidationException("Unable to deserialize stored schedule");
        }
    }
}
