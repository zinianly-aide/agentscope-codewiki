package com.agentscope.codewiki.web.enterprise;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "enterprise.conversations")
public class ConversationProperties {
    private long maxConversations = 1000;
    private Duration expireAfterAccess = Duration.ofMinutes(30);
    private int maxEventsPerConversation = 200;

    public long getMaxConversations() {
        return maxConversations;
    }

    public void setMaxConversations(long maxConversations) {
        this.maxConversations = maxConversations;
    }

    public Duration getExpireAfterAccess() {
        return expireAfterAccess;
    }

    public void setExpireAfterAccess(Duration expireAfterAccess) {
        this.expireAfterAccess = expireAfterAccess;
    }

    public int getMaxEventsPerConversation() {
        return maxEventsPerConversation;
    }

    public void setMaxEventsPerConversation(int maxEventsPerConversation) {
        this.maxEventsPerConversation = maxEventsPerConversation;
    }
}
