package com.agentscope.codewiki.dify;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * High-level chat model that wraps {@link DifyClient}.
 *
 * <p>Manages user identity and optional conversation persistence. This is the primary API consumers
 * should use.
 *
 * <pre>{@code
 * DifyChatModel model = new DifyChatModel("https://api.dify.ai", "app-xxx", "user-123");
 * String answer = model.chat("What does this code do?");
 * }</pre>
 */
public class DifyChatModel {

    private final DifyClient client;
    private final String user;
    private final DifyResponseMode responseMode;
    private String conversationId;

    /**
     * Create a chat model.
     *
     * @param baseUrl Dify instance base URL
     * @param apiKey Dify app API key
     * @param user end-user identifier
     */
    public DifyChatModel(String baseUrl, String apiKey, String user) {
        this(baseUrl, apiKey, user, null);
    }

    /** Create a chat model with an existing conversation. */
    public DifyChatModel(String baseUrl, String apiKey, String user, String conversationId) {
        this(baseUrl, apiKey, user, conversationId, Duration.ofSeconds(60));
    }

    /** Create a chat model with an existing conversation and custom timeout. */
    public DifyChatModel(
            String baseUrl, String apiKey, String user, String conversationId, Duration timeout) {
        this(baseUrl, apiKey, user, conversationId, timeout, DifyResponseMode.BLOCKING);
    }

    /** Create a chat model with an existing conversation, custom timeout, and response mode. */
    public DifyChatModel(
            String baseUrl,
            String apiKey,
            String user,
            String conversationId,
            Duration timeout,
            DifyResponseMode responseMode) {
        this.client = new DifyClient(baseUrl, apiKey, timeout);
        this.user = user;
        this.conversationId = conversationId;
        this.responseMode = responseMode == null ? DifyResponseMode.BLOCKING : responseMode;
    }

    /** For testing — inject a pre-configured client. */
    DifyChatModel(DifyClient client, String user, String conversationId) {
        this(client, user, conversationId, DifyResponseMode.BLOCKING);
    }

    /** For testing — inject a pre-configured client and response mode. */
    DifyChatModel(
            DifyClient client, String user, String conversationId, DifyResponseMode responseMode) {
        this.client = client;
        this.user = user;
        this.conversationId = conversationId;
        this.responseMode = responseMode == null ? DifyResponseMode.BLOCKING : responseMode;
    }

    /**
     * Send a simple text query and return the answer.
     *
     * @param query the user query
     * @return the AI answer text
     * @throws DifyException on API errors
     * @throws IOException on network errors
     */
    public String chat(String query) throws IOException {
        return chat(query, Map.of()).getAnswer();
    }

    /**
     * Send a query with input variables.
     *
     * @param query the user query
     * @param inputs Dify app input variables
     * @return the full response (answer + conversation metadata)
     * @throws DifyException on API errors
     * @throws IOException on network errors
     */
    public DifyChatResponse chat(String query, Map<String, Object> inputs) throws IOException {
        DifyChatRequest request = new DifyChatRequest(query, user);
        request.setInputs(inputs);
        request.setResponseMode(responseMode.wireValue());
        if (conversationId != null && !conversationId.isBlank()) {
            request.setConversationId(conversationId);
        }

        DifyChatResponse response = client.chat(request);

        // Auto-track conversation for multi-turn
        if (response.getConversationId() != null) {
            this.conversationId = response.getConversationId();
        }

        return response;
    }

    /** Get the current conversation ID (null before first message). */
    public String getConversationId() {
        return conversationId;
    }

    /** Manually set the conversation ID to resume a previous conversation. */
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getUser() {
        return user;
    }

    public DifyResponseMode getResponseMode() {
        return responseMode;
    }
}
