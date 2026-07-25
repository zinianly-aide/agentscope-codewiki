package com.agentscope.codewiki.web.enterprise;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class OpenAiCompatibleAgentModelClient implements AgentModelClient {
    private final EnterpriseAgentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleAgentModelClient(
            EnterpriseAgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public ModelChatResponse chat(ModelChatRequest request) {
        if (!properties.modelConfigured()) {
            throw new ModelClientException("MODEL_NOT_CONFIGURED", "model is not configured");
        }
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return doChat(request);
            } catch (ModelClientException ex) {
                if (("MODEL_RATE_LIMITED".equals(ex.errorCode())
                                || "MODEL_SERVER_ERROR".equals(ex.errorCode()))
                        && attempts == 1) {
                    sleep();
                    continue;
                }
                throw ex;
            }
        }
    }

    private ModelChatResponse doChat(ModelChatRequest request) {
        try {
            String body = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest =
                    HttpRequest.newBuilder(chatUri())
                            .timeout(Duration.ofSeconds(60))
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .header("Authorization", "Bearer " + properties.getApiKey())
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
            HttpResponse<InputStream> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                throw new ModelClientException("MODEL_AUTH_FAILED", "model authentication failed");
            }
            if (status == 429) {
                throw new ModelClientException("MODEL_RATE_LIMITED", "model rate limited");
            }
            if (status >= 500) {
                throw new ModelClientException("MODEL_SERVER_ERROR", "model server error");
            }
            if (status < 200 || status >= 300) {
                throw new ModelClientException("MODEL_HTTP_ERROR", "model HTTP error");
            }
            String responseBody =
                    readLimited(response.body(), properties.getMaxModelResponseBytes());
            return parse(responseBody);
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new ModelClientException("MODEL_TIMEOUT", "model request timed out");
        } catch (IOException ex) {
            throw new ModelClientException("MODEL_IO_ERROR", "model request failed");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ModelClientException("MODEL_INTERRUPTED", "model request interrupted");
        }
    }

    private ModelChatResponse parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode message = root.path("choices").path(0).path("message");
            String content =
                    message.path("content").isMissingNode() || message.path("content").isNull()
                            ? ""
                            : message.path("content").asText();
            List<ModelToolCall> toolCalls = new ArrayList<>();
            for (JsonNode node : message.path("tool_calls")) {
                toolCalls.add(
                        new ModelToolCall(
                                node.path("id").asText(),
                                node.path("type").asText("function"),
                                new ModelFunctionCall(
                                        node.path("function").path("name").asText(),
                                        node.path("function").path("arguments").asText("{}"))));
            }
            JsonNode usage = root.path("usage");
            ModelUsage modelUsage =
                    usage.isMissingNode()
                            ? null
                            : new ModelUsage(
                                    usage.path("prompt_tokens").isMissingNode()
                                            ? null
                                            : usage.path("prompt_tokens").asInt(),
                                    usage.path("completion_tokens").isMissingNode()
                                            ? null
                                            : usage.path("completion_tokens").asInt(),
                                    usage.path("total_tokens").isMissingNode()
                                            ? null
                                            : usage.path("total_tokens").asInt());
            return new ModelChatResponse(
                    content,
                    toolCalls,
                    modelUsage,
                    root.path("model").asText(properties.getModel()));
        } catch (RuntimeException | IOException ex) {
            throw new ModelClientException("MODEL_MALFORMED_JSON", "model returned malformed JSON");
        }
    }

    private URI chatUri() {
        String base = properties.getBaseUrl();
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        if (trimmed.endsWith("/chat/completions")) {
            return URI.create(trimmed);
        }
        return URI.create(trimmed + "/chat/completions");
    }

    private static String readLimited(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > limit) {
                throw new ModelClientException(
                        "MODEL_RESPONSE_TOO_LARGE", "model response too large");
            }
            out.write(buffer, 0, read);
        }
        return out.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void sleep() {
        try {
            Thread.sleep(150);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
