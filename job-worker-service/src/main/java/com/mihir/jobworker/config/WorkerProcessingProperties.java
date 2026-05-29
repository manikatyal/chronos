package com.mihir.jobworker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chronos.worker")
public record WorkerProcessingProperties(
        Email email,
        News news,
        Endpoints endpoints,
        Backdoor backdoor,
        Reports reports) {

    public record Email(String fromAddress) {
    }

    public record News(String indiaHeadlinesUrl, String goldPriceUrl, String stockUpdatesUrl) {
    }

    public record Endpoints(
            String notification,
            String news,
            String payment,
            String order,
            String hotel,
            String sync) {
    }

    public record Backdoor(
            String schedulerFailureEndpoint,
            String failedJobsEndpoint,
            String internalKey,
            String workerId) {
    }

    public record Reports(String outputDirectory) {
    }
}
