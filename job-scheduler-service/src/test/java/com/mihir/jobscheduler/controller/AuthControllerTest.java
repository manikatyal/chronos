package com.mihir.jobscheduler.controller;

import com.mihir.jobscheduler.entity.UserAccountEntity;
import com.mihir.jobscheduler.manager.ScheduledJobLeaseClaimManager;
import com.mihir.jobscheduler.repo.UserAccountRepository;
import com.mihir.jobscheduler.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private AuthService authService;

    @MockBean
    private ScheduledJobLeaseClaimManager scheduledJobLeaseClaimManager;

    @BeforeEach
    void cleanUsers() {
        when(scheduledJobLeaseClaimManager.claimDueJobs(anyString(), any(Duration.class), anyInt())).thenReturn(List.of());
        userAccountRepository.deleteAll();
        authService.bootstrapAdminIfConfigured();
    }

    @Test
    void signUpShouldCreateNormalUserAndReturnToken() throws Exception {
        mockMvc.perform(post("/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "mihir.user",
                                  "password": "strongPass123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.userId").value("mihir.user"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.accessToken").isString());

        UserAccountEntity user = userAccountRepository.findById("mihir.user").orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo("strongPass123");
    }

    @Test
    void duplicateSignUpShouldReturnConflict() throws Exception {
        mockMvc.perform(post("/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "mihir.user",
                                  "password": "strongPass123"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "mihir.user",
                                  "password": "anotherPass123"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void loginShouldReturnTokenForExistingUser() throws Exception {
        mockMvc.perform(post("/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "login.user",
                                  "password": "strongPass123"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "login.user",
                                  "password": "strongPass123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.userId").value("login.user"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.accessToken").isString());
    }

    @Test
    void loginShouldReturnUnauthorizedForWrongPassword() throws Exception {
        mockMvc.perform(post("/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "login.user",
                                  "password": "strongPass123"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "login.user",
                                  "password": "wrongPass123"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bootstrapAdminShouldBeAbleToLogin() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "test-admin",
                                  "password": "test-admin-pass"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("test-admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.accessToken").isString());
    }
}
