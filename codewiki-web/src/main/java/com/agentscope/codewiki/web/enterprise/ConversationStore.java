package com.agentscope.codewiki.web.enterprise;

import java.util.Optional;

public interface ConversationStore {
    ConversationExecution create(String requestId);

    Optional<ConversationExecution> find(String conversationId);
}
