package com.mihir.jobscheduler.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mihir.jobscheduler.entity.JobEntity;
import com.mihir.jobscheduler.entity.ScheduledJobLeaseEntity;
import com.mihir.jobscheduler.entity.ScheduledJobRetryExhaustionEntity;
import com.mihir.jobscheduler.manager.ScheduledJobLeaseClaimManager;
import com.mihir.jobscheduler.model.JobName;
import com.mihir.jobscheduler.model.JobStatus;
import com.mihir.jobscheduler.model.FailureSource;
import com.mihir.jobscheduler.model.ScheduleType;
import com.mihir.jobscheduler.model.UserRole;
import com.mihir.jobscheduler.repo.JobRepository;
import com.mihir.jobscheduler.repo.ScheduledJobLeaseRepository;
import com.mihir.jobscheduler.repo.ScheduledJobRetryExhaustionRepository;
import com.mihir.jobscheduler.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ScheduledJobLeaseRepository leaseRepository;

    @Autowired
    private ScheduledJobRetryExhaustionRepository retryExhaustionRepository;

    @MockBean
    private ScheduledJobLeaseClaimManager scheduledJobLeaseClaimManager;

    @BeforeEach
    void cleanDatabase() {
        when(scheduledJobLeaseClaimManager.claimDueJobs(anyString(), any(), anyInt())).thenReturn(List.of());
        retryExhaustionRepository.deleteAll();
        leaseRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void createJobShouldPersistJobAndLease() throws Exception {
        String request = """
                {
                  "jobName": "SEND_EMAIL",
                  "scheduleType": "DELAYED",
                  "schedule": {
                    "delaySeconds": 600
                  },
                  "payload": {
                  "subject": "Test subject",
                  "body": "Test body",
                    "email": "mihir@test.com",
                    "template": "WELCOME_EMAIL",
                    "userId": "U123"
                  },
                  "maxRetries": 3
                }
                """;

        String response = mockMvc.perform(post("/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, userAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").isNumber())
                .andExpect(jsonPath("$.jobName").value("SEND_EMAIL"))
                .andExpect(jsonPath("$.scheduleType").value("DELAYED"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.maxRetries").value(3))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long jobId = readJobId(response);
        JobEntity job = jobRepository.findById(jobId).orElseThrow();
        ScheduledJobLeaseEntity lease = leaseRepository.findById(jobId).orElseThrow();

        assertThat(job.getStatus()).isEqualTo(JobStatus.SCHEDULED);
        assertThat(lease.getRetryCount()).isZero();
        assertThat(lease.getFirstClaimedAt()).isNull();
        assertThat(lease.getLockedBy()).isNull();
        assertThat(lease.getLockedUntil()).isNull();
        assertThat(lease.getNextRunAt()).isAfter(Instant.now().minusSeconds(1));
    }

    @Test
    void createDuplicateJobShouldReturnExistingJobWithoutCreatingNewRow() throws Exception {
        String request = """
                {
                  "jobName": "SEND_EMAIL",
                  "scheduleType": "DELAYED",
                  "schedule": {
                    "delaySeconds": 600
                  },
                  "payload": {
                  "subject": "Test subject",
                  "body": "Test body",
                    "email": "mihir@test.com",
                    "template": "WELCOME_EMAIL",
                    "userId": "U123"
                  },
                  "maxRetries": 3
                }
                """;

        String firstResponse = mockMvc.perform(post("/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, userAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondResponse = mockMvc.perform(post("/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, userAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyExists").value(true))
                .andExpect(jsonPath("$.mismatches").isArray())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(readJobId(secondResponse)).isEqualTo(readJobId(firstResponse));
        assertThat(jobRepository.count()).isEqualTo(1);
        assertThat(leaseRepository.count()).isEqualTo(1);
    }

    @Test
    void createSameJobWithDifferentMaxRetriesShouldReturnExistingJob() throws Exception {
        String firstRequest = """
                {
                  "jobName": "SEND_EMAIL",
                  "scheduleType": "DELAYED",
                  "schedule": {
                    "delaySeconds": 600
                  },
                  "payload": {
                  "subject": "Test subject",
                  "body": "Test body",
                    "email": "mihir@test.com",
                    "template": "WELCOME_EMAIL",
                    "userId": "U123"
                  },
                  "maxRetries": 3
                }
                """;
        String secondRequest = """
                {
                  "jobName": "SEND_EMAIL",
                  "scheduleType": "DELAYED",
                  "schedule": {
                    "delaySeconds": 600
                  },
                  "payload": {
                  "subject": "Test subject",
                  "body": "Test body",
                    "email": "mihir@test.com",
                    "template": "WELCOME_EMAIL",
                    "userId": "U123"
                  },
                  "maxRetries": 5
                }
                """;

        String firstResponse = mockMvc.perform(post("/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, userAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRequest))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondResponse = mockMvc.perform(post("/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, userAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyExists").value(true))
                .andExpect(jsonPath("$.mismatches[0].field").value("maxRetries"))
                .andExpect(jsonPath("$.mismatches[0].requestedValue").value(5))
                .andExpect(jsonPath("$.mismatches[0].existingValue").value(3))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long jobId = readJobId(firstResponse);
        assertThat(readJobId(secondResponse)).isEqualTo(jobId);

        JobEntity job = jobRepository.findById(jobId).orElseThrow();
        assertThat(job.getMaxRetries()).isEqualTo(3);
        assertThat(jobRepository.count()).isEqualTo(1);
    }

    @Test
    void getStatusShouldExposeEditableFlagsForUnclaimedJob() throws Exception {
        long jobId = createJob("""
                {
                  "jobName": "ORDER_EXPIRY",
                  "scheduleType": "DELAYED",
                  "schedule": {
                    "delaySeconds": 300
                  },
                  "payload": {
                    "orderId": "ORD900",
                    "userId": "U890"
                  },
                  "maxRetries": 1
                }
                """);

        mockMvc.perform(get("/v1/jobs/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, userAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.retryCount").value(0))
                .andExpect(jsonPath("$.firstClaimedAt").doesNotExist())
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.deletable").value(true));
    }

    @Test
    void updateUnclaimedJobShouldUpdateBusinessAndLeaseState() throws Exception {
        long jobId = createJob("""
                {
                  "jobName": "SEND_EMAIL",
                  "scheduleType": "DELAYED",
                  "schedule": {
                    "delaySeconds": 600
                  },
                  "payload": {
                  "subject": "Test subject",
                  "body": "Test body",
                    "email": "mihir@test.com",
                    "template": "WELCOME_EMAIL",
                    "userId": "U123"
                  },
                  "maxRetries": 3
                }
                """);

        mockMvc.perform(put("/v1/jobs/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, userAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobName": "SEND_EMAIL",
                                  "scheduleType": "DELAYED",
                                  "schedule": {
                                    "delaySeconds": 1200
                                  },
                                  "payload": {
                                  "subject": "Test subject",
                                  "body": "Test body",
                                    "email": "mihir@test.com",
                                    "template": "WELCOME_EMAIL",
                                    "userId": "U123"
                                  },
                                  "maxRetries": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.maxRetries").value(5));

        JobEntity job = jobRepository.findById(jobId).orElseThrow();
        ScheduledJobLeaseEntity lease = leaseRepository.findById(jobId).orElseThrow();

        assertThat(job.getMaxRetries()).isEqualTo(5);
        assertThat(job.getStatus()).isEqualTo(JobStatus.SCHEDULED);
        assertThat(lease.getRetryCount()).isZero();
        assertThat(lease.getLockedBy()).isNull();
        assertThat(lease.getLockedUntil()).isNull();
        assertThat(lease.getFirstClaimedAt()).isNull();
    }

    @Test
    void deleteUnclaimedJobShouldRemoveJobAndLease() throws Exception {
        long jobId = createJob("""
                {
                  "jobName": "DATA_SYNC",
                  "scheduleType": "CRON",
                  "schedule": {
                    "cronExpression": "0 * * * *"
                  },
                  "payload": {
                    "source": "CRM",
                    "destination": "DATA_WAREHOUSE"
                  },
                  "maxRetries": 3
                }
                """);

        mockMvc.perform(delete("/v1/jobs/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, userAuth()))
                .andExpect(status().isNoContent());

        assertThat(jobRepository.findById(jobId)).isEmpty();
        assertThat(leaseRepository.findById(jobId)).isEmpty();
    }

    @Test
    void updateClaimedJobShouldReturnConflict() throws Exception {
        long jobId = createJob("""
                {
                  "jobName": "SEND_NOTIFICATION",
                  "scheduleType": "ONE_TIME",
                  "schedule": {
                    "runAt": "2099-03-14T20:00:00Z"
                  },
                  "payload": {
                    "userId": "test-user",
                    "title": "Meeting Reminder",
                    "message": "Your meeting starts in 30 minutes"
                  },
                  "maxRetries": 2
                }
                """);
        markClaimed(jobId);

        mockMvc.perform(put("/v1/jobs/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, userAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobName": "SEND_NOTIFICATION",
                                  "scheduleType": "ONE_TIME",
                                  "schedule": {
                                    "runAt": "2099-03-14T21:00:00Z"
                                  },
                                  "payload": {
                                    "userId": "test-user",
                                    "title": "Meeting Reminder",
                                    "message": "Updated content"
                                  },
                                  "maxRetries": 2
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("current state")));
    }

    @Test
    void deleteClaimedJobShouldReturnConflict() throws Exception {
        long jobId = createJob("""
                {
                  "jobName": "ORDER_EXPIRY",
                  "scheduleType": "DELAYED",
                  "schedule": {
                    "delaySeconds": 600
                  },
                  "payload": {
                    "orderId": "ORD900",
                    "userId": "U890"
                  },
                  "maxRetries": 1
                }
                """);
        markClaimed(jobId);

        mockMvc.perform(delete("/v1/jobs/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, userAuth()))
                .andExpect(status().isConflict());

        assertThat(jobRepository.findById(jobId)).isPresent();
        assertThat(leaseRepository.findById(jobId)).isPresent();
    }

    @Test
    void updateCronJobShouldBeAllowedAfterPreviousRunWhenNotCurrentlyLocked() throws Exception {
        long jobId = createJob("""
                {
                  "jobName": "DATA_SYNC",
                  "scheduleType": "CRON",
                  "schedule": {
                    "cronExpression": "0 * * * *"
                  },
                  "payload": {
                    "source": "CRM",
                    "destination": "DATA_WAREHOUSE"
                  },
                  "maxRetries": 3
                }
                """);
        markPreviouslyClaimedButUnlocked(jobId);

        mockMvc.perform(put("/v1/jobs/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, userAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobName": "DATA_SYNC",
                                  "scheduleType": "CRON",
                                  "schedule": {
                                    "cronExpression": "*/5 * * * * *"
                                  },
                                  "payload": {
                                    "source": "CRM",
                                    "destination": "DWH"
                                  },
                                  "maxRetries": 4
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.maxRetries").value(4));

        mockMvc.perform(get("/v1/jobs/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, userAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.deletable").value(true));
    }

    @Test
    void deleteCronJobShouldBeBlockedWhileCurrentlyLocked() throws Exception {
        long jobId = createJob("""
                {
                  "jobName": "DATA_SYNC",
                  "scheduleType": "CRON",
                  "schedule": {
                    "cronExpression": "0 * * * *"
                  },
                  "payload": {
                    "source": "CRM",
                    "destination": "DATA_WAREHOUSE"
                  },
                  "maxRetries": 3
                }
                """);
        markCurrentlyLocked(jobId);

        mockMvc.perform(delete("/v1/jobs/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, userAuth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("current state")));

        mockMvc.perform(get("/v1/jobs/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, userAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false))
                .andExpect(jsonPath("$.deletable").value(false));
    }

    @Test
    void updateToExistingJobIdentityShouldReturnSavedJobWithMismatchDetails() throws Exception {
        long existingJobId = createJob("""
                {
                  "jobName": "SEND_EMAIL",
                  "scheduleType": "DELAYED",
                  "schedule": {
                    "delaySeconds": 600
                  },
                  "payload": {
                  "subject": "Test subject",
                  "body": "Test body",
                    "email": "mihir@test.com",
                    "template": "WELCOME_EMAIL",
                    "userId": "U123"
                  },
                  "maxRetries": 3
                }
                """);
        long secondJobId = createJob("""
                {
                  "jobName": "SEND_NOTIFICATION",
                  "scheduleType": "ONE_TIME",
                  "schedule": {
                    "runAt": "2099-03-14T20:00:00Z"
                  },
                  "payload": {
                    "userId": "test-user",
                    "title": "Meeting Reminder",
                    "message": "Your meeting starts in 30 minutes"
                  },
                  "maxRetries": 2
                }
                """);

        mockMvc.perform(put("/v1/jobs/{jobId}", secondJobId)
                        .header(HttpHeaders.AUTHORIZATION, userAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobName": "SEND_EMAIL",
                                  "scheduleType": "DELAYED",
                                  "schedule": {
                                    "delaySeconds": 600
                                  },
                                  "payload": {
                                  "subject": "Test subject",
                                  "body": "Test body",
                                    "email": "mihir@test.com",
                                    "template": "WELCOME_EMAIL",
                                    "userId": "U123"
                                  },
                                  "maxRetries": 5
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.jobId").value(existingJobId))
                .andExpect(jsonPath("$.alreadyExists").value(true))
                .andExpect(jsonPath("$.mismatches[0].field").value("maxRetries"))
                .andExpect(jsonPath("$.mismatches[0].requestedValue").value(5))
                .andExpect(jsonPath("$.mismatches[0].existingValue").value(3));
    }

    @Test
    void getMissingJobShouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/v1/jobs/{jobId}", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, userAuth()))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidScheduleShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, userAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobName": "SEND_EMAIL",
                                  "scheduleType": "DELAYED",
                                  "schedule": {
                                    "runAt": "2099-03-14T20:00:00Z"
                                  },
                                  "payload": {
                                  "subject": "Test subject",
                                  "body": "Test body",
                                    "email": "mihir@test.com",
                                    "template": "WELCOME_EMAIL",
                                    "userId": "U123"
                                  },
                                  "maxRetries": 3
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(org.hamcrest.Matchers.containsString("delaySeconds"))));
    }

    @Test
    void getFailedJobsShouldReturnFailedJobsWithLatestExhaustionDetails() throws Exception {
        JobEntity olderFailedJob = jobRepository.save(JobEntity.builder()
                .createdBy("ops-user")
                .jobName(JobName.ORDER_EXPIRY)
                .scheduleType(ScheduleType.DELAYED)
                .scheduleJson("{\"delaySeconds\":600}")
                .payloadJson("{\"orderId\":\"ORD-1\",\"userId\":\"U-1\"}")
                .maxRetries(2)
                .status(JobStatus.FAILED)
                .build());

        JobEntity latestFailedJob = jobRepository.save(JobEntity.builder()
                .createdBy("ops-admin")
                .jobName(JobName.DATA_SYNC)
                .scheduleType(ScheduleType.CRON)
                .scheduleJson("{\"cronExpression\":\"0 * * * *\"}")
                .payloadJson("{\"source\":\"CRM\",\"destination\":\"DWH\"}")
                .maxRetries(5)
                .status(JobStatus.FAILED)
                .build());

        retryExhaustionRepository.save(ScheduledJobRetryExhaustionEntity.builder()
                .jobId(olderFailedJob.getJobId())
                .workerId("worker-old")
                .failureSource(FailureSource.SCHEDULER)
                .executionId(olderFailedJob.getJobId() + "_2026-03-17T10:00:00Z")
                .logTag("chronos:" + olderFailedJob.getJobId() + "_2026-03-17T10:00:00Z")
                .retryCount(2)
                .failureReason("older failure")
                .exhaustedAt(Instant.parse("2026-03-17T10:00:00Z"))
                .build());

        retryExhaustionRepository.save(ScheduledJobRetryExhaustionEntity.builder()
                .jobId(latestFailedJob.getJobId())
                .workerId("worker-new")
                .failureSource(FailureSource.SCHEDULER)
                .executionId(latestFailedJob.getJobId() + "_2026-03-17T11:00:00Z")
                .logTag("chronos:" + latestFailedJob.getJobId() + "_2026-03-17T11:00:00Z")
                .retryCount(5)
                .failureReason("latest failure")
                .exhaustedAt(Instant.parse("2026-03-17T11:00:00Z"))
                .build());

        mockMvc.perform(get("/v1/jobs/failed")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].jobId").value(latestFailedJob.getJobId()))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].workerId").value("worker-new"))
                .andExpect(jsonPath("$[0].retryCount").value(5))
                .andExpect(jsonPath("$[0].failureReason").value("latest failure"))
                .andExpect(jsonPath("$[1].jobId").value(olderFailedJob.getJobId()))
                .andExpect(jsonPath("$[1].workerId").value("worker-old"));
    }

    @Test
    void getFailedJobsShouldReturnEmptyListWhenNoFailedJobsExist() throws Exception {
        mockMvc.perform(get("/v1/jobs/failed")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void normalUserShouldNotSeeAllFailedJobs() throws Exception {
        mockMvc.perform(get("/v1/jobs/failed")
                        .header(HttpHeaders.AUTHORIZATION, userAuth()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getFailedJobsShouldSupportPagination() throws Exception {
        JobEntity firstFailedJob = jobRepository.save(JobEntity.builder()
                .createdBy("ops-user")
                .jobName(JobName.SEND_EMAIL)
                .scheduleType(ScheduleType.DELAYED)
                .scheduleJson("{\"delaySeconds\":60}")
                .payloadJson("{\"email\":\"first@test.com\",\"template\":\"WELCOME_EMAIL\",\"userId\":\"U-1\"}")
                .maxRetries(3)
                .status(JobStatus.FAILED)
                .build());
        JobEntity secondFailedJob = jobRepository.save(JobEntity.builder()
                .createdBy("ops-user")
                .jobName(JobName.DATA_SYNC)
                .scheduleType(ScheduleType.CRON)
                .scheduleJson("{\"cronExpression\":\"0 * * * *\"}")
                .payloadJson("{\"source\":\"CRM\",\"destination\":\"DWH\"}")
                .maxRetries(3)
                .status(JobStatus.FAILED)
                .build());

        retryExhaustionRepository.save(ScheduledJobRetryExhaustionEntity.builder()
                .jobId(firstFailedJob.getJobId())
                .workerId("worker-old")
                .failureSource(FailureSource.SCHEDULER)
                .executionId(firstFailedJob.getJobId() + "_2026-03-17T10:00:00Z")
                .logTag("chronos:" + firstFailedJob.getJobId() + "_2026-03-17T10:00:00Z")
                .retryCount(3)
                .failureReason("older failure")
                .exhaustedAt(Instant.parse("2026-03-17T10:00:00Z"))
                .build());
        retryExhaustionRepository.save(ScheduledJobRetryExhaustionEntity.builder()
                .jobId(secondFailedJob.getJobId())
                .workerId("worker-new")
                .failureSource(FailureSource.WORKER)
                .executionId(secondFailedJob.getJobId() + "_2026-03-17T11:00:00Z")
                .logTag("chronos:" + secondFailedJob.getJobId() + "_2026-03-17T11:00:00Z")
                .retryCount(0)
                .failureReason("latest failure")
                .exhaustedAt(Instant.parse("2026-03-17T11:00:00Z"))
                .build());

        mockMvc.perform(get("/v1/jobs/failed")
                        .param("page", "0")
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].jobId").value(secondFailedJob.getJobId()));

        mockMvc.perform(get("/v1/jobs/failed")
                        .param("page", "1")
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].jobId").value(firstFailedJob.getJobId()));
    }

    @Test
    void getFailedJobsShouldAlsoIncludeWorkerReportedFailures() throws Exception {
        JobEntity workerFailedJob = jobRepository.save(JobEntity.builder()
                .createdBy("ops-user")
                .jobName(JobName.SEND_EMAIL)
                .scheduleType(ScheduleType.DELAYED)
                .scheduleJson("{\"delaySeconds\":60}")
                .payloadJson("{\"email\":\"ops@test.com\",\"template\":\"WELCOME_EMAIL\",\"userId\":\"U-1\"}")
                .maxRetries(3)
                .status(JobStatus.COMPLETED)
                .build());

        retryExhaustionRepository.save(ScheduledJobRetryExhaustionEntity.builder()
                .jobId(workerFailedJob.getJobId())
                .workerId("job-worker")
                .failureSource(FailureSource.WORKER)
                .executionId(workerFailedJob.getJobId() + "_2026-03-25T10:00:00Z")
                .logTag("chronos:" + workerFailedJob.getJobId() + "_2026-03-25T10:00:00Z")
                .retryCount(0)
                .failureReason("SMTP delivery failed")
                .exhaustedAt(Instant.parse("2026-03-25T10:01:00Z"))
                .build());

        mockMvc.perform(get("/v1/jobs/failed")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId").value(workerFailedJob.getJobId()))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].failureSource").value("WORKER"))
                .andExpect(jsonPath("$[0].executionId").value(workerFailedJob.getJobId() + "_2026-03-25T10:00:00Z"))
                .andExpect(jsonPath("$[0].logTag").value("chronos:" + workerFailedJob.getJobId() + "_2026-03-25T10:00:00Z"))
                .andExpect(jsonPath("$[0].failureReason").value("SMTP delivery failed"));
    }

    @Test
    void normalUserShouldNotAccessAnotherUsersJob() throws Exception {
        long jobId = createJob("""
                {
                  "jobName": "SEND_EMAIL",
                  "scheduleType": "DELAYED",
                  "schedule": {
                    "delaySeconds": 600
                  },
                  "payload": {
                  "subject": "Test subject",
                  "body": "Test body",
                    "email": "owner@test.com",
                    "template": "WELCOME_EMAIL",
                    "userId": "U123"
                  },
                  "maxRetries": 3
                }
                """);

        mockMvc.perform(get("/v1/jobs/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer("another-user", UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void normalUserShouldNotCreateBulkEmailJob() throws Exception {
        mockMvc.perform(post("/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, userAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobName": "SEND_EMAIL",
                                  "scheduleType": "DELAYED",
                                  "schedule": {
                                    "delaySeconds": 600
                                  },
                                  "payload": {
                                  "subject": "Test subject",
                                  "body": "Test body",
                                    "emails": ["qa1@test.com", "qa2@test.com"],
                                    "userIds": ["U123", "U456"],
                                    "template": "WELCOME_EMAIL"
                                  },
                                  "maxRetries": 3
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("Only admins can create email jobs for multiple recipients"));
    }

    @Test
    void adminShouldBeAbleToCreateBulkEmailJob() throws Exception {
        mockMvc.perform(post("/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobName": "SEND_EMAIL",
                                  "scheduleType": "DELAYED",
                                  "schedule": {
                                    "delaySeconds": 600
                                  },
                                  "payload": {
                                  "subject": "Test subject",
                                  "body": "Test body",
                                    "emails": ["qa1@test.com", "qa2@test.com"],
                                    "userIds": ["U123", "U456"],
                                    "template": "WELCOME_EMAIL"
                                  },
                                  "maxRetries": 3
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobName").value("SEND_EMAIL"))
                .andExpect(jsonPath("$.createdBy").value("test-admin"));

        assertThat(jobRepository.count()).isEqualTo(1);
    }

    @Test
    void normalUserShouldNotCreateBulkNotificationJob() throws Exception {
        mockMvc.perform(post("/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, userAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobName": "SEND_NOTIFICATION",
                                  "scheduleType": "DELAYED",
                                  "schedule": {
                                    "delaySeconds": 600
                                  },
                                  "payload": {
                                    "userIds": ["U123", "U456"],
                                    "title": "Bulk Notification",
                                    "message": "Hello team"
                                  },
                                  "maxRetries": 3
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("Only admins can create notification jobs for multiple recipients"));
    }

    @Test
    void normalUserShouldCreateNotificationJobForSelf() throws Exception {
        mockMvc.perform(post("/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, userAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobName": "SEND_NOTIFICATION",
                                  "scheduleType": "DELAYED",
                                  "schedule": {
                                    "delaySeconds": 600
                                  },
                                  "payload": {
                                    "userId": "test-user",
                                    "title": "Personal Notification",
                                    "message": "Hello self"
                                  },
                                  "maxRetries": 3
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobName").value("SEND_NOTIFICATION"));
    }

    @Test
    void normalUserShouldNotCreateNotificationJobForAnotherUser() throws Exception {
        mockMvc.perform(post("/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, userAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobName": "SEND_NOTIFICATION",
                                  "scheduleType": "DELAYED",
                                  "schedule": {
                                    "delaySeconds": 600
                                  },
                                  "payload": {
                                    "userId": "other-user",
                                    "title": "Foreign Notification",
                                    "message": "This should not be allowed"
                                  },
                                  "maxRetries": 3
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("Users can create notification jobs only for themselves"));
    }

    @Test
    void adminShouldCreateFailedJobsReportJob() throws Exception {
        mockMvc.perform(post("/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobName": "GENERATE_REPORT",
                                  "scheduleType": "DELAYED",
                                  "schedule": {
                                    "delaySeconds": 300
                                  },
                                  "payload": {
                                    "reportType": "FAILED_JOBS_REPORT",
                                    "window": "LAST_30_DAYS"
                                  },
                                  "maxRetries": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobName").value("GENERATE_REPORT"))
                .andExpect(jsonPath("$.createdBy").value("test-admin"));
    }

    @Test
    void normalUserShouldNotCreateFailedJobsReportJob() throws Exception {
        mockMvc.perform(post("/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, userAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobName": "GENERATE_REPORT",
                                  "scheduleType": "DELAYED",
                                  "schedule": {
                                    "delaySeconds": 300
                                  },
                                  "payload": {
                                    "reportType": "FAILED_JOBS_REPORT",
                                    "window": "LAST_5_MINUTES"
                                  },
                                  "maxRetries": 2
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("Only admins can create failed jobs report requests"));
    }

    private long createJob(String request) throws Exception {
        String response = mockMvc.perform(post("/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, userAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return readJobId(response);
    }

    private long readJobId(String response) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(response);
        return jsonNode.get("jobId").asLong();
    }

    private void markClaimed(long jobId) {
        ScheduledJobLeaseEntity lease = leaseRepository.findById(jobId).orElseThrow();
        lease.setFirstClaimedAt(Instant.now());
        leaseRepository.save(lease);
    }

    private void markPreviouslyClaimedButUnlocked(long jobId) {
        ScheduledJobLeaseEntity lease = leaseRepository.findById(jobId).orElseThrow();
        lease.setFirstClaimedAt(Instant.now().minusSeconds(60));
        lease.setLockedBy(null);
        lease.setLockedAt(null);
        lease.setLockedUntil(null);
        leaseRepository.save(lease);
    }

    private void markCurrentlyLocked(long jobId) {
        ScheduledJobLeaseEntity lease = leaseRepository.findById(jobId).orElseThrow();
        lease.setFirstClaimedAt(Instant.now().minusSeconds(60));
        lease.setLockedBy("worker-1");
        lease.setLockedAt(Instant.now());
        lease.setLockedUntil(Instant.now().plusSeconds(30));
        leaseRepository.save(lease);
    }

    private String userAuth() {
        return bearer("test-user", UserRole.USER);
    }

    private String adminAuth() {
        return bearer("test-admin", UserRole.ADMIN);
    }

    private String bearer(String userId, UserRole role) {
        return "Bearer " + jwtService.generateToken(userId, role);
    }
}
