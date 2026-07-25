package com.agentscope.codewiki.web.enterprise;

public record ToolExecutionContext(String requestId, AgentProgressPublisher progressPublisher) {
    public ToolExecutionContext(String requestId) {
        this(requestId, null);
    }
}
