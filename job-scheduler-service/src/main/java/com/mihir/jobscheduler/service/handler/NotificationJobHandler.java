package com.mihir.jobscheduler.service.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.mihir.jobscheduler.model.JobName;
import org.springframework.stereotype.Component;

@Component
public class NotificationJobHandler extends AbstractPayloadValidator {

    @Override
    public JobName supports() {
        return JobName.SEND_NOTIFICATION;
    }

    @Override
    public void validatePayload(JsonNode payload) {
        requireText(payload, "title");
        requireText(payload, "message");

        boolean hasSingleRecipient = payload != null && payload.hasNonNull("userId");
        boolean hasBulkRecipients = payload != null && payload.has("userIds");
        boolean sendToAll = payload != null && payload.path("sendToAll").asBoolean(false);

        int modes = 0;
        if (hasSingleRecipient) modes += 1;
        if (hasBulkRecipients) modes += 1;
        if (sendToAll) modes += 1;

        if (modes != 1) {
            throw new com.mihir.jobscheduler.exception.JobValidationException(
                    "payload must contain exactly one of userId, userIds, or sendToAll");
        }

        if (hasSingleRecipient) {
            requireText(payload, "userId");
            return;
        }

        if (hasBulkRecipients) {
            requireTextArray(payload, "userIds");
        }
    }
}
