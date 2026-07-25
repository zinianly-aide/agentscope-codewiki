package com.agentscope.codewiki.web.enterprise;

import java.util.Map;

public interface McpClient {
    McpToolsResponse discoverTools();

    McpToolCallResponse<?> call(String toolName, Map<String, Object> arguments);

    boolean available();

    String status();
}
