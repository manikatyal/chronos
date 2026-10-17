package com.mihir.jobworker.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Verifies tokens issued by the scheduler (HS256, same shared secret).
 * The worker never issues tokens; it only trusts the subject and role inside a valid one.
 */
@Component
public class JwtVerifier {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final byte[] secret;
    private final ObjectMapper objectMapper;

    public JwtVerifier(@Value("${chronos.worker.security.jwt-secret}") String jwtSecret, ObjectMapper objectMapper) {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException("CHRONOS_JWT_SECRET must be set and at least 32 characters long");
        }
        this.secret = jwtSecret.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    public TokenUser verify(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token is missing");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed JWT token");
        }
        byte[] expected = sign(parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid JWT signature");
        }

        Map<String, Object> claims;
        try {
            claims = objectMapper.readValue(URL_DECODER.decode(parts[1]), MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to parse JWT token");
        }
        String userId = claims.get("sub") == null ? null : String.valueOf(claims.get("sub"));
        String role = claims.get("role") == null ? null : String.valueOf(claims.get("role"));
        if (userId == null || userId.isBlank() || role == null || role.isBlank()) {
            throw new IllegalArgumentException("JWT subject or role is missing");
        }
        if (!(claims.get("exp") instanceof Number exp) || Instant.now().isAfter(Instant.ofEpochSecond(exp.longValue()))) {
            throw new IllegalArgumentException("JWT token has expired");
        }
        return new TokenUser(userId, role);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to verify JWT signature", exception);
        }
    }
}
