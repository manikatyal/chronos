package com.mihir.jobscheduler.validator;

import com.mihir.jobscheduler.annotation.ValidJobRequest;
import com.mihir.jobscheduler.dto.JobRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.Duration;

public class JobRequestValidator implements ConstraintValidator<ValidJobRequest, JobRequest> {

    private static final long MIN_CRON_GAP_SECONDS = 60;

    @Override
    public boolean isValid(JobRequest request, ConstraintValidatorContext context) {
        if (request == null || request.scheduleType() == null || request.schedule() == null) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        return switch (request.scheduleType()) {
            case DELAYED -> validateDelayed(request, context);
            case ONE_TIME -> validateOneTime(request, context);
            case CRON -> validateCron(request, context);
        };
    }

    private boolean validateDelayed(JobRequest request, ConstraintValidatorContext context) {
        boolean valid = true;
        valid &= require(request.schedule().delaySeconds() != null, context, "schedule.delaySeconds is required for DELAYED");
        valid &= require(request.schedule().runAt() == null, context, "schedule.runAt must be null for DELAYED");
        valid &= require(request.schedule().cronExpression() == null, context, "schedule.cronExpression must be null for DELAYED");
        return valid;
    }

    private boolean validateOneTime(JobRequest request, ConstraintValidatorContext context) {
        boolean valid = true;
        valid &= require(request.schedule().delaySeconds() == null, context, "schedule.delaySeconds must be null for ONE_TIME");
        valid &= require(request.schedule().runAt() != null, context, "schedule.runAt is required for ONE_TIME");
        valid &= require(request.schedule().cronExpression() == null, context, "schedule.cronExpression must be null for ONE_TIME");
        valid &= require(request.schedule().runAt() == null || request.schedule().runAt().isAfter(Instant.now()),
                context, "schedule.runAt must be in the future");
        return valid;
    }

    private boolean validateCron(JobRequest request, ConstraintValidatorContext context) {
        boolean valid = true;
        valid &= require(request.schedule().delaySeconds() == null, context, "schedule.delaySeconds must be null for CRON");
        valid &= require(request.schedule().runAt() == null, context, "schedule.runAt must be null for CRON");
        valid &= require(request.schedule().cronExpression() != null && !request.schedule().cronExpression().isBlank(),
                context, "schedule.cronExpression is required for CRON");
        String cronError = cronError(request.schedule().cronExpression());
        valid &= require(cronError == null, context, cronError);
        return valid;
    }

    private String cronError(String expression) {
        if (expression == null || expression.isBlank()) {
            return "schedule.cronExpression is invalid";
        }
        String[] fields = expression.trim().split("\\s+");
        String normalized = fields.length == 5 ? "0 " + expression.trim() : expression.trim();
        CronExpression cron;
        try {
            cron = CronExpression.parse(normalized);
        } catch (IllegalArgumentException exception) {
            return "schedule.cronExpression is invalid";
        }
        ZonedDateTime first = cron.next(ZonedDateTime.now(ZoneOffset.UTC));
        if (first == null) {
            return "schedule.cronExpression never fires";
        }
        ZonedDateTime second = cron.next(first);
        if (second != null && Duration.between(first, second).getSeconds() < MIN_CRON_GAP_SECONDS) {
            return "schedule.cronExpression must not run more than once a minute";
        }
        return null;
    }

    private boolean require(boolean condition, ConstraintValidatorContext context, String message) {
        if (condition) {
            return true;
        }
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
