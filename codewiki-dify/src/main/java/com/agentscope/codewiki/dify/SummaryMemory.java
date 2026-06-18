package com.agentscope.codewiki.dify;

import java.time.Instant;

/**
 * A compressed summary of conversation history that falls outside the recent-message window.
 *
 * <p>Stored and loaded via {@link MemoryProvider}.
 */
public class SummaryMemory {

    /** Conversation this summary belongs to. */
    private String conversationId;

    /** Compressed text summary (LLM-generated). */
    private String summary;

    /** How many original messages were compressed into this summary. */
    private int summarizedMessageCount;

    /** When this summary was created. */
    private Instant createdAt;

    /** Timestamp of the most recent message included in this summary. */
    private Instant lastMessageTime;

    public SummaryMemory() {}

    public SummaryMemory(
            String conversationId,
            String summary,
            int summarizedMessageCount,
            Instant lastMessageTime) {
        this.conversationId = conversationId;
        this.summary = summary;
        this.summarizedMessageCount = summarizedMessageCount;
        this.createdAt = Instant.now();
        this.lastMessageTime = lastMessageTime;
    }

    /** Rough token estimate for the summary text. */
    public int estimatedTokens() {
        return summary != null ? Math.max(1, summary.length() / 4) : 0;
    }

    // ── accessors ──

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public int getSummarizedMessageCount() {
        return summarizedMessageCount;
    }

    public void setSummarizedMessageCount(int summarizedMessageCount) {
        this.summarizedMessageCount = summarizedMessageCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(Instant lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    @Override
    public String toString() {
        return "SummaryMemory{covers="
                + summarizedMessageCount
                + " msgs, tokens~"
                + estimatedTokens()
                + "}";
    }
}
