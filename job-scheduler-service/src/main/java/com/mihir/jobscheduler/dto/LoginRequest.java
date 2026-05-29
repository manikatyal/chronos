package com.mihir.jobscheduler.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "userId is required")
        String userId,
        @NotBlank(message = "password is required")
        String password) {
}
