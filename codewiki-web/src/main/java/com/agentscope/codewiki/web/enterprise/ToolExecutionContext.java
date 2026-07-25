package com.agentscope.codewiki.web.enterprise;

public record ToolExecutionContext(
        String requestId, String conversationId, AgentProgressPublisher progressPublisher) {
    public ToolExecutionContext(String requestId) {
        this(requestId, "", null);
    }

    public ToolExecutionContext(String requestId, AgentProgressPublisher progressPublisher) {
        this(requestId, "", progressPublisher);
    }
}
