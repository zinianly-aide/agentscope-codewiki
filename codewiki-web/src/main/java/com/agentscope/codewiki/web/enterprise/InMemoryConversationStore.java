package com.agentscope.codewiki.web.enterprise;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Optional;
import java.util.UUID;

public class InMemoryConversationStore implements ConversationStore {
    private final ConversationProperties properties;
    private final Cache<String, ConversationExecution> cache;

    public InMemoryConversationStore(ConversationProperties properties) {
        this.properties = properties;
        this.cache =
                Caffeine.newBuilder()
                        .maximumSize(properties.getMaxConversations())
                        .expireAfterAccess(properties.getExpireAfterAccess())
                        .build();
    }

    @Override
    public ConversationExecution create(String requestId) {
        String id = "conv-" + UUID.randomUUID().toString().substring(0, 8);
        ConversationExecution execution =
                new ConversationExecution(id, requestId, properties.getMaxEventsPerConversation());
        cache.put(id, execution);
        return execution;
    }

    @Override
    public Optional<ConversationExecution> find(String conversationId) {
        return Optional.ofNullable(cache.getIfPresent(conversationId));
    }
}
