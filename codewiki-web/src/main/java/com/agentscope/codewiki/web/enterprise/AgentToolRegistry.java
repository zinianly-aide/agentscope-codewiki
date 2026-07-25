package com.agentscope.codewiki.web.enterprise;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class AgentToolRegistry {
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public synchronized void register(AgentTool tool) {
        tools.put(tool.name(), tool);
    }

    public synchronized Optional<AgentTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public synchronized Collection<AgentTool> tools() {
        return java.util.List.copyOf(tools.values());
    }

    public ToolResult<?> execute(
            String name, Map<String, Object> arguments, ToolExecutionContext context) {
        return find(name)
                .map(tool -> tool.execute(arguments, context))
                .orElseGet(
                        () ->
                                ToolResult.failure(
                                        name,
                                        "tool-registry",
                                        0,
                                        "TOOL_NOT_FOUND",
                                        "tool is not registered"));
    }
}
