package com.agentscope.codewiki.web.enterprise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class EnterpriseSessionStoreTest {
    @Test
    void sessionExpiresAfterAccess() {
        MutableTicker ticker = new MutableTicker();
        EnterpriseAgentProperties properties = new EnterpriseAgentProperties();
        properties.setSessionExpireAfterAccess(Duration.ofMillis(10));
        EnterpriseSessionStore store = new EnterpriseSessionStore(properties, ticker);
        EnterpriseSession first = store.get("u", "s");
        ticker.advance(11, TimeUnit.MILLISECONDS);
        EnterpriseSession second = store.get("u", "s");
        assertNotSame(first, second);
    }

    @Test
    void sessionCapacityLimitEvictsEntries() {
        EnterpriseAgentProperties properties = new EnterpriseAgentProperties();
        properties.setMaxSessions(1);
        EnterpriseSessionStore store = new EnterpriseSessionStore(properties, new MutableTicker());
        store.get("u1", "s1");
        store.get("u2", "s2");
        assertEquals(1, store.estimatedSize());
    }

    @Test
    void sessionKeepsRecentTurnsOnly() {
        EnterpriseAgentProperties properties = new EnterpriseAgentProperties();
        properties.setMaxTurnsPerSession(2);
        EnterpriseSessionStore store = new EnterpriseSessionStore(properties, new MutableTicker());
        EnterpriseSession session = store.get("u", "s");
        session.add("u1", "a1");
        session.add("u2", "a2");
        session.add("u3", "a3");
        assertEquals(2, session.snapshot().size());
        assertEquals("u2", session.snapshot().get(0).userMessage());
    }

    private static final class MutableTicker implements Ticker {
        private long nanos;

        @Override
        public long read() {
            return nanos;
        }

        void advance(long amount, TimeUnit unit) {
            nanos += unit.toNanos(amount);
        }
    }
}
