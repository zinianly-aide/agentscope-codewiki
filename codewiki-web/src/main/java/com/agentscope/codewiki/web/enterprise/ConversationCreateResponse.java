package com.agentscope.codewiki.web.enterprise;

public record ConversationCreateResponse(
        String conversationId, String requestId, ConversationStatus status, String eventsUrl) {}
