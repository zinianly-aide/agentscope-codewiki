package com.agentscope.codewiki.web.enterprise;

import java.util.ArrayList;
import java.util.List;

/** Bounded per-user/session conversation memory. */
public class EnterpriseSession {
    private final int maxTurns;
    private final List<SessionTurn> turns = new ArrayList<>();

    public EnterpriseSession(int maxTurns) {
        this.maxTurns = Math.max(1, maxTurns);
    }

    public synchronized List<SessionTurn> snapshot() {
        return List.copyOf(turns);
    }

    public synchronized void add(String userMessage, String assistantAnswer) {
        turns.add(new SessionTurn(compact(userMessage), compact(assistantAnswer)));
        while (turns.size() > maxTurns) {
            turns.remove(0);
        }
    }

    public synchronized int size() {
        return turns.size();
    }

    private static String compact(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\s+", " ").trim();
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }
}
