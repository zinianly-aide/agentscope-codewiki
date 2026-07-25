package com.agentscope.codewiki.web.enterprise;

/** Internal service result with answer and actual execution mode. */
public record AgentChatResult(String answer, ExecutionMode executionMode) {}
