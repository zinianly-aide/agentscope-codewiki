package com.agentscope.codewiki.web.enterprise;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class AgentToolRegistry {
    private static final int MAX_ARGUMENT_BYTES = 64 * 1024;
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();
    private final ToolRetryExecutor retryExecutor;
    private final ToolMetricsRegistry metricsRegistry;
    private final ToolAuditService auditService;

    public AgentToolRegistry() {
        this(new ToolRetryExecutor(1, 0), new ToolMetricsRegistry(), new ToolAuditService());
    }

    public AgentToolRegistry(
            ToolRetryExecutor retryExecutor,
            ToolMetricsRegistry metricsRegistry,
            ToolAuditService auditService) {
        this.retryExecutor = retryExecutor;
        this.metricsRegistry = metricsRegistry;
        this.auditService = auditService;
    }

    public synchronized void register(AgentTool tool) {
        tools.put(tool.name(), tool);
    }

    public synchronized void refreshMcpTools(
            Collection<McpToolDescriptor> descriptors, McpClient client) {
        tools.entrySet().removeIf(entry -> entry.getValue().source() == ToolSource.MCP);
        for (McpToolDescriptor descriptor : descriptors) {
            register(new McpAgentTool(descriptor, client));
        }
    }

    public synchronized Optional<AgentTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public synchronized Collection<AgentTool> tools() {
        return java.util.List.copyOf(tools.values());
    }

    public Map<String, Object> metricsSnapshot() {
        return metricsRegistry.snapshot();
    }

    public java.util.List<ToolAuditRecord> recentAudits(int limit) {
        return auditService.recent(limit);
    }

    public ToolResult<?> execute(
            String name, Map<String, Object> arguments, ToolExecutionContext context) {
        AgentTool tool = find(name).orElse(null);
        if (tool == null) {
            ToolResult<?> result =
                    ToolResult.failure(
                            name, "tool-registry", 0, "TOOL_NOT_FOUND", "tool is not registered");
            metricsRegistry.record(name, false, 0);
            auditService.record(
                    context,
                    name,
                    "tool-registry",
                    OffsetDateTime.now(ZoneOffset.ofHours(9)),
                    0,
                    false,
                    result.errorCode());
            return result;
        }
        if (argumentSize(arguments) > MAX_ARGUMENT_BYTES) {
            ToolResult<?> result =
                    ToolResult.failure(
                            name,
                            tool.source().name(),
                            0,
                            "TOOL_ARGUMENTS_TOO_LARGE",
                            "tool arguments exceed 64KB");
            metricsRegistry.record(name, false, 0);
            auditService.record(
                    context,
                    name,
                    tool.source().name(),
                    OffsetDateTime.now(ZoneOffset.ofHours(9)),
                    0,
                    false,
                    result.errorCode());
            return result;
        }
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.ofHours(9));
        ToolResult<?> result =
                retryExecutor.execute(
                        tool, arguments, context, () -> tool.execute(arguments, context));
        metricsRegistry.record(name, result.success(), result.durationMs());
        auditService.record(
                context,
                name,
                tool.source().name(),
                start,
                result.durationMs(),
                result.success(),
                result.errorCode());
        return result;
    }

    private static int argumentSize(Map<String, Object> arguments) {
        return String.valueOf(arguments).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
}
