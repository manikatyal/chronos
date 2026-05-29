package com.mihir.jobworker.service;

import com.mihir.jobworker.exception.JobProcessingException;
import com.mihir.jobworker.model.JobName;
import com.mihir.jobworker.service.handler.JobHandler;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class JobHandlerRegistry {

    private final Map<JobName, JobHandler> handlers = new EnumMap<>(JobName.class);

    public JobHandlerRegistry(List<JobHandler> handlers) {
        for (JobHandler handler : handlers) {
            this.handlers.put(handler.jobName(), handler);
        }
    }

    public JobHandler get(JobName jobName) {
        JobHandler handler = handlers.get(jobName);
        if (handler == null) {
            throw new JobProcessingException("No worker handler configured for jobName: " + jobName);
        }
        return handler;
    }
}
