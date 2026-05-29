package com.mihir.jobscheduler.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mihir.jobscheduler.config.ChronosSecurityProperties;
import com.mihir.jobscheduler.model.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ChronosSecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public String generateToken(String userId, UserRole role) {
        try {
            Instant now = Instant.now();
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", userId);
            payload.put("role", role.name());
            payload.put("iat", now.getEpochSecond());
            payload.put("exp", now.plusSeconds(securityProperties.jwtTtlSeconds()).getEpochSecond());

            String encodedHeader = encode(objectMapper.writeValueAsBytes(header));
            String encodedPayload = encode(objectMapper.writeValueAsBytes(payload));
            String signature = sign(encodedHeader + "." + encodedPayload);
            return encodedHeader + "." + encodedPayload + "." + signature;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate JWT token", exception);
        }
    }

    public AuthenticatedUser parseToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Malformed JWT token");
            }

            String signedContent = parts[0] + "." + parts[1];
            String expectedSignature = sign(signedContent);
            if (!expectedSignature.equals(parts[2])) {
                throw new IllegalArgumentException("Invalid JWT signature");
            }

            Map<String, Object> claims = objectMapper.readValue(URL_DECODER.decode(parts[1]), MAP_TYPE);
            String userId = asString(claims.get("sub"));
            String role = asString(claims.get("role"));
            long expiryEpochSeconds = asLong(claims.get("exp"));

            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException("JWT subject is missing");
            }
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("JWT role is missing");
            }
            if (Instant.now().isAfter(Instant.ofEpochSecond(expiryEpochSeconds))) {
                throw new IllegalArgumentException("JWT token has expired");
            }

            return new AuthenticatedUser(userId, UserRole.valueOf(role));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to parse JWT token", exception);
        }
    }

    private String encode(byte[] data) {
        return URL_ENCODER.encodeToString(data);
    }

    private String sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(securityProperties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return encode(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
