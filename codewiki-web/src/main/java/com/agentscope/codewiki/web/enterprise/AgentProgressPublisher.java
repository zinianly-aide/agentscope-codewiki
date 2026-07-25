package com.agentscope.codewiki.web.enterprise;

import java.util.Map;

@FunctionalInterface
public interface AgentProgressPublisher {
    void publish(
            AgentEventType type,
            String stage,
            String message,
            AgentEventStatus status,
            int progress,
            Map<String, Object> metadata);
}
