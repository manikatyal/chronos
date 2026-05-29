package com.mihir.jobworker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mihir.jobworker.dto.JobExecutionMessage;
import com.mihir.jobworker.dto.JobExecutionResponse;
import com.mihir.jobworker.entity.JobExecutionEntity;
import com.mihir.jobworker.exception.JobExecutionNotFoundException;
import com.mihir.jobworker.model.ExecutionStatus;
import com.mihir.jobworker.repo.JobExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
public class JobExecutionService {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionService.class);

    private final JobExecutionRepository jobExecutionRepository;
    private final JobHandlerRegistry handlerRegistry;
    private final JobExecutorRegistry jobExecutorRegistry;
    private final ObjectMapper objectMapper;
    private final WorkerFailureBackdoorService workerFailureBackdoorService;

    public void process(JobExecutionMessage message) {
        JobExecutionEntity existing = jobExecutionRepository.findByExecutionId(message.executionId()).orElse(null);
        if (existing != null) {
            log.info("Skipping duplicate job execution logTag={} jobId={} executionId={} status={}",
                    message.logTag(), message.jobId(), message.executionId(), existing.getStatus());
            return;
        }

        JobExecutionEntity execution = JobExecutionEntity.builder()
                .jobId(message.jobId())
                .executionId(message.executionId())
                .logTag(message.logTag())
                .scheduledAt(message.scheduledAt())
                .jobName(message.jobName())
                .status(ExecutionStatus.PROCESSING)
                .payloadJson(toJson(message.payload()))
                .build();

        try {
            jobExecutionRepository.saveAndFlush(execution);
        } catch (DataIntegrityViolationException exception) {
            JobExecutionEntity duplicate = jobExecutionRepository.findByExecutionId(message.executionId())
                    .orElseThrow(() -> exception);
            log.info("Job execution already persisted for logTag={} jobId={} executionId={} status={}",
                    message.logTag(), message.jobId(), message.executionId(), duplicate.getStatus());
            return;
        }

        try {
            ExecutionResult result = jobExecutorRegistry.execute(
                    message.jobName(),
                    () -> handlerRegistry.get(message.jobName()).execute(message));
            execution.setStatus(ExecutionStatus.COMPLETED);
            execution.setResultJson(toJson(result.result()));
            execution.setFailureReason(null);
            execution.setProcessedAt(Instant.now());
            jobExecutionRepository.save(execution);
            log.info("Job executed successfully logTag={} jobId={} executionId={} jobName={} summary={}",
                    message.logTag(), message.jobId(), message.executionId(), message.jobName(), result.summary());
        } catch (CompletionException exception) {
            markFailed(message, execution, exception.getCause() == null ? exception : exception.getCause());
        } catch (Exception exception) {
            markFailed(message, execution, exception);
        }
    }

    public JobExecutionResponse getByJobId(Long jobId) {
        return jobExecutionRepository.findTopByJobIdOrderByCreatedAtDesc(jobId)
                .map(this::toResponse)
                .orElseThrow(() -> new JobExecutionNotFoundException("Execution not found for jobId: " + jobId));
    }

    public List<JobExecutionResponse> getAll() {
        return jobExecutionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<JobExecutionResponse> getFailed() {
        return jobExecutionRepository.findAllByStatusOrderByProcessedAtDesc(ExecutionStatus.FAILED).stream()
                .map(this::toResponse)
                .toList();
    }

    private void markFailed(JobExecutionMessage message, JobExecutionEntity execution, Throwable exception) {
        execution.setStatus(ExecutionStatus.FAILED);
        execution.setFailureReason(trimReason(exception));
        execution.setProcessedAt(Instant.now());
        jobExecutionRepository.save(execution);
        log.error("Job execution failed logTag={} jobId={} executionId={} jobName={} reason={}",
                execution.getLogTag(), execution.getJobId(), execution.getExecutionId(), execution.getJobName(), execution.getFailureReason());
        workerFailureBackdoorService.reportFailure(message, execution.getFailureReason());
    }

    private JobExecutionResponse toResponse(JobExecutionEntity entity) {
        return new JobExecutionResponse(
                entity.getJobId(),
                entity.getExecutionId(),
                entity.getLogTag(),
                entity.getScheduledAt(),
                entity.getJobName(),
                entity.getStatus(),
                readJson(entity.getPayloadJson()),
                readJson(entity.getResultJson()),
                entity.getFailureReason(),
                entity.getProcessedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize execution JSON", exception);
        }
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read stored execution JSON", exception);
        }
    }

    private String trimReason(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
