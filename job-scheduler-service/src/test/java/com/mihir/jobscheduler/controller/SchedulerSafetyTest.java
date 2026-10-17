package com.mihir.jobscheduler.controller;

import com.mihir.jobscheduler.manager.ScheduledJobLeaseClaimManager;
import com.mihir.jobscheduler.model.UserRole;
import com.mihir.jobscheduler.repo.JobRepository;
import com.mihir.jobscheduler.repo.ScheduledJobLeaseRepository;
import com.mihir.jobscheduler.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs with production defaults for the abuse guards: email is admin-only and cron is limited to once a minute.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "chronos.security.user-email-enabled=false")
class SchedulerSafetyTest {

    private static final String EMAIL_JOB = """
            {
              "jobName": "SEND_EMAIL",
              "scheduleType": "DELAYED",
              "schedule": { "delaySeconds": 600 },
              "payload": { "email": "someone@example.com", "subject": "Hi", "body": "Hello" },
              "maxRetries": 1
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ScheduledJobLeaseRepository leaseRepository;

    @MockBean
    private ScheduledJobLeaseClaimManager scheduledJobLeaseClaimManager;

    @BeforeEach
    void clean() {
        when(scheduledJobLeaseClaimManager.claimDueJobs(anyString(), any(), anyInt())).thenReturn(List.of());
        leaseRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void normalUserCannotCreateEmailJob() throws Exception {
        mockMvc.perform(post("/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer("test-user", UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMAIL_JOB))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("Only admins can create email jobs"));
    }

    @Test
    void adminCanCreateEmailJob() throws Exception {
        mockMvc.perform(post("/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer("test-admin", UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMAIL_JOB))
                .andExpect(status().isCreated());
    }

    @Test
    void cronThatNeverFiresIsRejected() throws Exception {
        mockMvc.perform(post("/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer("test-user", UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(syncJob("0 0 0 30 2 *")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("schedule.cronExpression never fires"));
    }

    @Test
    void cronFasterThanOncePerMinuteIsRejected() throws Exception {
        mockMvc.perform(post("/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer("test-user", UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(syncJob("* * * * * *")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("schedule.cronExpression must not run more than once a minute"));
    }

    @Test
    void everyMinuteCronIsAccepted() throws Exception {
        mockMvc.perform(post("/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer("test-user", UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(syncJob("* * * * *")))
                .andExpect(status().isCreated());
    }

    private String syncJob(String cron) {
        return """
                {
                  "jobName": "DATA_SYNC",
                  "scheduleType": "CRON",
                  "schedule": { "cronExpression": "%s" },
                  "payload": { "source": "CRM", "destination": "DWH" },
                  "maxRetries": 1
                }
                """.formatted(cron);
    }

    private String bearer(String userId, UserRole role) {
        return "Bearer " + jwtService.generateToken(userId, role);
    }
}
