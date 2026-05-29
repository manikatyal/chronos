package com.mihir.jobworker.service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mihir.jobworker.config.WorkerProcessingProperties;
import com.mihir.jobworker.dto.JobExecutionMessage;
import com.mihir.jobworker.model.JobName;
import com.mihir.jobworker.service.ExecutionResult;
import com.mihir.jobworker.service.RemoteJobEndpointService;
import org.springframework.stereotype.Component;

@Component
public class OrderJobHandler extends AbstractPayloadValidator implements JobHandler {

    private final WorkerProcessingProperties processingProperties;
    private final RemoteJobEndpointService remoteJobEndpointService;

    public OrderJobHandler(
            ObjectMapper objectMapper,
            WorkerProcessingProperties processingProperties,
            RemoteJobEndpointService remoteJobEndpointService) {
        super(objectMapper);
        this.processingProperties = processingProperties;
        this.remoteJobEndpointService = remoteJobEndpointService;
    }

    @Override
    public JobName jobName() {
        return JobName.ORDER_EXPIRY;
    }

    @Override
    public ExecutionResult execute(JobExecutionMessage message) {
        requireText(message.payload(), "orderId");
        requireText(message.payload(), "userId");
        return new ExecutionResult(
                remoteJobEndpointService.post(
                        message.jobId(),
                        message.executionId(),
                        message.jobName(),
                        processingProperties.endpoints().order(),
                        message.payload()),
                "Order expiry request sent");
    }
}
