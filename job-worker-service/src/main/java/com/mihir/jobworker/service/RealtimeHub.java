package com.mihir.jobworker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mihir.jobworker.model.JobName;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RealtimeHub {

    private static final int NOTIFICATION_LIMIT = 50;
    private static final int NEWS_LIMIT = 20;
    private static final int REPORT_LIMIT = 20;

    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> allSessions = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsByRole = new ConcurrentHashMap<>();
    private final Deque<ObjectNode> notifications = new ArrayDeque<>();
    private final Deque<ObjectNode> newsBatches = new ArrayDeque<>();
    private final Deque<ObjectNode> reports = new ArrayDeque<>();

    public RealtimeHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(WebSocketSession session, String userId, String role) {
        allSessions.add(session);
        if (userId != null && !userId.isBlank()) {
            sessionsByUser.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
        }
        if (role != null && !role.isBlank()) {
            sessionsByRole.computeIfAbsent(role, key -> ConcurrentHashMap.newKeySet()).add(session);
        }
    }

    public void unregister(WebSocketSession session, String userId, String role) {
        allSessions.remove(session);
        if (userId != null && !userId.isBlank()) {
            Set<WebSocketSession> sessions = sessionsByUser.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    sessionsByUser.remove(userId);
                }
            }
        }
        if (role != null && !role.isBlank()) {
            Set<WebSocketSession> sessions = sessionsByRole.get(role);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    sessionsByRole.remove(role);
                }
            }
        }
    }

    public ObjectNode buildBootstrap(String userId, String role) {
        ObjectNode data = objectMapper.createObjectNode();
        ArrayNode notificationsNode = objectMapper.createArrayNode();
        synchronized (notifications) {
            for (ObjectNode item : notifications) {
                if (userId != null && isRecipient(item, userId)) {
                    notificationsNode.add(item);
                }
            }
        }
        ArrayNode newsNode = objectMapper.createArrayNode();
        synchronized (newsBatches) {
            for (ObjectNode item : newsBatches) {
                if (userId != null && isNewsRecipient(item, userId)) {
                    newsNode.add(item);
                }
            }
        }
        data.set("notifications", notificationsNode);
        data.set("news", newsNode);
        if ("ADMIN".equalsIgnoreCase(role)) {
            ArrayNode reportNode = objectMapper.createArrayNode();
            synchronized (reports) {
                for (ObjectNode item : reports) {
                    reportNode.add(item);
                }
            }
            data.set("reports", reportNode);
        }

        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("type", "bootstrap");
        envelope.set("data", data);
        return envelope;
    }

    public ObjectNode pushReminder(Long jobId, String executionId, JsonNode payload) {
        String senderUserId = payload.path("senderUserId").asText(null);
        boolean sendToAll = payload.path("sendToAll").asBoolean(false);
        Set<String> recipients = ConcurrentHashMap.newKeySet();

        if (sendToAll) {
            recipients.addAll(sessionsByUser.keySet());
            if (senderUserId != null && !senderUserId.isBlank()) {
                recipients.remove(senderUserId);
            }
        } else if (payload.has("userIds")) {
            for (JsonNode value : payload.get("userIds")) {
                if (value != null && !value.asText().isBlank()) {
                    recipients.add(value.asText());
                }
            }
        } else if (payload.hasNonNull("userId")) {
            recipients.add(payload.get("userId").asText());
        }

        ObjectNode item = objectMapper.createObjectNode();
        item.put("id", Instant.now().toEpochMilli() + "-" + Math.abs(item.hashCode()));
        item.put("jobId", jobId);
        item.put("executionId", executionId);
        item.put("jobName", JobName.SEND_NOTIFICATION.name());
        item.put("title", payload.path("title").asText("Reminder"));
        item.put("message", payload.path("message").asText(""));
        if (senderUserId != null && !senderUserId.isBlank()) {
            item.put("senderUserId", senderUserId);
        }
        item.put("sendToAll", sendToAll);
        ArrayNode recipientsNode = objectMapper.createArrayNode();
        for (String recipient : recipients) {
            recipientsNode.add(recipient);
        }
        item.set("recipients", recipientsNode);
        item.put("receivedAt", Instant.now().toString());
        item.set("payload", payload);

        synchronized (notifications) {
            notifications.addFirst(item);
            trimDeque(notifications, NOTIFICATION_LIMIT);
        }

        broadcastToRecipients(item, recipients);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("action", "REMINDER_PUSHED");
        result.put("recipientCount", recipients.size());
        result.put("processedAt", Instant.now().toString());
        return result;
    }

    public void pushNews(Long jobId, String executionId, JobName jobName, JsonNode payload, String requesterUserId) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("id", Instant.now().toEpochMilli() + "-" + Math.abs(item.hashCode()));
        item.put("jobId", jobId);
        item.put("executionId", executionId);
        item.put("jobName", jobName.name());
        String category = payload.path("category").asText("INDIA");
        item.put("category", category);
        String categoryLabel = payload.path("categoryLabel").asText(
                "GOLD".equalsIgnoreCase(category) ? "Gold price updates"
                        : "STOCKS".equalsIgnoreCase(category) ? "Stock updates" : "India latest news");
        item.put("categoryLabel", categoryLabel);
        item.put("articleCount", payload.path("articleCount").asInt(0));
        item.put("fetchedAt", payload.path("fetchedAt").asText(Instant.now().toString()));
        item.put("sourceUrl", payload.path("sourceUrl").asText(""));
        if (requesterUserId != null && !requesterUserId.isBlank()) {
            item.put("requesterUserId", requesterUserId);
        }
        if (payload.has("articles")) {
            item.set("articles", payload.get("articles"));
        }

        synchronized (newsBatches) {
            newsBatches.addFirst(item);
            trimDeque(newsBatches, NEWS_LIMIT);
        }

        if (requesterUserId != null && !requesterUserId.isBlank()) {
            broadcastToRecipients(item, Set.of(requesterUserId), "news");
        }
    }

    public void pushReport(Long jobId, String executionId, JsonNode payload) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("id", Instant.now().toEpochMilli() + "-" + Math.abs(item.hashCode()));
        item.put("jobId", jobId);
        item.put("executionId", executionId);
        item.put("jobName", JobName.GENERATE_REPORT.name());
        item.put("action", payload.path("action").asText("REPORT_GENERATED"));
        item.put("reportType", payload.path("reportType").asText(""));
        item.put("window", payload.path("window").asText(""));
        item.put("generatedAt", payload.path("generatedAt").asText(Instant.now().toString()));
        item.put("rowCount", payload.path("rowCount").asInt(0));
        item.put("fileName", payload.path("fileName").asText(""));
        item.put("receivedAt", Instant.now().toString());

        synchronized (reports) {
            reports.addFirst(item);
            trimDeque(reports, REPORT_LIMIT);
        }

        broadcastToRole(item, "ADMIN", "report");
    }

    private void broadcastToRecipients(ObjectNode item, Set<String> recipients) {
        broadcastToRecipients(item, recipients, "reminder");
    }

    private void broadcastToRecipients(ObjectNode item, Set<String> recipients, String type) {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("type", type);
        envelope.set("data", item);
        String payload = envelope.toString();

        for (String userId : recipients) {
            Set<WebSocketSession> sessions = sessionsByUser.get(userId);
            if (sessions == null) {
                continue;
            }
            for (WebSocketSession session : sessions) {
                send(session, payload);
            }
        }
    }

    private void broadcastToRole(ObjectNode item, String role, String type) {
        if (role == null || role.isBlank()) {
            return;
        }
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("type", type);
        envelope.set("data", item);
        String payload = envelope.toString();

        Set<WebSocketSession> sessions = sessionsByRole.get(role);
        if (sessions == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            send(session, payload);
        }
    }

    private void broadcastToAll(ObjectNode item, String type) {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("type", type);
        envelope.set("data", item);
        String payload = envelope.toString();
        for (WebSocketSession session : allSessions) {
            send(session, payload);
        }
    }

    private boolean isRecipient(ObjectNode item, String userId) {
        JsonNode recipients = item.get("recipients");
        if (recipients != null && recipients.isArray()) {
            for (JsonNode node : recipients) {
                if (userId.equals(node.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isNewsRecipient(ObjectNode item, String userId) {
        JsonNode requester = item.get("requesterUserId");
        if (requester == null || requester.isNull()) {
            return false;
        }
        return userId.equals(requester.asText());
    }

    private void send(WebSocketSession session, String payload) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(payload));
        } catch (IOException ignored) {
            // ignore send failures; client will reconnect
        }
    }

    private <T> void trimDeque(Deque<T> deque, int limit) {
        while (deque.size() > limit) {
            deque.removeLast();
        }
    }
}
