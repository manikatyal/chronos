package com.mihir.jobscheduler.dto;

import com.mihir.jobscheduler.model.UserRole;

public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String userId,
        UserRole role) {
}
