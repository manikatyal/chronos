package com.mihir.jobworker.service;

import com.mihir.jobworker.config.WorkerProcessingProperties;
import com.mihir.jobworker.dto.SchedulerFailedJobResponse;
import com.mihir.jobworker.exception.JobProcessingException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;

@Service
public class SchedulerFailedJobsClient {

    private static final ParameterizedTypeReference<List<SchedulerFailedJobResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final WorkerProcessingProperties properties;

    public SchedulerFailedJobsClient(RestClient restClient, WorkerProcessingProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public List<SchedulerFailedJobResponse> fetchFailedJobsSince(Instant fromTime) {
        WorkerProcessingProperties.Backdoor backdoor = properties.backdoor();
        if (backdoor == null
                || backdoor.failedJobsEndpoint() == null
                || backdoor.failedJobsEndpoint().isBlank()
                || backdoor.internalKey() == null
                || backdoor.internalKey().isBlank()) {
            throw new JobProcessingException("Scheduler failed jobs backdoor is not configured");
        }

        try {
            String requestUri = UriComponentsBuilder
                    .fromUriString(backdoor.failedJobsEndpoint())
                    .queryParam("fromTime", fromTime.toString())
                    .build()
                    .toUriString();
            List<SchedulerFailedJobResponse> response = restClient.get()
                    .uri(requestUri)
                    .header("X-Internal-Key", backdoor.internalKey())
                    .retrieve()
                    .body(RESPONSE_TYPE);
            return response == null ? List.of() : response;
        } catch (RestClientException exception) {
            throw new JobProcessingException("Unable to fetch failed jobs from scheduler: " + exception.getMessage());
        }
    }
}
