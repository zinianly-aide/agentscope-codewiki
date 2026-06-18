package com.agentscope.codewiki.dify;

import java.io.IOException;
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
        this.client = new DifyClient(baseUrl, apiKey);
        this.user = user;
        this.conversationId = conversationId;
    }

    /** For testing — inject a pre-configured client. */
    DifyChatModel(DifyClient client, String user, String conversationId) {
        this.client = client;
        this.user = user;
        this.conversationId = conversationId;
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
}
