package com.agentscope.codewiki.web.enterprise;

import org.springframework.scheduling.annotation.Scheduled;

public class ConversationCleanupService {
    private final ConversationStore store;

    public ConversationCleanupService(ConversationStore store) {
        this.store = store;
    }

    @Scheduled(fixedRateString = "PT1H")
    public void cleanupExpiredConversations() {
        store.cleanupExpired();
    }
}
