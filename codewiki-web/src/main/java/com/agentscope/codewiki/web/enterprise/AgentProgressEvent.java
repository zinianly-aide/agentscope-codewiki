package com.agentscope.codewiki.web.enterprise;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentProgressEvent(
        long sequence,
        String eventId,
        String conversationId,
        String requestId,
        OffsetDateTime timestamp,
        AgentEventType type,
        String stage,
        String message,
        AgentEventStatus status,
        int progress,
        Map<String, Object> metadata) {}
