package com.mihir.jobscheduler.service;

import com.mihir.jobscheduler.config.ChronosSecurityProperties;
import com.mihir.jobscheduler.dto.AuthTokenResponse;
import com.mihir.jobscheduler.dto.LoginRequest;
import com.mihir.jobscheduler.dto.SignUpRequest;
import com.mihir.jobscheduler.entity.UserAccountEntity;
import com.mihir.jobscheduler.exception.AuthConflictException;
import com.mihir.jobscheduler.exception.AuthInvalidCredentialsException;
import com.mihir.jobscheduler.manager.UserAccountManager;
import com.mihir.jobscheduler.model.UserRole;
import com.mihir.jobscheduler.security.JwtService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountManager userAccountManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ChronosSecurityProperties securityProperties;

    @PostConstruct
    @Transactional
    public void bootstrapAdminIfConfigured() {
        String adminUserId = trimToNull(securityProperties.bootstrapAdminUserId());
        String adminPassword = trimToNull(securityProperties.bootstrapAdminPassword());
        if (adminUserId == null || adminPassword == null || userAccountManager.existsByUserId(adminUserId)) {
            return;
        }

        userAccountManager.save(UserAccountEntity.builder()
                .userId(adminUserId)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(UserRole.ADMIN)
                .build());
    }

    @Transactional
    public AuthTokenResponse signUp(SignUpRequest request) {
        if (userAccountManager.existsByUserId(request.userId())) {
            throw new AuthConflictException("User already exists with userId: " + request.userId());
        }

        UserAccountEntity user = userAccountManager.save(UserAccountEntity.builder()
                .userId(request.userId())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.USER)
                .build());
        return toTokenResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthTokenResponse login(LoginRequest request) {
        UserAccountEntity user = userAccountManager.findByUserId(request.userId())
                .orElseThrow(() -> new AuthInvalidCredentialsException("Invalid userId or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthInvalidCredentialsException("Invalid userId or password");
        }
        return toTokenResponse(user);
    }

    private AuthTokenResponse toTokenResponse(UserAccountEntity user) {
        return new AuthTokenResponse(
                jwtService.generateToken(user.getUserId(), user.getRole()),
                "Bearer",
                securityProperties.jwtTtlSeconds(),
                user.getUserId(),
                user.getRole());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
