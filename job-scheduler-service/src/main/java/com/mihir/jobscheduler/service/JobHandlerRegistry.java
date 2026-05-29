package com.mihir.jobscheduler.service;

import com.mihir.jobscheduler.exception.JobValidationException;
import com.mihir.jobscheduler.model.JobName;
import com.mihir.jobscheduler.service.handler.JobHandler;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class JobHandlerRegistry {

    private final Map<JobName, JobHandler> handlers;

    public JobHandlerRegistry(List<JobHandler> discoveredHandlers) {
        this.handlers = new EnumMap<>(JobName.class);
        for (JobHandler handler : discoveredHandlers) {
            handlers.put(handler.supports(), handler);
        }
    }

    public JobHandler get(JobName jobName) {
        JobHandler handler = handlers.get(jobName);
        if (handler == null) {
            throw new JobValidationException("Unsupported jobName: " + jobName);
        }
        return handler;
    }
}
