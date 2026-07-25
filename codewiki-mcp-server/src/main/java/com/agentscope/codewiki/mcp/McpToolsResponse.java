package com.agentscope.codewiki.mcp;

import java.util.List;

public record McpToolsResponse(String serverName, List<McpToolDescriptor> tools) {}
