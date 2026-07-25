package com.agentscope.codewiki.dify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Response from Dify POST /v1/chat-messages. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DifyChatResponse {

    /** Event name returned by Dify. */
    private String event;

    /** Unique message ID. */
    @JsonProperty("message_id")
    private String messageId;

    /** Conversation ID (persist for multi-turn chat). */
    @JsonProperty("conversation_id")
    private String conversationId;

    /** LLM answer text. */
    private String answer;

    /** Unix timestamp in seconds. */
    @JsonProperty("created_at")
    private long createdAt;

    // ── accessors ──

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "DifyChatResponse{event="
                + event
                + ", messageId="
                + messageId
                + ", conversationId="
                + conversationId
                + ", answer="
                + (answer != null
                        ? answer.substring(0, Math.min(80, answer.length())) + "..."
                        : "null")
                + "}";
    }
}
