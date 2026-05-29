package com.mihir.jobscheduler.annotation;

import com.mihir.jobscheduler.validator.JobRequestValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = JobRequestValidator.class)
public @interface ValidJobRequest {

    String message() default "Invalid job request";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
