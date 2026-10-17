package com.mihir.jobworker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mihir.jobworker.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Executions and reports contain every user's payloads and carry no owner,
 * so all /v1 worker endpoints are admin-only.
 */
@Component
@RequiredArgsConstructor
public class WorkerAuthFilter extends OncePerRequestFilter {

    private final JwtVerifier jwtVerifier;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", "A valid Bearer token is required");
            return;
        }
        TokenUser user;
        try {
            user = jwtVerifier.verify(header.substring(7));
        } catch (IllegalArgumentException exception) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", exception.getMessage());
            return;
        }
        if (!user.isAdmin()) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden", "Only admins can access worker data");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(Instant.now(), status, error, List.of(message)));
    }
}
