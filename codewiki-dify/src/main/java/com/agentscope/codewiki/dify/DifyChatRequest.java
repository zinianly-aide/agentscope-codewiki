package com.agentscope.codewiki.dify;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;

/**
 * Request body for Dify POST /v1/chat-messages.
 *
 * @see <a href="https://docs.dify.ai/api-reference/chat/post-chat-messages">Dify Chat API</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DifyChatRequest {

    /** User query text. */
    private String query;

    /** Input variables for the Dify app (can be empty). */
    private Map<String, Object> inputs = new HashMap<>();

    /** Dify response mode: "blocking" or "streaming". */
    @JsonProperty("response_mode")
    private String responseMode = DifyResponseMode.BLOCKING.wireValue();

    /** End-user identifier (required by Dify). */
    private String user;

    /** Conversation ID for continuing an existing conversation (optional). */
    @JsonProperty("conversation_id")
    private String conversationId;

    public DifyChatRequest() {}

    public DifyChatRequest(String query, String user) {
        this.query = query;
        this.user = user;
    }

    // ── accessors ──

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, Object> inputs) {
        this.inputs = inputs;
    }

    public String getResponseMode() {
        return responseMode;
    }

    public void setResponseMode(String responseMode) {
        this.responseMode = responseMode;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
}
