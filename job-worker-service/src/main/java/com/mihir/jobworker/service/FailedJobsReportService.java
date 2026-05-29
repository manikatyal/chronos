package com.mihir.jobworker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mihir.jobworker.config.WorkerProcessingProperties;
import com.mihir.jobworker.exception.JobProcessingException;
import com.mihir.jobworker.model.ReportTimeWindow;
import com.mihir.jobworker.dto.SchedulerFailedJobResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class FailedJobsReportService {

    private static final DateTimeFormatter FILE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final SchedulerFailedJobsClient schedulerFailedJobsClient;
    private final WorkerProcessingProperties processingProperties;
    private final ObjectMapper objectMapper;

    public FailedJobsReportService(
            SchedulerFailedJobsClient schedulerFailedJobsClient,
            WorkerProcessingProperties processingProperties,
            ObjectMapper objectMapper) {
        this.schedulerFailedJobsClient = schedulerFailedJobsClient;
        this.processingProperties = processingProperties;
        this.objectMapper = objectMapper;
    }

    public ObjectNode generateFailedJobsReport(String executionId, ReportTimeWindow window) {
        Instant generatedAt = Instant.now();
        Instant fromTime = generatedAt.minus(window.duration());
        List<SchedulerFailedJobResponse> failedExecutions = schedulerFailedJobsClient.fetchFailedJobsSince(fromTime);

        Path reportDirectory = resolveReportDirectory();
        String fileName = "failed-jobs-report-" + window.name().toLowerCase() + "-"
                + FILE_TIME_FORMATTER.format(generatedAt) + ".csv";
        Path reportPath = reportDirectory.resolve(fileName);

        try {
            Files.createDirectories(reportDirectory);
            Files.writeString(reportPath, buildCsv(failedExecutions), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new JobProcessingException("Unable to generate failed jobs report: " + exception.getMessage());
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.put("action", "REPORT_GENERATED");
        result.put("reportType", "FAILED_JOBS_REPORT");
        result.put("window", window.name());
        result.put("executionId", executionId);
        result.put("fromTime", fromTime.toString());
        result.put("generatedAt", generatedAt.toString());
        result.put("rowCount", failedExecutions.size());
        result.put("filePath", reportPath.toAbsolutePath().toString());
        result.put("fileName", reportPath.getFileName().toString());
        return result;
    }

    private Path resolveReportDirectory() {
        String configured = processingProperties.reports() != null
                ? processingProperties.reports().outputDirectory()
                : null;
        if (configured == null || configured.isBlank()) {
            return Path.of("generated-reports");
        }
        return Path.of(configured);
    }

    private String buildCsv(List<SchedulerFailedJobResponse> failedExecutions) {
        StringBuilder builder = new StringBuilder();
        builder.append("jobId,jobName,scheduleType,status,failureSource,executionId,logTag,workerId,retryCount,failureReason,failedAt,createdBy,createdAt\n");
        for (SchedulerFailedJobResponse execution : failedExecutions) {
            builder.append(csv(execution.jobId()))
                    .append(',').append(csv(execution.jobName()))
                    .append(',').append(csv(execution.scheduleType()))
                    .append(',').append(csv(execution.status()))
                    .append(',').append(csv(execution.failureSource()))
                    .append(',').append(csv(execution.executionId()))
                    .append(',').append(csv(execution.logTag()))
                    .append(',').append(csv(execution.workerId()))
                    .append(',').append(csv(execution.retryCount()))
                    .append(',').append(csv(execution.failureReason()))
                    .append(',').append(csv(execution.exhaustedAt()))
                    .append(',').append(csv(execution.createdBy()))
                    .append(',').append(csv(execution.createdAt()))
                    .append('\n');
        }
        return builder.toString();
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
