package com.mihir.jobworker.service;

import com.mihir.jobworker.security.JwtHandshakeInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private final RealtimeHub realtimeHub;

    public RealtimeWebSocketHandler(RealtimeHub realtimeHub) {
        this.realtimeHub = realtimeHub;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Set by JwtHandshakeInterceptor from the verified token, never from the URL.
        String userId = (String) session.getAttributes().get(JwtHandshakeInterceptor.USER_ID);
        String role = (String) session.getAttributes().get(JwtHandshakeInterceptor.ROLE);
        realtimeHub.register(session, userId, role);
        session.sendMessage(new TextMessage(realtimeHub.buildBootstrap(userId, role).toString()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get(JwtHandshakeInterceptor.USER_ID);
        String role = (String) session.getAttributes().get(JwtHandshakeInterceptor.ROLE);
        realtimeHub.unregister(session, userId, role);
    }
}
