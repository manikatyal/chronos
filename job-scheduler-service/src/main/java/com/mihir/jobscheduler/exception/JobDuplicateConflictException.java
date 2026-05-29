package com.mihir.jobscheduler.exception;

import com.mihir.jobscheduler.dto.JobResponse;

public class JobDuplicateConflictException extends RuntimeException {

    private final JobResponse jobResponse;

    public JobDuplicateConflictException(String message, JobResponse jobResponse) {
        super(message);
        this.jobResponse = jobResponse;
    }

    public JobResponse getJobResponse() {
        return jobResponse;
    }
}
