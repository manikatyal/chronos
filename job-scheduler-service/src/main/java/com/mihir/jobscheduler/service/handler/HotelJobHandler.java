package com.mihir.jobscheduler.service.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.mihir.jobscheduler.model.JobName;
import org.springframework.stereotype.Component;

@Component
public class HotelJobHandler extends AbstractPayloadValidator {

    @Override
    public JobName supports() {
        return JobName.HOTEL_NIGHT_AUDIT;
    }

    @Override
    public void validatePayload(JsonNode payload) {
        requireText(payload, "hotelId");
    }
}
