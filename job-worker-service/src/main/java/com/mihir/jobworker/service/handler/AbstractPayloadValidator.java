package com.mihir.jobworker.service.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mihir.jobworker.exception.JobProcessingException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

abstract class AbstractPayloadValidator {

    protected final ObjectMapper objectMapper;

    protected AbstractPayloadValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    protected String requireText(JsonNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        if (value == null || value.asText().isBlank()) {
            throw new JobProcessingException("payload." + fieldName + " is required");
        }
        return value.asText();
    }

    protected BigDecimal requireDecimal(JsonNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        if (value == null || !value.isNumber()) {
            throw new JobProcessingException("payload." + fieldName + " must be numeric");
        }
        return value.decimalValue();
    }

    protected List<String> requireTextArray(JsonNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        if (value == null || !value.isArray() || value.isEmpty()) {
            throw new JobProcessingException("payload." + fieldName + " must be a non-empty array");
        }

        List<String> values = new ArrayList<>();
        for (JsonNode node : value) {
            if (node == null || node.asText().isBlank()) {
                throw new JobProcessingException("payload." + fieldName + " must contain non-blank values");
            }
            values.add(node.asText());
        }
        return values;
    }

    protected String optionalText(JsonNode payload, String fieldName) {
        JsonNode node = payload.get(fieldName);
        if (node == null) {
            return null;
        }
        String value = node.asText();
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    protected ObjectNode resultNode(String action) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("action", action);
        return node;
    }

    protected String stableReference(String prefix, Object... values) {
        StringBuilder seed = new StringBuilder(prefix);
        for (Object value : values) {
            seed.append('|').append(value);
        }
        UUID uuid = UUID.nameUUIDFromBytes(seed.toString().getBytes(StandardCharsets.UTF_8));
        return prefix + "-" + uuid.toString().substring(0, 12);
    }

    protected int boundedMetric(String seed, int minimum, int spread) {
        int value = Math.abs(seed.hashCode());
        return minimum + (value % spread);
    }

    protected LocalDate todayUtc() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    protected Instant nowUtc() {
        return Instant.now();
    }
}
