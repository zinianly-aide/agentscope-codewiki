package com.agentscope.codewiki.web.enterprise;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelMessage(
        String role,
        String content,
        String tool_call_id,
        String name,
        List<ModelToolCall> tool_calls) {
    public static ModelMessage system(String content) {
        return new ModelMessage("system", content, null, null, null);
    }

    public static ModelMessage user(String content) {
        return new ModelMessage("user", content, null, null, null);
    }

    public static ModelMessage assistant(String content) {
        return new ModelMessage("assistant", content, null, null, null);
    }

    public static ModelMessage assistantToolCalls(List<ModelToolCall> toolCalls) {
        return new ModelMessage("assistant", null, null, null, toolCalls);
    }

    public static ModelMessage tool(String toolCallId, String name, String content) {
        return new ModelMessage("tool", content, toolCallId, name, null);
    }
}
