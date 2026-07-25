package com.agentscope.codewiki.web.enterprise;

/** One compact chat turn stored in the bounded in-memory session. */
public record SessionTurn(String userMessage, String assistantAnswer) {}
