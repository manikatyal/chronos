package com.mihir.jobscheduler.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chronos.security")
public record ChronosSecurityProperties(
        @NotBlank @Size(min = 32, message = "CHRONOS_JWT_SECRET must be at least 32 characters") String jwtSecret,
        @Min(60) long jwtTtlSeconds,
        String bootstrapAdminUserId,
        String bootstrapAdminPassword) {
}
