package com.mihir.jobscheduler.service.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.mihir.jobscheduler.model.JobName;
import org.springframework.stereotype.Component;

@Component
public class SyncJobHandler extends AbstractPayloadValidator {

    @Override
    public JobName supports() {
        return JobName.DATA_SYNC;
    }

    @Override
    public void validatePayload(JsonNode payload) {
        requireText(payload, "source");
        requireText(payload, "destination");
    }
}
