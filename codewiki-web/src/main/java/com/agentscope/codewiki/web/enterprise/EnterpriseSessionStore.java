package com.agentscope.codewiki.web.enterprise;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.util.Objects;

/** Caffeine-backed in-memory session lifecycle manager. */
public class EnterpriseSessionStore {
    private static final int MAX_ID_LENGTH = 128;
    private final Cache<String, EnterpriseSession> cache;
    private final int maxTurnsPerSession;

    public EnterpriseSessionStore(EnterpriseAgentProperties properties) {
        this(properties, Ticker.systemTicker());
    }

    EnterpriseSessionStore(EnterpriseAgentProperties properties, Ticker ticker) {
        this.maxTurnsPerSession = properties.getMaxTurnsPerSession();
        this.cache =
                Caffeine.newBuilder()
                        .maximumSize(properties.getMaxSessions())
                        .expireAfterAccess(properties.getSessionExpireAfterAccess())
                        .ticker(ticker)
                        .build();
    }

    public EnterpriseSession get(String userId, String sessionId) {
        validateId("userId", userId);
        validateId("sessionId", sessionId);
        String key = userId + ":" + sessionId;
        return cache.get(key, ignored -> new EnterpriseSession(maxTurnsPerSession));
    }

    public long estimatedSize() {
        cache.cleanUp();
        return cache.estimatedSize();
    }

    private static void validateId(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new EnterpriseAgentException(field + " must not be blank");
        }
        if (value.length() > MAX_ID_LENGTH) {
            throw new EnterpriseAgentException(field + " is too long");
        }
        Objects.requireNonNull(field);
    }
}
