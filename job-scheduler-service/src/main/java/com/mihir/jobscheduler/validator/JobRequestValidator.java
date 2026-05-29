package com.mihir.jobscheduler.validator;

import com.mihir.jobscheduler.annotation.ValidJobRequest;
import com.mihir.jobscheduler.dto.JobRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;

public class JobRequestValidator implements ConstraintValidator<ValidJobRequest, JobRequest> {

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
        valid &= require(isValidCron(request.schedule().cronExpression()), context, "schedule.cronExpression is invalid");
        return valid;
    }

    private boolean isValidCron(String expression) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        String[] fields = expression.trim().split("\\s+");
        String normalized = fields.length == 5 ? "0 " + expression.trim() : expression.trim();
        try {
            CronExpression.parse(normalized);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean require(boolean condition, ConstraintValidatorContext context, String message) {
        if (condition) {
            return true;
        }
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
