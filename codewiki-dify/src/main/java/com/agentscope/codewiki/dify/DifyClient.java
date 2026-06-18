package com.agentscope.codewiki.dify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.httpClient = httpClient;
    }

    /**
     * Send a blocking chat request.
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

            return MAPPER.readValue(httpResponse.body(), DifyChatResponse.class);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Dify request interrupted", e);
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
