package com.agentscope.codewiki.dify;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates conversation context assembly and memory lifecycle.
 *
 * <p>Core responsibilities:
 *
 * <ul>
 *   <li>Assemble a {@link ContextPack} for each LLM call (recent messages + long-term summary,
 *       respecting token budget)
 *   <li>Save user/assistant messages after each turn
 *   <li>Trigger summarization when message count exceeds a threshold
 * </ul>
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * ContextManager cm = new ContextManager(store, 4096);
 * ContextPack pack = cm.assembleContext("conv-1", "What does this code do?", 1000);
 * // ... call LLM with pack ...
 * cm.saveTurn("conv-1", "What does this code do?", "It calculates taxes.", 50);
 * }</pre>
 */
public class ContextManager {

    /** Rough tokens-per-character ratio for English text. */
    private static final double TOKEN_RATIO = 0.25;

    /** Overhead tokens for system prompt and formatting. */
    private static final int SYSTEM_OVERHEAD = 200;

    /** Default message-count threshold before triggering summarization. */
    private static final int DEFAULT_SUMMARIZE_THRESHOLD = 20;

    /** Max messages to include in recent context. */
    private static final int DEFAULT_MAX_RECENT = 10;

    private final MemoryProvider store;
    private final int defaultTokenBudget;
    private final int summarizeThreshold;
    private final int maxRecentMessages;

    /**
     * Create a ContextManager with defaults.
     *
     * @param store memory provider backend
     * @param defaultTokenBudget default token budget (e.g., 4096 for GPT-4, 8192 for larger models)
     */
    public ContextManager(MemoryProvider store, int defaultTokenBudget) {
        this(store, defaultTokenBudget, DEFAULT_SUMMARIZE_THRESHOLD, DEFAULT_MAX_RECENT);
    }

    public ContextManager(
            MemoryProvider store,
            int defaultTokenBudget,
            int summarizeThreshold,
            int maxRecentMessages) {
        this.store = store;
        this.defaultTokenBudget = defaultTokenBudget;
        this.summarizeThreshold = summarizeThreshold;
        this.maxRecentMessages = maxRecentMessages;
    }

    /**
     * Assemble context for an LLM call.
     *
     * @param conversationId target conversation
     * @param currentQuery the user's current message
     * @return a ContextPack ready for LLM consumption
     */
    public ContextPack assembleContext(String conversationId, String currentQuery) {
        return assembleContext(conversationId, currentQuery, defaultTokenBudget);
    }

    /** Assemble context with a specific token budget. */
    public ContextPack assembleContext(
            String conversationId, String currentQuery, int tokenBudget) {
        int availableBudget = tokenBudget - SYSTEM_OVERHEAD;

        // Load long-term summary
        Optional<SummaryMemory> summaryOpt = store.loadSummary(conversationId);
        SummaryMemory summary = summaryOpt.orElse(null);
        int summaryTokens = summary != null ? summary.estimatedTokens() : 0;

        // Load recent messages, trimming to stay within budget
        List<MessageEntry> allRecent = store.getRecentMessages(conversationId, maxRecentMessages);
        List<MessageEntry> included = new ArrayList<>();
        int msgTokens = 0;
        int queryTokens = estimateTokens(currentQuery);

        // Budget allocation: summary + recent messages + current query must fit
        int msgBudget = availableBudget - summaryTokens - queryTokens;

        for (int i = allRecent.size() - 1; i >= 0 && msgBudget > 0; i--) {
            MessageEntry msg = allRecent.get(i);
            int mt = msg.estimatedTokens();
            if (mt <= msgBudget) {
                included.add(0, msg); // prepend to maintain order
                msgBudget -= mt;
                msgTokens += mt;
            } else {
                break; // can't fit more
            }
        }

        int totalEstimate = SYSTEM_OVERHEAD + summaryTokens + msgTokens + queryTokens;

        return new ContextPack(
                conversationId, currentQuery, included, summary, tokenBudget, totalEstimate);
    }

    /**
     * Save a user-assistant turn after the LLM response.
     *
     * @param conversationId target conversation
     * @param userQuery the user message
     * @param assistantReply the LLM response
     * @param responseTokens estimated tokens in the response (for budget tracking)
     */
    public void saveTurn(
            String conversationId, String userQuery, String assistantReply, int responseTokens) {
        Instant now = Instant.now();
        store.saveMessage(conversationId, new MessageEntry(MessageEntry.Role.USER, userQuery, now));
        store.saveMessage(
                conversationId, new MessageEntry(MessageEntry.Role.ASSISTANT, assistantReply, now));

        // Check if summarization is needed
        if (store.messageCount(conversationId) >= summarizeThreshold) {
            summarize(conversationId);
        }
    }

    /** Manually trigger summarization for a conversation. */
    public void summarize(String conversationId) {
        int total = store.messageCount(conversationId);
        if (total <= maxRecentMessages) return; // nothing to summarize

        // Take older messages (excluding the last maxRecentMessages)
        List<MessageEntry> all = store.getRecentMessages(conversationId, total);
        int keepRecent = Math.min(maxRecentMessages, total);
        List<MessageEntry> toSummarize = all.subList(0, total - keepRecent);

        if (toSummarize.isEmpty()) return;

        // Build a placeholder summary (real implementation would call LLM)
        StringBuilder sb = new StringBuilder();
        sb.append("Conversation summary (").append(toSummarize.size()).append(" messages): ");
        int chars = 0;
        for (MessageEntry m : toSummarize) {
            String snippet = m.getContent().substring(0, Math.min(80, m.getContent().length()));
            sb.append(snippet).append(" | ");
            chars += snippet.length();
            if (chars > 500) break; // cap summary length
        }

        SummaryMemory summary =
                new SummaryMemory(
                        conversationId,
                        sb.toString(),
                        toSummarize.size(),
                        toSummarize.get(toSummarize.size() - 1).getTimestamp());

        store.saveSummary(conversationId, summary);
    }

    // ── token estimation ──

    /** Estimate tokens for a text string. */
    public static int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return Math.max(1, (int) (text.length() * TOKEN_RATIO));
    }

    // ── delegates ──

    public MemoryProvider getStore() {
        return store;
    }

    public int getDefaultTokenBudget() {
        return defaultTokenBudget;
    }
}
