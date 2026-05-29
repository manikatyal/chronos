package com.mihir.jobworker.service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mihir.jobworker.dto.JobExecutionMessage;
import com.mihir.jobworker.model.JobName;
import com.mihir.jobworker.service.EmailDeliveryService;
import com.mihir.jobworker.service.ExecutionResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EmailJobHandler extends AbstractPayloadValidator implements JobHandler {

    private final EmailDeliveryService emailDeliveryService;

    public EmailJobHandler(ObjectMapper objectMapper, EmailDeliveryService emailDeliveryService) {
        super(objectMapper);
        this.emailDeliveryService = emailDeliveryService;
    }

    @Override
    public JobName jobName() {
        return JobName.SEND_EMAIL;
    }

    @Override
    public ExecutionResult execute(JobExecutionMessage message) {
        String subject = optionalText(message.payload(), "subject");
        String body = optionalText(message.payload(), "body");
        if (subject == null || subject.isBlank() || body == null || body.isBlank()) {
            throw new com.mihir.jobworker.exception.JobProcessingException(
                    "payload.subject and payload.body are required");
        }
        if (message.payload().has("emails")) {
            List<String> emails = requireTextArray(message.payload(), "emails");
            return new ExecutionResult(
                    emailDeliveryService.send(message.jobId(), message.executionId(), emails, subject, body),
                    "Bulk email sent successfully");
        }

        String email = requireText(message.payload(), "email");
        return new ExecutionResult(
                emailDeliveryService.send(message.jobId(), message.executionId(), email, subject, body),
                "Email sent successfully");
    }
}
