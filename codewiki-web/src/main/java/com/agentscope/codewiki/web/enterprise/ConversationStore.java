package com.agentscope.codewiki.web.enterprise;

import java.util.Optional;

public interface ConversationStore {
    ConversationExecution create(String requestId, String question);

    default ConversationExecution create(String requestId) {
        return create(requestId, "");
    }

    Optional<ConversationExecution> find(String conversationId);

    default Optional<ConversationExecution> findByRequestId(String requestId) {
        return Optional.empty();
    }

    default void cleanupExpired() {}
}
