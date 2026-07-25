package com.agentscope.codewiki.web.enterprise;

import java.util.List;

public record ModelChatResponse(
        String content, List<ModelToolCall> toolCalls, ModelUsage usage, String model) {
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
