package com.agentscope.codewiki.web.enterprise;

/** Unified result envelope for read-only enterprise tools. */
public record ToolResult<T>(
        boolean success,
        String toolName,
        T data,
        String source,
        long durationMs,
        String errorCode,
        String errorMessage) {
    public static <T> ToolResult<T> success(
            String toolName, T data, String source, long durationMs) {
        return new ToolResult<>(true, toolName, data, source, durationMs, null, null);
    }

    public static <T> ToolResult<T> failure(
            String toolName,
            String source,
            long durationMs,
            String errorCode,
            String errorMessage) {
        return new ToolResult<>(false, toolName, null, source, durationMs, errorCode, errorMessage);
    }
}
