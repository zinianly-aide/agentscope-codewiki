package com.agentscope.codewiki.web.enterprise;

import java.util.Map;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class ConversationService {
    private final ConversationStore store;
    private final EnterpriseAgentService agentService;
    private final ThreadPoolTaskExecutor executor;

    public ConversationService(ConversationStore store, EnterpriseAgentService agentService) {
        this(store, agentService, defaultExecutor());
    }

    public ConversationService(
            ConversationStore store,
            EnterpriseAgentService agentService,
            ThreadPoolTaskExecutor executor) {
        this.store = store;
        this.agentService = agentService;
        this.executor = executor;
    }

    private static ThreadPoolTaskExecutor defaultExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.initialize();
        return executor;
    }

    public ConversationExecution start(ChatRequest request) {
        ConversationExecution execution = store.create(request.requestId(), request.message());
        execution.append(
                AgentEventType.REQUEST_ACCEPTED,
                "请求已接收",
                "已创建异步 Conversation",
                AgentEventStatus.COMPLETED,
                1,
                Map.of("executionMode", "pending"));
        Map<String, String> parentMdc =
                Map.of(
                        "requestId", request.requestId(),
                        "userId", request.userId(),
                        "sessionId", request.sessionId());
        try {
            executor.execute(
                    () -> {
                        MDC.setContextMap(parentMdc);
                        try {
                            run(execution, request);
                        } finally {
                            MDC.clear();
                        }
                    });
        } catch (TaskRejectedException ex) {
            execution.status(ConversationStatus.FAILED);
            execution.append(
                    AgentEventType.AGENT_FAILED,
                    "队列已满",
                    "异步执行队列已满，请稍后重试",
                    AgentEventStatus.FAILED,
                    100,
                    Map.of("errorCode", "ASYNC_QUEUE_FULL"));
        }
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
                            && !lower.contains("reasoning")
                            && !lower.contains("chainofthought")) {
                        sanitized.put(key, value);
                    }
                });
        return Map.copyOf(sanitized);
    }
}
