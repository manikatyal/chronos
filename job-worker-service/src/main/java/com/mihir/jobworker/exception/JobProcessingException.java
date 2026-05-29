package com.mihir.jobworker.exception;

public class JobProcessingException extends RuntimeException {

    public JobProcessingException(String message) {
        super(message);
    }
}
