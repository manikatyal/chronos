package com.mihir.jobscheduler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank(message = "userId is required")
        @Size(min = 3, max = 100, message = "userId must be between 3 and 100 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "userId can contain only letters, numbers, dot, underscore and hyphen")
        String userId,
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 100, message = "password must be between 8 and 100 characters")
        String password) {
}
