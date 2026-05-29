package com.mihir.jobscheduler.service.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.mihir.jobscheduler.model.JobName;
import org.springframework.stereotype.Component;

@Component
public class PaymentJobHandler extends AbstractPayloadValidator {

    @Override
    public JobName supports() {
        return JobName.RETRY_PAYMENT;
    }

    @Override
    public void validatePayload(JsonNode payload) {
        requireText(payload, "paymentId");
        requireText(payload, "orderId");
        requireNumber(payload, "amount");
        requireText(payload, "currency");
    }
}
