package com.agentscope.codewiki.web.enterprise;

import java.time.OffsetDateTime;

public record ToolAuditRecord(
        String requestId,
        String conversationId,
        String tool,
        String source,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        long durationMs,
        String status,
        String errorType) {}
