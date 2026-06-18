package com.agentscope.codewiki.dify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of {@link MemoryProvider}.
 *
 * <p>Uses {@link LinkedHashMap} for O(1) access per conversation. Designed for single-JVM use;
 * replace with a persistent implementation for production.
 */
public class ConversationStore implements MemoryProvider {

    /** Max messages to keep per conversation (LRU eviction if exceeded). */
    private static final int DEFAULT_MAX_MESSAGES = 10_000;

    private final Map<String, List<MessageEntry>> messages = new LinkedHashMap<>();
    private final Map<String, SummaryMemory> summaries = new LinkedHashMap<>();

    // ── Message operations ──

    @Override
    public void saveMessage(String conversationId, MessageEntry entry) {
        messages.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(entry);
    }

    @Override
    public List<MessageEntry> getRecentMessages(String conversationId, int maxMessages) {
        List<MessageEntry> all = messages.getOrDefault(conversationId, List.of());
        int from = Math.max(0, all.size() - maxMessages);
        return Collections.unmodifiableList(all.subList(from, all.size()));
    }

    @Override
    public int messageCount(String conversationId) {
        return messages.getOrDefault(conversationId, List.of()).size();
    }

    // ── Summary operations ──

    @Override
    public void saveSummary(String conversationId, SummaryMemory summary) {
        summaries.put(conversationId, summary);
    }

    @Override
    public Optional<SummaryMemory> loadSummary(String conversationId) {
        return Optional.ofNullable(summaries.get(conversationId));
    }

    // ── Lifecycle ──

    @Override
    public void deleteConversation(String conversationId) {
        messages.remove(conversationId);
        summaries.remove(conversationId);
    }

    /** Number of stored conversations. */
    public int conversationCount() {
        return messages.size();
    }
}
