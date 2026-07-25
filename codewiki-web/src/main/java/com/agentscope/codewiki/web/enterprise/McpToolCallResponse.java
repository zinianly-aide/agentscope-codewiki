package com.agentscope.codewiki.web.enterprise;

public record McpToolCallResponse<T>(
        boolean success,
        String toolName,
        T data,
        String source,
        String errorCode,
        String errorMessage) {}
