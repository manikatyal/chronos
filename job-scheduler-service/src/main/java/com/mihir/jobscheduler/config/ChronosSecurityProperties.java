package com.mihir.jobscheduler.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chronos.security")
public record ChronosSecurityProperties(
        @NotBlank String jwtSecret,
        @Min(60) long jwtTtlSeconds,
        String bootstrapAdminUserId,
        String bootstrapAdminPassword) {
}
