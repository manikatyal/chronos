package com.mihir.jobworker.service;

import com.mihir.jobworker.config.WorkerProcessingProperties;
import com.mihir.jobworker.dto.JobExecutionMessage;
import com.mihir.jobworker.dto.WorkerFailureReportRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class WorkerFailureBackdoorService {

    private static final Logger log = LoggerFactory.getLogger(WorkerFailureBackdoorService.class);

    private final RestClient restClient;
    private final WorkerProcessingProperties properties;

    public void reportFailure(JobExecutionMessage message, String failureReason) {
        WorkerProcessingProperties.Backdoor backdoor = properties.backdoor();
        if (backdoor == null) {
            log.warn("Worker failure backdoor is not configured logTag={} jobId={} executionId={}",
                    message.logTag(), message.jobId(), message.executionId());
            return;
        }
        String endpoint = backdoor.schedulerFailureEndpoint();
        String internalKey = backdoor.internalKey();
        if (endpoint == null || endpoint.isBlank() || internalKey == null || internalKey.isBlank()) {
            log.warn("Worker failure backdoor is not configured logTag={} jobId={} executionId={}",
                    message.logTag(), message.jobId(), message.executionId());
            return;
        }

        WorkerFailureReportRequest request = new WorkerFailureReportRequest(
                message.jobId(),
                message.executionId(),
                message.logTag(),
                backdoor.workerId(),
                failureReason,
                Instant.now());

        try {
            restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Key", internalKey)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Worker failure reported to scheduler logTag={} jobId={} executionId={} endpoint={}",
                    message.logTag(), message.jobId(), message.executionId(), endpoint);
        } catch (RestClientException exception) {
            log.error("Worker failure report failed logTag={} jobId={} executionId={} endpoint={} reason={}",
                    message.logTag(), message.jobId(), message.executionId(), endpoint, exception.getMessage());
        }
    }
}
