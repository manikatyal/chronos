package com.mihir.jobworker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mihir.jobworker.exception.JobProcessingException;
import com.mihir.jobworker.model.JobName;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;

@Service
public class RemoteJobEndpointService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RemoteJobEndpointService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public ObjectNode post(Long jobId, String executionId, JobName jobName, String endpoint, JsonNode payload) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new JobProcessingException("Endpoint is not configured for jobName: " + jobName);
        }

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("jobId", jobId);
        requestBody.put("executionId", executionId);
        requestBody.put("jobName", jobName.name());
        requestBody.put("requestedAt", Instant.now().toString());
        requestBody.set("payload", payload);

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Idempotency-Key", executionId)
                    .header("X-Job-Id", String.valueOf(jobId))
                    .header("X-Job-Name", jobName.name())
                    .body(requestBody)
                    .retrieve()
                    .toEntity(String.class);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("action", jobName.name() + "_REQUEST_SENT");
            result.put("endpoint", endpoint);
            result.put("httpStatus", response.getStatusCode().value());
            result.put("processedAt", Instant.now().toString());
            if (response.getBody() != null && !response.getBody().isBlank()) {
                result.put("downstreamResponse", response.getBody());
            }
            return result;
        } catch (RestClientException exception) {
            throw new JobProcessingException("Remote call failed for " + jobName + ": " + exception.getMessage());
        }
    }
}
