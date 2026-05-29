package com.mihir.jobscheduler.service.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.mihir.jobscheduler.model.JobName;
import org.springframework.stereotype.Component;

@Component
public class OrderJobHandler extends AbstractPayloadValidator {

    @Override
    public JobName supports() {
        return JobName.ORDER_EXPIRY;
    }

    @Override
    public void validatePayload(JsonNode payload) {
        requireText(payload, "orderId");
        requireText(payload, "userId");
    }
}
