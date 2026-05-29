package com.mihir.jobworker.service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mihir.jobworker.dto.JobExecutionMessage;
import com.mihir.jobworker.exception.JobProcessingException;
import com.mihir.jobworker.model.JobName;
import com.mihir.jobworker.model.ReportTimeWindow;
import com.mihir.jobworker.service.ExecutionResult;
import com.mihir.jobworker.service.FailedJobsReportService;
import com.mihir.jobworker.service.RealtimeHub;
import org.springframework.stereotype.Component;

@Component
public class GenerateReportJobHandler extends AbstractPayloadValidator implements JobHandler {

    private final FailedJobsReportService failedJobsReportService;
    private final RealtimeHub realtimeHub;

    public GenerateReportJobHandler(
            ObjectMapper objectMapper,
            FailedJobsReportService failedJobsReportService,
            RealtimeHub realtimeHub) {
        super(objectMapper);
        this.failedJobsReportService = failedJobsReportService;
        this.realtimeHub = realtimeHub;
    }

    @Override
    public JobName jobName() {
        return JobName.GENERATE_REPORT;
    }

    @Override
    public ExecutionResult execute(JobExecutionMessage message) {
        String reportType = requireText(message.payload(), "reportType");
        String window = requireText(message.payload(), "window");

        if (!"FAILED_JOBS_REPORT".equals(reportType)) {
            throw new JobProcessingException("payload.reportType must be FAILED_JOBS_REPORT");
        }

        ReportTimeWindow reportTimeWindow;
        try {
            reportTimeWindow = ReportTimeWindow.valueOf(window);
        } catch (IllegalArgumentException exception) {
            throw new JobProcessingException("payload.window is unsupported: " + window);
        }

        var result = failedJobsReportService.generateFailedJobsReport(message.executionId(), reportTimeWindow);
        realtimeHub.pushReport(message.jobId(), message.executionId(), result);
        return new ExecutionResult(
                result,
                "Failed jobs report generated for " + reportTimeWindow.name());
    }
}
