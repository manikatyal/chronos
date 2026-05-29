package com.mihir.jobworker.service;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private final RealtimeHub realtimeHub;

    public RealtimeWebSocketHandler(RealtimeHub realtimeHub) {
        this.realtimeHub = realtimeHub;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = extractUserId(session.getUri());
        String role = extractRole(session.getUri());
        session.getAttributes().put("userId", userId);
        session.getAttributes().put("role", role);
        realtimeHub.register(session, userId, role);
        session.sendMessage(new TextMessage(realtimeHub.buildBootstrap(userId, role).toString()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get("userId");
        String role = (String) session.getAttributes().get("role");
        realtimeHub.unregister(session, userId, role);
    }

    private String extractUserId(URI uri) {
        if (uri == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst("userId");
    }

    private String extractRole(URI uri) {
        if (uri == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst("role");
    }
}
