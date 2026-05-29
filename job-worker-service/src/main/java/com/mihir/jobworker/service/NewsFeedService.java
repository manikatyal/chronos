package com.mihir.jobworker.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mihir.jobworker.config.WorkerProcessingProperties;
import com.mihir.jobworker.exception.JobProcessingException;
import com.mihir.jobworker.model.NewsCategory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class NewsFeedService {

    private final RestClient restClient;
    private final WorkerProcessingProperties properties;
    private final ObjectMapper objectMapper;

    public NewsFeedService(RestClient restClient, WorkerProcessingProperties properties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ObjectNode fetch(NewsCategory category, int limit) {
        List<String> feedUrls = candidateFeedUrls(category);
        if (feedUrls.isEmpty()) {
            throw new JobProcessingException(category.displayName() + " feed URL is not configured");
        }

        String lastReason = "no feed content";
        for (String feedUrl : feedUrls) {
            try {
                String xml = restClient.get()
                        .uri(feedUrl)
                        .retrieve()
                        .body(String.class);
                if (xml == null || xml.isBlank()) {
                    lastReason = "News feed returned empty content from " + feedUrl;
                    continue;
                }

                ObjectNode result = toResult(feedUrl, xml, category, limit);
                if (result.path("articleCount").asInt(0) > 0) {
                    return result;
                }
                lastReason = "No articles found in feed " + feedUrl;
            } catch (RestClientException exception) {
                lastReason = "Unable to fetch from " + feedUrl + ": " + exception.getMessage();
            } catch (JobProcessingException exception) {
                lastReason = exception.getMessage();
            }
        }
        throw new JobProcessingException("Unable to fetch " + category.displayName() + " feed: " + lastReason);
    }

    private List<String> candidateFeedUrls(NewsCategory category) {
        Set<String> urls = new LinkedHashSet<>();
        if (properties.news() != null) {
            switch (category) {
                case INDIA -> addIfPresent(urls, properties.news().indiaHeadlinesUrl());
                case GOLD -> addIfPresent(urls, properties.news().goldPriceUrl());
                case STOCKS -> addIfPresent(urls, properties.news().stockUpdatesUrl());
            }
        }

        switch (category) {
            case INDIA -> {
                addIfPresent(urls, "https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en");
                addIfPresent(urls, "https://news.google.com/rss/search?q=india%20latest%20news&hl=en-IN&gl=IN&ceid=IN:en");
            }
            case GOLD -> {
                addIfPresent(urls, "https://news.google.com/rss/search?q=gold%20price%20india&hl=en-IN&gl=IN&ceid=IN:en");
                addIfPresent(urls, "https://news.google.com/rss/search?q=gold%20rate&hl=en-IN&gl=IN&ceid=IN:en");
            }
            case STOCKS -> {
                addIfPresent(urls, "https://news.google.com/rss/search?q=nifty%2050%20stock%20market&hl=en-IN&gl=IN&ceid=IN:en");
                addIfPresent(urls, "https://news.google.com/rss/search?q=indian%20stock%20market&hl=en-IN&gl=IN&ceid=IN:en");
                addIfPresent(urls, "https://news.google.com/rss/search?q=share%20market%20india&hl=en-IN&gl=IN&ceid=IN:en");
            }
        }
        return new ArrayList<>(urls);
    }

    private void addIfPresent(Set<String> urls, String url) {
        if (url == null) {
            return;
        }
        String trimmed = url.trim();
        if (!trimmed.isBlank()) {
            urls.add(trimmed);
        }
    }

    private ObjectNode toResult(String feedUrl, String xml, NewsCategory category, int limit) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList items = document.getElementsByTagName("item");

            ObjectNode result = objectMapper.createObjectNode();
            result.put("action", "NEWS_HEADLINES_FETCHED");
            result.put("category", category.name());
            result.put("categoryLabel", category.displayName());
            result.put("sourceUrl", feedUrl);
            result.put("fetchedAt", Instant.now().toString());

            ArrayNode articles = result.putArray("articles");
            int safeLimit = Math.min(Math.max(limit, 1), items.getLength());
            for (int index = 0; index < safeLimit; index++) {
                Element item = (Element) items.item(index);
                ObjectNode article = articles.addObject();
                article.put("headline", text(item, "title"));
                article.put("link", text(item, "link"));
                String pubDate = text(item, "pubDate");
                if (!pubDate.isBlank()) {
                    article.put("publishedAt", pubDate);
                }
                String description = text(item, "description");
                if (!description.isBlank()) {
                    article.put("summary", description);
                }
            }
            result.put("articleCount", articles.size());
            return result;
        } catch (Exception exception) {
            throw new JobProcessingException("Unable to parse " + category.displayName() + " feed: " + exception.getMessage());
        }
    }

    private String text(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0) == null) {
            return "";
        }
        String value = nodes.item(0).getTextContent();
        return value == null ? "" : value.trim();
    }
}
