package com.mihir.jobworker.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mihir.jobworker.config.KafkaTopicNames;
import com.mihir.jobworker.dto.JobExecutionMessage;
import com.mihir.jobworker.service.JobExecutionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobExecutionConsumer {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionConsumer.class);

    private final ObjectMapper objectMapper;
    private final JobExecutionService jobExecutionService;

    @KafkaListener(
            topics = "${chronos.worker.topic.job-execution:" + KafkaTopicNames.JOB_EXECUTION + "}",
            groupId = "${spring.kafka.consumer.group-id:job-worker}")
    public void consume(String payload) throws Exception {
        JobExecutionMessage message = objectMapper.readValue(payload, JobExecutionMessage.class);
        log.info("Received job execution message logTag={} jobId={} executionId={} jobName={}",
                message.logTag(), message.jobId(), message.executionId(), message.jobName());
        jobExecutionService.process(message);
    }
}
