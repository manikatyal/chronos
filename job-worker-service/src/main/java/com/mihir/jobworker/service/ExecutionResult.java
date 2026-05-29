package com.mihir.jobworker.service;

import com.fasterxml.jackson.databind.JsonNode;

public record ExecutionResult(
        JsonNode result,
        String summary) {
}
