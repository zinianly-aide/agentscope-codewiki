package com.agentscope.codewiki.dify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * OpenAI-compatible embedding provider for Dify-compatible endpoints.
 *
 * <p>Calls:
 *
 * <pre>{@code
 * POST {baseUrl}/v1/embeddings
 * Authorization: Bearer {apiKey}
 * Content-Type: application/json
 * }</pre>
 *
 * <p>This class only generates vectors. It does not store vectors, perform retrieval, or implement
 * any vector database integration.
 */
public class DifyEmbeddingProvider implements EmbeddingProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;

    public DifyEmbeddingProvider(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, HttpClient.newBuilder().connectTimeout(DEFAULT_TIMEOUT).build());
    }

    DifyEmbeddingProvider(String baseUrl, String apiKey, HttpClient httpClient) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey;
        this.httpClient = httpClient;
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) throws IOException {
        validate(request);
        try {
            String body = MAPPER.writeValueAsString(request);
            HttpRequest httpRequest =
                    HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/v1/embeddings"))
                            .header("Authorization", "Bearer " + apiKey)
                            .header("Content-Type", "application/json")
                            .timeout(DEFAULT_TIMEOUT)
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();

            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new DifyException(
                        "Dify embedding API returned "
                                + response.statusCode()
                                + ": "
                                + response.body());
            }

            return MAPPER.readValue(response.body(), EmbeddingResponse.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Embedding request interrupted", e);
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static void validate(EmbeddingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("EmbeddingRequest must not be null");
        }
        if (request.getModel() == null || request.getModel().isBlank()) {
            throw new IllegalArgumentException("Embedding model must not be blank");
        }
        if (request.getInput() == null || request.getInput().isEmpty()) {
            throw new IllegalArgumentException("Embedding input must not be empty");
        }
    }
}
