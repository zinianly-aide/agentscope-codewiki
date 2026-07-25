package com.agentscope.codewiki.web.enterprise;

/** REST response for the enterprise single-agent chat API. */
public record ChatResponse(String requestId, String answer, String executionMode) {}
