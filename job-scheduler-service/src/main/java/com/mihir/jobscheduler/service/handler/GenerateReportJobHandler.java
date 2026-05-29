package com.mihir.jobscheduler.service.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.mihir.jobscheduler.exception.JobValidationException;
import com.mihir.jobscheduler.model.JobName;
import com.mihir.jobscheduler.model.ReportTimeWindow;
import org.springframework.stereotype.Component;

@Component
public class GenerateReportJobHandler extends AbstractPayloadValidator {

    @Override
    public JobName supports() {
        return JobName.GENERATE_REPORT;
    }

    @Override
    public void validatePayload(JsonNode payload) {
        requireText(payload, "reportType");
        requireText(payload, "window");

        String reportType = payload.get("reportType").asText();
        if (!"FAILED_JOBS_REPORT".equals(reportType)) {
            throw new JobValidationException("payload.reportType must be FAILED_JOBS_REPORT");
        }

        String window = payload.get("window").asText();
        if (!ReportTimeWindow.SUPPORTED_VALUES.contains(window)) {
            throw new JobValidationException("payload.window must be one of " + ReportTimeWindow.SUPPORTED_VALUES);
        }
    }
}
