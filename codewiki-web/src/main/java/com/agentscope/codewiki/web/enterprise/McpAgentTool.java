package com.agentscope.codewiki.web.enterprise;

import java.util.Map;

public class McpAgentTool implements AgentTool {
    private final McpToolDescriptor descriptor;
    private final McpClient client;

    public McpAgentTool(McpToolDescriptor descriptor, McpClient client) {
        this.descriptor = descriptor;
        this.client = client;
    }

    @Override
    public String name() {
        return descriptor.name();
    }

    @Override
    public String description() {
        return descriptor.description();
    }

    @Override
    public ToolSource source() {
        return ToolSource.MCP;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return descriptor.inputSchema();
    }

    @Override
    public ToolResult<?> execute(Map<String, Object> arguments, ToolExecutionContext context) {
        long start = System.nanoTime();
        McpToolCallResponse<?> response = client.call(name(), arguments);
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        if (response.success()) {
            return ToolResult.success(name(), response.data(), response.source(), durationMs);
        }
        return ToolResult.failure(
                name(),
                response.source(),
                durationMs,
                response.errorCode(),
                response.errorMessage());
    }
}
