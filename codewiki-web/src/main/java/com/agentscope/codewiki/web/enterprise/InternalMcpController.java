package com.agentscope.codewiki.web.enterprise;

import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/mcp")
public class InternalMcpController {
    private final McpClient mcpClient;
    private final AgentToolRegistry registry;

    public InternalMcpController(McpClient mcpClient, AgentToolRegistry registry) {
        this.mcpClient = mcpClient;
        this.registry = registry;
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        McpToolsResponse response = mcpClient.discoverTools();
        registry.refreshMcpTools(response.tools(), mcpClient);
        return Map.of(
                "success", true,
                "serverName", response.serverName(),
                "toolCount", response.tools().size(),
                "available", mcpClient.available(),
                "status", mcpClient.status());
    }
}
