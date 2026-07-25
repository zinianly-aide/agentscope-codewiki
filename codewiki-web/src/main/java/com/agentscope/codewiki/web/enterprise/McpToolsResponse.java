package com.agentscope.codewiki.web.enterprise;

import java.util.List;

public record McpToolsResponse(String serverName, List<McpToolDescriptor> tools) {}
