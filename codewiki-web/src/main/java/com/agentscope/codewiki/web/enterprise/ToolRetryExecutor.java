package com.agentscope.codewiki.web.enterprise;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.function.Supplier;

public class ToolRetryExecutor {
    private final int maxAttempts;
    private final long retryIntervalMs;

    public ToolRetryExecutor(int maxAttempts, long retryIntervalMs) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryIntervalMs = Math.max(0, retryIntervalMs);
    }

    public ToolResult<?> execute(
            AgentTool tool,
            Map<String, Object> arguments,
            ToolExecutionContext context,
            Supplier<ToolResult<?>> supplier) {
        ToolResult<?> last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            last = supplier.get();
            if (last.success() || !retryable(last)) {
                return last;
            }
            if (attempt < maxAttempts) {
                emitRetry(context, tool, attempt + 1, last);
                sleep();
            }
        }
        return last;
    }

    private boolean retryable(ToolResult<?> result) {
        String code = result.errorCode() == null ? "" : result.errorCode();
        String message = result.errorMessage() == null ? "" : result.errorMessage();
        return code.contains("TIMEOUT")
                || code.contains("UNAVAILABLE")
                || code.contains("CONNECT")
                || message.contains(HttpTimeoutException.class.getSimpleName())
                || message.contains(SocketTimeoutException.class.getSimpleName())
                || message.contains(ConnectException.class.getSimpleName());
    }

    private void emitRetry(
            ToolExecutionContext context, AgentTool tool, int nextAttempt, ToolResult<?> last) {
        if (context != null && context.progressPublisher() != null) {
            context.progressPublisher()
                    .publish(
                            AgentEventType.TOOL_RETRY,
                            "Tool 重试",
                            "正在重试 " + tool.name(),
                            AgentEventStatus.RUNNING,
                            70,
                            Map.of(
                                    "toolName",
                                    tool.name(),
                                    "toolSource",
                                    tool.source().name(),
                                    "attempt",
                                    nextAttempt,
                                    "lastErrorCode",
                                    last.errorCode() == null ? "" : last.errorCode()));
        }
    }

    private void sleep() {
        try {
            Thread.sleep(retryIntervalMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
