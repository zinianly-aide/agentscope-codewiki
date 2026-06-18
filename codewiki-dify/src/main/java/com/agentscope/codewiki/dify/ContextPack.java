package com.agentscope.codewiki.dify;

import java.util.ArrayList;
import java.util.List;

/**
 * A self-contained context bundle assembled for a single LLM call.
 *
 * <p>Produced by {@link ContextManager#assembleContext}.
 *
 * <p>Contains:
 *
 * <ul>
 *   <li>Recent messages (within token budget)
 *   <li>Long-term summary (compressed older history)
 *   <li>Current query to be answered
 *   <li>Token budget metadata
 * </ul>
 */
public class ContextPack {

    private final String conversationId;
    private final String currentQuery;
    private final List<MessageEntry> recentMessages;
    private final SummaryMemory longTermSummary;
    private final int tokenBudget;
    private final int estimatedTokens;

    public ContextPack(
            String conversationId,
            String currentQuery,
            List<MessageEntry> recentMessages,
            SummaryMemory longTermSummary,
            int tokenBudget,
            int estimatedTokens) {
        this.conversationId = conversationId;
        this.currentQuery = currentQuery;
        this.recentMessages = new ArrayList<>(recentMessages);
        this.longTermSummary = longTermSummary;
        this.tokenBudget = tokenBudget;
        this.estimatedTokens = estimatedTokens;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getCurrentQuery() {
        return currentQuery;
    }

    public List<MessageEntry> getRecentMessages() {
        return recentMessages;
    }

    public SummaryMemory getLongTermSummary() {
        return longTermSummary;
    }

    public int getTokenBudget() {
        return tokenBudget;
    }

    public int getEstimatedTokens() {
        return estimatedTokens;
    }

    /** True if there is enough budget remaining for the expected response. */
    public boolean hasBudget(int responseTokens) {
        return estimatedTokens + responseTokens <= tokenBudget;
    }

    @Override
    public String toString() {
        return "ContextPack{conv="
                + conversationId
                + ", recent="
                + recentMessages.size()
                + " msgs, hasSummary="
                + (longTermSummary != null)
                + ", tokens="
                + estimatedTokens
                + "/"
                + tokenBudget
                + "}";
    }
}
