package com.agentscope.codewiki.web.enterprise;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Optional;
import java.util.UUID;

public class InMemoryConversationStore implements ConversationStore {
    private final ConversationProperties properties;
    private final Cache<String, ConversationExecution> cache;
    private final Cache<String, String> requestIndex;

    public InMemoryConversationStore(ConversationProperties properties) {
        this.properties = properties;
        this.cache =
                Caffeine.newBuilder()
                        .maximumSize(properties.getMaxConversations())
                        .expireAfterAccess(properties.getExpireAfterAccess())
                        .build();
        this.requestIndex =
                Caffeine.newBuilder()
                        .maximumSize(properties.getMaxConversations())
                        .expireAfterAccess(properties.getExpireAfterAccess())
                        .build();
    }

    @Override
    public ConversationExecution create(String requestId, String question) {
        String id = "conv-" + UUID.randomUUID().toString().substring(0, 8);
        ConversationExecution execution =
                new ConversationExecution(
                        id, requestId, question, properties.getMaxEventsPerConversation());
        cache.put(id, execution);
        requestIndex.put(requestId, id);
        return execution;
    }

    @Override
    public Optional<ConversationExecution> find(String conversationId) {
        return Optional.ofNullable(cache.getIfPresent(conversationId));
    }

    @Override
    public Optional<ConversationExecution> findByRequestId(String requestId) {
        String conversationId = requestIndex.getIfPresent(requestId);
        if (conversationId == null) {
            return Optional.empty();
        }
        Optional<ConversationExecution> execution = find(conversationId);
        if (execution.isEmpty()) {
            requestIndex.invalidate(requestId);
        }
        return execution;
    }

    @Override
    public void cleanupExpired() {
        cache.cleanUp();
        requestIndex.cleanUp();
    }
}
