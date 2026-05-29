package com.mihir.jobscheduler.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mihir.jobscheduler.config.ChronosRateLimitProperties;
import com.mihir.jobscheduler.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final ChronosRateLimitProperties rateLimitProperties;
    private final RedisRateLimitService redisRateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !rateLimitProperties.enabled()
                || !request.getRequestURI().startsWith("/v1/")
                || request.getRequestURI().startsWith("/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = resolveKey(request);
        RateLimitDecision decision = redisRateLimitService.checkLimit(key);
        writeHeaders(response, decision);

        if (!decision.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            if (decision.retryAfterSeconds() > 0) {
                response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            }
            objectMapper.writeValue(response.getWriter(), new ErrorResponse(
                    Instant.now(),
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "Too Many Requests",
                    List.of("Rate limit exceeded for this user")));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String resolveKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.userId();
        }
        return request.getRemoteAddr();
    }

    private void writeHeaders(HttpServletResponse response, RateLimitDecision decision) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remainingRequests()));
        if (decision.retryAfterSeconds() > 0) {
            response.setHeader("X-RateLimit-Reset-After", String.valueOf(decision.retryAfterSeconds()));
        }
    }
}
