package com.agentscope.codewiki.dify;

import java.time.Instant;

/** A single message in a conversation. Immutable value object. */
public class MessageEntry {

    public enum Role {
        USER,
        ASSISTANT,
        SYSTEM
    }

    private final Role role;
    private final String content;
    private final Instant timestamp;

    public MessageEntry(Role role, String content) {
        this(role, content, Instant.now());
    }

    public MessageEntry(Role role, String content, Instant timestamp) {
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    /** Rough token estimate (4 chars ≈ 1 token for English). */
    public int estimatedTokens() {
        return Math.max(1, content.length() / 4);
    }

    @Override
    public String toString() {
        return "["
                + role
                + "] "
                + (content.length() > 60 ? content.substring(0, 60) + "..." : content);
    }
}
