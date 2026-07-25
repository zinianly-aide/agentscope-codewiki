package com.agentscope.codewiki.dify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Low-level HTTP client for the Dify Chat API.
 *
 * <p>Uses {@code java.net.http.HttpClient} (Java 21) — zero extra dependencies beyond Jackson.
 */
public class DifyClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final Duration timeout;

    /**
     * Create a Dify API client.
     *
     * @param baseUrl Dify instance base URL (e.g., "https://api.dify.ai")
     * @param apiKey API key from Dify app settings
     */
    public DifyClient(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, DEFAULT_TIMEOUT);
    }

    /** Create a Dify API client with a custom request timeout. */
    public DifyClient(String baseUrl, String apiKey, Duration timeout) {
        this(baseUrl, apiKey, timeout, HttpClient.newBuilder().connectTimeout(timeout).build());
    }

    /** Constructor for testing — inject a custom HttpClient. */
    DifyClient(String baseUrl, String apiKey, HttpClient httpClient) {
        this(baseUrl, apiKey, DEFAULT_TIMEOUT, httpClient);
    }

    /** Constructor for testing — inject a custom timeout and HttpClient. */
    DifyClient(String baseUrl, String apiKey, Duration timeout, HttpClient httpClient) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.httpClient = httpClient;
    }

    /**
     * Send a chat request.
     *
     * @param request the chat request
     * @return the chat response
     * @throws DifyException on HTTP errors or non-200 responses
     * @throws IOException on network errors
     */
    public DifyChatResponse chat(DifyChatRequest request) throws IOException {
        try {
            String body = MAPPER.writeValueAsString(request);
            HttpRequest httpRequest =
                    HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/v1/chat-messages"))
                            .header("Authorization", "Bearer " + apiKey)
                            .header("Content-Type", "application/json")
                            .timeout(timeout)
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();

            HttpResponse<String> httpResponse =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() != 200) {
                throw new DifyException(
                        "Dify API returned "
                                + httpResponse.statusCode()
                                + ": "
                                + httpResponse.body());
            }

            if (DifyResponseMode.STREAMING.wireValue().equals(request.getResponseMode())) {
                return parseStreamingResponse(httpResponse.body());
            }
            return MAPPER.readValue(httpResponse.body(), DifyChatResponse.class);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Dify request interrupted", e);
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    private DifyChatResponse parseStreamingResponse(String body) throws IOException {
        DifyChatResponse aggregated = new DifyChatResponse();
        StringBuilder answer = new StringBuilder();
        for (String rawLine : body.split("\\R")) {
            String line = rawLine.strip();
            if (!line.startsWith("data:")) {
                continue;
            }
            String payload = line.substring("data:".length()).strip();
            if (payload.isBlank() || "[DONE]".equals(payload)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> event = MAPPER.readValue(payload, Map.class);
            String eventName = text(event.get("event"));
            if ("error".equals(eventName)) {
                throw new DifyException("Dify streaming error: " + text(event.get("message")));
            }
            aggregated.setEvent(eventName);
            copyIfPresent(event, "message_id", aggregated::setMessageId);
            copyIfPresent(event, "conversation_id", aggregated::setConversationId);
            Object createdAt = event.get("created_at");
            if (createdAt instanceof Number number) {
                aggregated.setCreatedAt(number.longValue());
            }
            String chunk = text(event.get("answer"));
            if (!chunk.isBlank()) {
                answer.append(chunk);
            }
        }
        aggregated.setAnswer(answer.toString());
        return aggregated;
    }

    private static void copyIfPresent(
            Map<String, Object> event, String key, java.util.function.Consumer<String> setter) {
        String value = text(event.get(key));
        if (!value.isBlank()) {
            setter.accept(value);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Dify baseUrl must not be blank");
        }
        String normalized = value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        if (normalized.endsWith("/v1")) {
            return normalized.substring(0, normalized.length() - "/v1".length());
        }
        return normalized;
    }
}
