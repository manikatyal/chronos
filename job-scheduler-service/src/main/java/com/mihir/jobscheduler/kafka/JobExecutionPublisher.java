package com.mihir.jobscheduler.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mihir.jobscheduler.config.KafkaTopicNames;
import com.mihir.jobscheduler.entity.JobEntity;
import com.mihir.jobscheduler.entity.ScheduledJobLeaseEntity;
import com.mihir.jobscheduler.exception.JobValidationException;
import com.mihir.jobscheduler.manager.JobManager;
import com.mihir.jobscheduler.manager.ScheduledJobLeaseManager;
import com.mihir.jobscheduler.service.JobExecutionStateService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class JobExecutionPublisher {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionPublisher.class);

    private final JobManager jobManager;
    private final ScheduledJobLeaseManager scheduledJobLeaseManager;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JobExecutionStateService stateService;

    public void publish(Long jobId, String workerId) {
        JobEntity job = jobManager.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found for lease claim: " + jobId));
        ScheduledJobLeaseEntity lease = scheduledJobLeaseManager.findByJobId(jobId)
                .orElseThrow(() -> new IllegalStateException("Lease not found for claimed job: " + jobId));

        Instant scheduledAt = lease.getNextRunAt();
        if (scheduledAt == null) {
            throw new IllegalStateException("Scheduled occurrence time is missing for claimed job: " + jobId);
        }
        String executionId = buildExecutionId(jobId, scheduledAt);
        String logTag = buildLogTag(executionId);

        try {
            kafkaTemplate.send(KafkaTopicNames.JOB_EXECUTION, String.valueOf(jobId), toMessageValue(job, executionId, logTag, scheduledAt))
                    .whenComplete((result, exception) -> {
                        if (exception == null) {
                            log.info("Published logTag={} jobId={} executionId={} workerId={} topic={}",
                                    logTag, jobId, executionId, workerId, KafkaTopicNames.JOB_EXECUTION);
                            stateService.markPublished(job, workerId, executionId, logTag);
                            return;
                        }
                        log.warn("Kafka publish failed asynchronously logTag={} jobId={} executionId={} workerId={} reason={}",
                                logTag, jobId, executionId, workerId, exception.getMessage());
                        stateService.triggerRetry(job, workerId, executionId, logTag, exception);
                    });
        } catch (Exception exception) {
            log.warn("Kafka publish failed synchronously logTag={} jobId={} executionId={} workerId={} reason={}",
                    logTag, jobId, executionId, workerId, exception.getMessage());
            stateService.triggerRetry(job, workerId, executionId, logTag, exception);
        }
    }

    private String toMessageValue(JobEntity job, String executionId, String logTag, Instant scheduledAt) {
        try {
            JsonNode payload = objectMapper.readTree(job.getPayloadJson());
            JobExecutionMessage message = new JobExecutionMessage(
                    job.getJobId(),
                    executionId,
                    logTag,
                    scheduledAt,
                    job.getJobName(),
                    job.getScheduleType(),
                    payload);
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new JobValidationException("Unable to serialize Kafka execution message");
        }
    }

    private String buildExecutionId(Long jobId, Instant scheduledAt) {
        return jobId + "_" + scheduledAt;
    }

    private String buildLogTag(String executionId) {
        return "chronos:" + executionId;
    }
}
