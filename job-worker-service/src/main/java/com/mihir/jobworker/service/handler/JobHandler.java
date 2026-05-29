package com.mihir.jobworker.service.handler;

import com.mihir.jobworker.dto.JobExecutionMessage;
import com.mihir.jobworker.model.JobName;
import com.mihir.jobworker.service.ExecutionResult;

public interface JobHandler {

    JobName jobName();

    ExecutionResult execute(JobExecutionMessage message);
}
