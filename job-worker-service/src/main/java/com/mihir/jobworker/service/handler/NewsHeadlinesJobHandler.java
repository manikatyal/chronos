package com.mihir.jobworker.service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mihir.jobworker.dto.JobExecutionMessage;
import com.mihir.jobworker.exception.JobProcessingException;
import com.mihir.jobworker.model.JobName;
import com.mihir.jobworker.service.RealtimeHub;
import com.mihir.jobworker.service.ExecutionResult;
import com.mihir.jobworker.model.NewsCategory;
import com.mihir.jobworker.service.NewsFeedService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class NewsHeadlinesJobHandler extends AbstractPayloadValidator implements JobHandler {

    private final NewsFeedService newsFeedService;
    private final RealtimeHub realtimeHub;

    public NewsHeadlinesJobHandler(
            ObjectMapper objectMapper,
            NewsFeedService newsFeedService,
            RealtimeHub realtimeHub) {
        super(objectMapper);
        this.newsFeedService = newsFeedService;
        this.realtimeHub = realtimeHub;
    }

    @Override
    public JobName jobName() {
        return JobName.FETCH_NEWS_HEADLINES;
    }

    @Override
    public ExecutionResult execute(JobExecutionMessage message) {
        String category = requireText(message.payload(), "category");
        NewsCategory newsCategory;
        try {
            newsCategory = NewsCategory.from(category);
        } catch (IllegalArgumentException exception) {
            throw new JobProcessingException(exception.getMessage());
        }
        int limit = message.payload().path("limit").asInt();
        if (limit < 1 || limit > 20) {
            throw new JobProcessingException("payload.limit must be between 1 and 20");
        }
        String requesterUserId = optionalText(message.payload(), "requesterUserId");

        ObjectNode headlines = newsFeedService.fetch(newsCategory, limit);
        realtimeHub.pushNews(
                message.jobId(),
                message.executionId(),
                message.jobName(),
                headlines,
                requesterUserId);
        return new ExecutionResult(
                headlines,
                newsCategory.displayName() + " fetched");
    }
}
