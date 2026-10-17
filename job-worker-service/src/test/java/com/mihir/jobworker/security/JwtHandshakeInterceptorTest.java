package com.mihir.jobworker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtHandshakeInterceptorTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-32-chars";

    private final JwtHandshakeInterceptor interceptor =
            new JwtHandshakeInterceptor(new JwtVerifier(SECRET, new ObjectMapper()));

    @Test
    void validTokenSetsIdentityFromTokenNotFromUrl() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = handshake("token=" + token("alice", "USER", 600) + "&userId=bob&role=ADMIN", attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry("userId", "alice").containsEntry("role", "USER");
    }

    @Test
    void missingTokenIsRejected() {
        assertThat(handshake("userId=alice&role=ADMIN", new HashMap<>())).isFalse();
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        assertThat(handshake("token=" + token("alice", "USER", -10), new HashMap<>())).isFalse();
    }

    @Test
    void shortSecretFailsFast() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new JwtVerifier("too-short", new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class);
    }

    private boolean handshake(String query, Map<String, Object> attributes) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws");
        request.setQueryString(query);
        return interceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                null,
                attributes);
    }

    private String token(String userId, String role, long ttlSeconds) throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        long exp = Instant.now().plusSeconds(ttlSeconds).getEpochSecond();
        String payload = encoder.encodeToString(("{\"sub\":\"" + userId + "\",\"role\":\"" + role + "\",\"exp\":" + exp + "}")
                .getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return header + "." + payload + "." + encoder.encodeToString(mac.doFinal((header + "." + payload).getBytes(StandardCharsets.UTF_8)));
    }
}
