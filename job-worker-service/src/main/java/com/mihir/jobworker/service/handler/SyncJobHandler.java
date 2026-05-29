package com.mihir.jobworker.service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mihir.jobworker.config.WorkerProcessingProperties;
import com.mihir.jobworker.dto.JobExecutionMessage;
import com.mihir.jobworker.model.JobName;
import com.mihir.jobworker.service.ExecutionResult;
import com.mihir.jobworker.service.RemoteJobEndpointService;
import org.springframework.stereotype.Component;

@Component
public class SyncJobHandler extends AbstractPayloadValidator implements JobHandler {

    private final WorkerProcessingProperties processingProperties;
    private final RemoteJobEndpointService remoteJobEndpointService;

    public SyncJobHandler(
            ObjectMapper objectMapper,
            WorkerProcessingProperties processingProperties,
            RemoteJobEndpointService remoteJobEndpointService) {
        super(objectMapper);
        this.processingProperties = processingProperties;
        this.remoteJobEndpointService = remoteJobEndpointService;
    }

    @Override
    public JobName jobName() {
        return JobName.DATA_SYNC;
    }

    @Override
    public ExecutionResult execute(JobExecutionMessage message) {
        requireText(message.payload(), "source");
        requireText(message.payload(), "destination");
        return new ExecutionResult(
                remoteJobEndpointService.post(
                        message.jobId(),
                        message.executionId(),
                        message.jobName(),
                        processingProperties.endpoints().sync(),
                        message.payload()),
                "Data sync request sent");
    }
}
