package com.mihir.jobscheduler.service.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.mihir.jobscheduler.model.JobName;

public interface JobHandler {

    JobName supports();

    void validatePayload(JsonNode payload);
}
