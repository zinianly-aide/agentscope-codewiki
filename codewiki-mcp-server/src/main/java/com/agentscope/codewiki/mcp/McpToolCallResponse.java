package com.agentscope.codewiki.mcp;

public record McpToolCallResponse<T>(
        boolean success,
        String toolName,
        T data,
        String source,
        String errorCode,
        String errorMessage) {
    public static <T> McpToolCallResponse<T> success(String toolName, T data) {
        return new McpToolCallResponse<>(true, toolName, data, "mcp:codewiki-demo-mcp", null, null);
    }

    public static <T> McpToolCallResponse<T> failure(
            String toolName, String errorCode, String errorMessage) {
        return new McpToolCallResponse<>(
                false, toolName, null, "mcp:codewiki-demo-mcp", errorCode, errorMessage);
    }
}
