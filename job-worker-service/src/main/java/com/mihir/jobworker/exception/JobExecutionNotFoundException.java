package com.mihir.jobworker.exception;

public class JobExecutionNotFoundException extends RuntimeException {

    public JobExecutionNotFoundException(String message) {
        super(message);
    }
}
