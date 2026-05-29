package com.mihir.jobscheduler.service.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.mihir.jobscheduler.model.JobName;
import org.springframework.stereotype.Component;

@Component
public class EmailJobHandler extends AbstractPayloadValidator {

    @Override
    public JobName supports() {
        return JobName.SEND_EMAIL;
    }

    @Override
    public void validatePayload(JsonNode payload) {
        String subject = optionalText(payload, "subject");
        String body = optionalText(payload, "body");
        if (subject == null || subject.isBlank() || body == null || body.isBlank()) {
            throw new com.mihir.jobscheduler.exception.JobValidationException(
                    "payload.subject and payload.body are required");
        }

        boolean hasSingleRecipient = payload != null && payload.hasNonNull("email");
        boolean hasBulkRecipients = payload != null && payload.has("emails");

        if (hasSingleRecipient == hasBulkRecipients) {
            throw new com.mihir.jobscheduler.exception.JobValidationException(
                    "payload must contain exactly one of email or emails");
        }

        if (hasSingleRecipient) {
            requireText(payload, "email");
            return;
        }

        requireTextArray(payload, "emails");
    }
}
