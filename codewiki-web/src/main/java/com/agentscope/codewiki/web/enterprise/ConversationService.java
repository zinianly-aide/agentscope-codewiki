package com.agentscope.codewiki.web.enterprise;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConversationService {
    private final ConversationStore store;
    private final EnterpriseAgentService agentService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ConversationService(ConversationStore store, EnterpriseAgentService agentService) {
        this.store = store;
        this.agentService = agentService;
    }

    public ConversationExecution start(ChatRequest request) {
        ConversationExecution execution = store.create(request.requestId());
        execution.append(
                AgentEventType.REQUEST_ACCEPTED,
                "请求已接收",
                "已创建异步 Conversation",
                AgentEventStatus.COMPLETED,
                1,
                Map.of("executionMode", "pending"));
        executor.submit(() -> run(execution, request));
        return execution;
    }

    public Optional<ConversationExecution> find(String id) {
        return store.find(id);
    }

    private void run(ConversationExecution execution, ChatRequest request) {
        AgentProgressPublisher publisher =
                (type, stage, message, status, progress, metadata) ->
                        execution.append(
                                type, stage, message, status, progress, sanitize(metadata));
        try {
            AgentChatResult result = agentService.chat(request, publisher);
            execution.answer(result.answer());
            execution.append(
                    AgentEventType.AGENT_COMPLETED,
                    "处理完成",
                    "Agent 已完成",
                    AgentEventStatus.COMPLETED,
                    100,
                    Map.of("executionMode", result.executionMode().wireValue()));
            execution.status(ConversationStatus.COMPLETED);
        } catch (Exception ex) {
            execution.status(ConversationStatus.FAILED);
            execution.append(
                    AgentEventType.AGENT_FAILED,
                    "处理失败",
                    ex.getMessage() == null ? "Agent request failed" : ex.getMessage(),
                    AgentEventStatus.FAILED,
                    100,
                    Map.of());
        }
    }

    private static Map<String, Object> sanitize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        java.util.Map<String, Object> sanitized = new java.util.LinkedHashMap<>();
        metadata.forEach(
                (key, value) -> {
                    String lower = key.toLowerCase(java.util.Locale.ROOT);
                    if (!lower.contains("token")
                            && !lower.contains("authorization")
                            && !lower.contains("apikey")
                            && !lower.contains("api-key")
                            && !lower.contains("prompt")
                            && !lower.contains("skillcontent")
                            && !lower.contains("reasoning")) {
                        sanitized.put(key, value);
                    }
                });
        return Map.copyOf(sanitized);
    }
}
