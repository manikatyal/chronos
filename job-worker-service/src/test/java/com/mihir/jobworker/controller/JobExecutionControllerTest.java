package com.mihir.jobworker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mihir.jobworker.dto.JobExecutionMessage;
import com.mihir.jobworker.dto.SchedulerFailedJobResponse;
import com.mihir.jobworker.entity.JobExecutionEntity;
import com.mihir.jobworker.model.ExecutionStatus;
import com.mihir.jobworker.model.JobName;
import com.mihir.jobworker.model.NewsCategory;
import com.mihir.jobworker.model.ScheduleType;
import com.mihir.jobworker.repo.JobExecutionRepository;
import com.mihir.jobworker.service.RealtimeHub;
import com.mihir.jobworker.service.EmailDeliveryService;
import com.mihir.jobworker.service.JobExecutionService;
import com.mihir.jobworker.service.RemoteJobEndpointService;
import com.mihir.jobworker.service.SchedulerFailedJobsClient;
import com.mihir.jobworker.service.WorkerFailureBackdoorService;
import com.mihir.jobworker.service.NewsFeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JobExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private JobExecutionRepository jobExecutionRepository;

    @MockBean
    private EmailDeliveryService emailDeliveryService;

    @MockBean
    private RemoteJobEndpointService remoteJobEndpointService;

    @MockBean
    private WorkerFailureBackdoorService workerFailureBackdoorService;

    @MockBean
    private SchedulerFailedJobsClient schedulerFailedJobsClient;

    @MockBean
    private NewsFeedService newsFeedService;

    @MockBean
    private RealtimeHub realtimeHub;

    @BeforeEach
    void clean() {
        jobExecutionRepository.deleteAll();

        ObjectNode emailResult = objectMapper.createObjectNode();
        emailResult.put("action", "EMAIL_SENT");
        emailResult.put("messageId", "email-101_2026-03-25T10:00:00Z");
        when(emailDeliveryService.send(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(emailResult);
        when(emailDeliveryService.send(anyLong(), anyString(), anyList(), anyString(), anyString())).thenReturn(emailResult);

        ObjectNode remoteResult = objectMapper.createObjectNode();
        remoteResult.put("action", "REMOTE_REQUEST_SENT");
        remoteResult.put("httpStatus", 200);
        when(remoteJobEndpointService.post(anyLong(), anyString(), any(), anyString(), any())).thenReturn(remoteResult);

        ObjectNode newsResult = objectMapper.createObjectNode();
        newsResult.put("action", "NEWS_HEADLINES_FETCHED");
        newsResult.put("category", "INDIA");
        newsResult.put("categoryLabel", "India latest news");
        newsResult.put("articleCount", 2);
        newsResult.putArray("articles")
                .addObject()
                .put("headline", "India headline one")
                .put("link", "https://news.example.com/one");
        newsResult.withArray("articles")
                .addObject()
                .put("headline", "India headline two")
                .put("link", "https://news.example.com/two");
        when(newsFeedService.fetch(any(NewsCategory.class), anyInt())).thenReturn(newsResult);
    }

    @Test
    void getExecutionShouldReturnProcessedJob() throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("email", "qa@test.com");
        payload.put("template", "WELCOME_EMAIL");
payload.put("subject", "Test subject");
payload.put("body", "Test body");
        payload.put("userId", "USR-1");

        jobExecutionService.process(new JobExecutionMessage(
                101L,
                "101_2026-03-25T10:00:00Z",
                "chronos:101_2026-03-25T10:00:00Z",
                Instant.parse("2026-03-25T10:00:00Z"),
                JobName.SEND_EMAIL,
                ScheduleType.DELAYED,
                payload));

        mockMvc.perform(get("/v1/executions/{jobId}", 101L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(101))
                .andExpect(jsonPath("$.executionId").value("101_2026-03-25T10:00:00Z"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.action").value("EMAIL_SENT"));
    }

    @Test
    void duplicateProcessingShouldKeepSingleExecutionRowPerOccurrence() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("email", "qa@test.com");
        payload.put("template", "WELCOME_EMAIL");
payload.put("subject", "Test subject");
payload.put("body", "Test body");
        payload.put("userId", "USR-1");

        JobExecutionMessage first = new JobExecutionMessage(
                202L,
                "202_2026-03-25T10:00:00Z",
                "chronos:202_2026-03-25T10:00:00Z",
                Instant.parse("2026-03-25T10:00:00Z"),
                JobName.SEND_EMAIL,
                ScheduleType.CRON,
                payload);
        JobExecutionMessage duplicate = new JobExecutionMessage(
                202L,
                "202_2026-03-25T10:00:00Z",
                "chronos:202_2026-03-25T10:00:00Z",
                Instant.parse("2026-03-25T10:00:00Z"),
                JobName.SEND_EMAIL,
                ScheduleType.CRON,
                payload);
        JobExecutionMessage nextOccurrence = new JobExecutionMessage(
                202L,
                "202_2026-03-25T10:10:00Z",
                "chronos:202_2026-03-25T10:10:00Z",
                Instant.parse("2026-03-25T10:10:00Z"),
                JobName.SEND_EMAIL,
                ScheduleType.CRON,
                payload);

        jobExecutionService.process(first);
        jobExecutionService.process(duplicate);
        jobExecutionService.process(nextOccurrence);

        assertThat(jobExecutionRepository.count()).isEqualTo(2);
        JobExecutionEntity latestExecution = jobExecutionRepository.findTopByJobIdOrderByCreatedAtDesc(202L).orElseThrow();
        assertThat(latestExecution.getExecutionId()).isEqualTo("202_2026-03-25T10:10:00Z");
        assertThat(latestExecution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        verify(emailDeliveryService, times(2)).send(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void getFailedShouldReturnFailedExecutions() throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("template", "WELCOME_EMAIL");
payload.put("subject", "Test subject");
payload.put("body", "Test body");
        payload.put("userId", "USR-2");

        jobExecutionService.process(new JobExecutionMessage(
                303L,
                "303_2026-03-25T10:00:00Z",
                "chronos:303_2026-03-25T10:00:00Z",
                Instant.parse("2026-03-25T10:00:00Z"),
                JobName.SEND_EMAIL,
                ScheduleType.DELAYED,
                payload));

        mockMvc.perform(get("/v1/executions/failed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId").value(303))
                .andExpect(jsonPath("$[0].executionId").value("303_2026-03-25T10:00:00Z"))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].failureReason").value("payload.email is required"));
    }

    @Test
    void bulkEmailProcessingShouldExecuteOnceForSingleOccurrence() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("template", "WELCOME_EMAIL");
payload.put("subject", "Test subject");
payload.put("body", "Test body");
        payload.putArray("emails")
                .add("qa1@test.com")
                .add("qa2@test.com");
        payload.putArray("userIds")
                .add("USR-1")
                .add("USR-2");

        JobExecutionMessage message = new JobExecutionMessage(
                404L,
                "404_2026-03-25T10:00:00Z",
                "chronos:404_2026-03-25T10:00:00Z",
                Instant.parse("2026-03-25T10:00:00Z"),
                JobName.SEND_EMAIL,
                ScheduleType.DELAYED,
                payload);

        jobExecutionService.process(message);
        jobExecutionService.process(message);

        assertThat(jobExecutionRepository.count()).isEqualTo(1);
        verify(emailDeliveryService, times(1)).send(anyLong(), anyString(), anyList(), anyString(), anyString());
    }

    @Test
    void generateFailedJobsReportShouldWriteCsvForRequestedWindow() throws Exception {
        when(schedulerFailedJobsClient.fetchFailedJobsSince(any())).thenReturn(List.of(
                new SchedulerFailedJobResponse(
                        505L,
                        "SEND_EMAIL",
                        "DELAYED",
                        "FAILED",
                        3,
                        "alice.user",
                        Instant.parse("2026-03-20T10:00:00Z"),
                        "WORKER",
                        "505_2026-03-25T10:00:00Z",
                        "chronos:505_2026-03-25T10:00:00Z",
                        "job-worker",
                        0,
                        "SMTP timeout",
                        Instant.parse("2026-03-25T10:02:00Z")),
                new SchedulerFailedJobResponse(
                        606L,
                        "DATA_SYNC",
                        "CRON",
                        "FAILED",
                        5,
                        "admin",
                        Instant.parse("2026-03-19T08:00:00Z"),
                        "SCHEDULER",
                        null,
                        null,
                        "scheduler-node-1",
                        5,
                        "Kafka unavailable",
                        Instant.parse("2026-03-25T10:03:00Z"))));

        ObjectNode reportPayload = objectMapper.createObjectNode();
        reportPayload.put("reportType", "FAILED_JOBS_REPORT");
        reportPayload.put("window", "LAST_5_DAYS");

        jobExecutionService.process(new JobExecutionMessage(
                507L,
                "507_2026-03-25T10:05:00Z",
                "chronos:507_2026-03-25T10:05:00Z",
                Instant.parse("2026-03-25T10:05:00Z"),
                JobName.GENERATE_REPORT,
                ScheduleType.DELAYED,
                reportPayload));

        JobExecutionEntity reportExecution = jobExecutionRepository.findTopByJobIdOrderByCreatedAtDesc(507L).orElseThrow();
        assertThat(reportExecution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);

        JsonNode result = objectMapper.readTree(reportExecution.getResultJson());
        assertThat(result.get("reportType").asText()).isEqualTo("FAILED_JOBS_REPORT");
        assertThat(result.get("window").asText()).isEqualTo("LAST_5_DAYS");
        assertThat(result.get("rowCount").asInt()).isEqualTo(2);

        Path reportPath = Path.of(result.get("filePath").asText());
        assertThat(Files.exists(reportPath)).isTrue();
        String csv = Files.readString(reportPath);
        assertThat(csv).contains("jobId,jobName,scheduleType,status,failureSource,executionId,logTag,workerId,retryCount,failureReason,failedAt,createdBy,createdAt");
        assertThat(csv).contains("505");
        assertThat(csv).contains("606");
        assertThat(csv).contains("SMTP timeout");
        assertThat(csv).contains("Kafka unavailable");
    }

    @Test
    void fetchNewsHeadlinesShouldStoreHeadlinesAndLinks() throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("category", "INDIA");
        payload.put("limit", 2);
        payload.put("requesterUserId", "USR-1");

        jobExecutionService.process(new JobExecutionMessage(
                808L,
                "808_2026-03-31T10:00:00Z",
                "chronos:808_2026-03-31T10:00:00Z",
                Instant.parse("2026-03-31T10:00:00Z"),
                JobName.FETCH_NEWS_HEADLINES,
                ScheduleType.DELAYED,
                payload));

        mockMvc.perform(get("/v1/executions/{jobId}", 808L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.action").value("NEWS_HEADLINES_FETCHED"))
                .andExpect(jsonPath("$.result.category").value("INDIA"))
                .andExpect(jsonPath("$.result.categoryLabel").value("India latest news"))
                .andExpect(jsonPath("$.result.articleCount").value(2))
                .andExpect(jsonPath("$.result.articles[0].headline").value("India headline one"))
                .andExpect(jsonPath("$.result.articles[0].link").value("https://news.example.com/one"));
    }
}
