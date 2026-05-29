package com.mihir.jobscheduler.exception;


public class JobConflictException extends RuntimeException {

    public JobConflictException(String message) {
        super(message);
    }
}
