package com.agentscope.codewiki.web.enterprise;

import java.util.List;

public record ConversationSnapshotResponse(
        String conversationId,
        String requestId,
        ConversationStatus status,
        String answer,
        List<AgentProgressEvent> events) {}
