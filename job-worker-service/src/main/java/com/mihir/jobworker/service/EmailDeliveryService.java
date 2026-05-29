package com.mihir.jobworker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mihir.jobworker.config.WorkerProcessingProperties;
import com.mihir.jobworker.exception.JobProcessingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class EmailDeliveryService {

    private final JavaMailSender mailSender;
    private final WorkerProcessingProperties processingProperties;
    private final ObjectMapper objectMapper;
    private final String mailHost;
    private final String mailUsername;
    private final String mailPassword;

    public EmailDeliveryService(
            JavaMailSender mailSender,
            WorkerProcessingProperties processingProperties,
            ObjectMapper objectMapper,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.password:}") String mailPassword) {
        this.mailSender = mailSender;
        this.processingProperties = processingProperties;
        this.objectMapper = objectMapper;
        this.mailHost = mailHost;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
    }

    public ObjectNode send(Long jobId, String executionId, String email, String subject, String body) {
        return send(jobId, executionId, List.of(email), subject, body);
    }

    public ObjectNode send(Long jobId, String executionId, List<String> emails, String subject, String body) {
        String fromAddress = processingProperties.email().fromAddress();
        List<String> recipients = emails.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (recipients.isEmpty()) {
            throw new JobProcessingException("emails must be non-empty");
        }

        String resolvedSubject = subject != null && !subject.isBlank()
                ? subject
                : "Chronos Email Notification";
        boolean smtpConfigured = fromAddress != null
                && !fromAddress.isBlank()
                && mailHost != null
                && !mailHost.isBlank()
                && mailUsername != null
                && !mailUsername.isBlank()
                && mailPassword != null
                && !mailPassword.isBlank();

        String resolvedBody = body != null && !body.isBlank() ? body : "";
        if (smtpConfigured) {
            for (int index = 0; index < recipients.size(); index++) {
                String email = recipients.get(index);
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress);
                message.setTo(email);
                message.setSubject(resolvedSubject);
                message.setText(resolvedBody);
                mailSender.send(message);
            }
        } else if (fromAddress == null || fromAddress.isBlank()) {
            fromAddress = "chronos-local@localhost";
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.put("action", "EMAIL_SENT");
        result.put("from", fromAddress);
        if (recipients.size() == 1) {
            result.put("to", recipients.getFirst());
        } else {
            result.put("recipientCount", recipients.size());
        }
        result.putPOJO("recipients", recipients);
        result.put("subject", resolvedSubject);
        result.put("body", resolvedBody);
        result.put("deliveryMode", smtpConfigured ? "SMTP" : "LOCAL_MOCK");
        result.put("messageId", "email-" + executionId);
        result.put("processedAt", Instant.now().toString());
        return result;
    }
}
