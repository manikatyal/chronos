package com.mihir.jobscheduler.dto;

public record JobFieldMismatch(
        String field,
        Object requestedValue,
        Object existingValue) {
}
