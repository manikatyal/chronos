package com.mihir.jobscheduler.service.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.mihir.jobscheduler.exception.JobValidationException;
import com.mihir.jobscheduler.model.JobName;
import com.mihir.jobscheduler.model.NewsCategory;
import org.springframework.stereotype.Component;

@Component
public class NewsHeadlinesJobHandler extends AbstractPayloadValidator {

    @Override
    public JobName supports() {
        return JobName.FETCH_NEWS_HEADLINES;
    }

    @Override
    public void validatePayload(JsonNode payload) {
        requireText(payload, "requesterUserId");
        requireText(payload, "category");
        String category = payload.get("category").asText();
        try {
            NewsCategory.from(category);
        } catch (IllegalArgumentException exception) {
            throw new JobValidationException(exception.getMessage());
        }
        requireNumber(payload, "limit");

        int limit = payload.get("limit").asInt();
        if (limit < 1 || limit > 20) {
            throw new JobValidationException("payload.limit must be between 1 and 20");
        }
    }
}
