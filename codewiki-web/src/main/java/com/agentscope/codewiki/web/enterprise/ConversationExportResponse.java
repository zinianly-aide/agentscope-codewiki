package com.agentscope.codewiki.web.enterprise;

import java.util.List;

public record ConversationExportResponse(
        String conversationId,
        String requestId,
        String question,
        String answer,
        List<AgentProgressEvent> events) {}
