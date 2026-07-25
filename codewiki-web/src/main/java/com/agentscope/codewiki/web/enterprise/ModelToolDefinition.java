package com.agentscope.codewiki.web.enterprise;

import java.util.Map;

public record ModelToolDefinition(String type, Function function) {
    public static ModelToolDefinition fromTool(AgentTool tool) {
        return new ModelToolDefinition(
                "function", new Function(tool.name(), tool.description(), tool.inputSchema()));
    }

    public record Function(String name, String description, Map<String, Object> parameters) {}
}
