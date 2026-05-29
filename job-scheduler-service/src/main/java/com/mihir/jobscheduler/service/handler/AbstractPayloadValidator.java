package com.mihir.jobscheduler.service.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.mihir.jobscheduler.exception.JobValidationException;

public abstract class AbstractPayloadValidator implements JobHandler {

    protected void requireText(JsonNode payload, String field) {
        if (payload == null || !payload.hasNonNull(field) || payload.get(field).asText().isBlank()) {
            throw new JobValidationException("payload." + field + " is required");
        }
    }

    protected String optionalText(JsonNode payload, String field) {
        if (payload == null || !payload.has(field)) {
            return null;
        }
        String value = payload.get(field).asText();
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    protected void requireNumber(JsonNode payload, String field) {
        if (payload == null || !payload.hasNonNull(field) || !payload.get(field).isNumber()) {
            throw new JobValidationException("payload." + field + " must be a number");
        }
    }

    protected void requireTextArray(JsonNode payload, String field) {
        if (payload == null || !payload.has(field) || !payload.get(field).isArray() || payload.get(field).isEmpty()) {
            throw new JobValidationException("payload." + field + " must be a non-empty array");
        }

        for (JsonNode value : payload.get(field)) {
            if (value == null || value.asText().isBlank()) {
                throw new JobValidationException("payload." + field + " must contain non-blank values");
            }
        }
    }
}
