package com.mihir.jobworker.service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mihir.jobworker.dto.JobExecutionMessage;
import com.mihir.jobworker.model.JobName;
import com.mihir.jobworker.service.ExecutionResult;
import com.mihir.jobworker.service.RealtimeHub;
import org.springframework.stereotype.Component;

@Component
public class NotificationJobHandler extends AbstractPayloadValidator implements JobHandler {

    private final RealtimeHub realtimeHub;

    public NotificationJobHandler(
            ObjectMapper objectMapper,
            RealtimeHub realtimeHub) {
        super(objectMapper);
        this.realtimeHub = realtimeHub;
    }

    @Override
    public JobName jobName() {
        return JobName.SEND_NOTIFICATION;
    }

    @Override
    public ExecutionResult execute(JobExecutionMessage message) {
        requireText(message.payload(), "title");
        requireText(message.payload(), "message");
        boolean hasBulkRecipients = message.payload().has("userIds");
        boolean hasSingleRecipient = message.payload().hasNonNull("userId");
        boolean sendToAll = message.payload().path("sendToAll").asBoolean(false);
        String senderRole = message.payload().path("senderRole").asText("");
        int modes = 0;
        if (hasBulkRecipients) modes += 1;
        if (hasSingleRecipient) modes += 1;
        if (sendToAll) modes += 1;
        if (modes != 1) {
            throw new com.mihir.jobworker.exception.JobProcessingException(
                    "payload must contain exactly one of userId, userIds, or sendToAll");
        }
        if (sendToAll && !"ADMIN".equalsIgnoreCase(senderRole)) {
            throw new com.mihir.jobworker.exception.JobProcessingException(
                    "sendToAll is only allowed for admin senders");
        }
        if (hasBulkRecipients) {
            requireTextArray(message.payload(), "userIds");
        } else if (hasSingleRecipient) {
            requireText(message.payload(), "userId");
        }
        return new ExecutionResult(
                realtimeHub.pushReminder(
                        message.jobId(),
                        message.executionId(),
                        message.payload()),
                "Reminder sent to connected users");
    }
}
