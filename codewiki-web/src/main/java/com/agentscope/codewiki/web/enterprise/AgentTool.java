package com.agentscope.codewiki.web.enterprise;

import java.util.Map;

public interface AgentTool {
    String name();

    String description();

    ToolSource source();

    Map<String, Object> inputSchema();

    ToolResult<?> execute(Map<String, Object> arguments, ToolExecutionContext context);
}
