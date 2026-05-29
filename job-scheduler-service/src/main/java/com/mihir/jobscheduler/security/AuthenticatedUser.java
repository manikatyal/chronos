package com.mihir.jobscheduler.security;

import com.mihir.jobscheduler.model.UserRole;

public record AuthenticatedUser(
        String userId,
        UserRole role) {
}
