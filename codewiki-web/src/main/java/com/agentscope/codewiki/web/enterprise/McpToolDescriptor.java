package com.agentscope.codewiki.web.enterprise;

import java.util.Map;

public record McpToolDescriptor(String name, String description, Map<String, Object> inputSchema) {}
