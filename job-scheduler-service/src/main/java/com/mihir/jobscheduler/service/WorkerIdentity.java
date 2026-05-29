package com.mihir.jobscheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class WorkerIdentity {

    private static final Logger log = LoggerFactory.getLogger(WorkerIdentity.class);

    private final String workerId = UUID.randomUUID().toString();

    public WorkerIdentity() {
        log.info("Chronos worker identity initialized: {}", workerId);
    }

    public String value() {
        return workerId;
    }
}
