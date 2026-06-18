package com.agentscope.codewiki.dify;

import java.util.List;
import java.util.Optional;

/**
 * Abstraction for conversation memory storage.
 *
 * <p>Implementations may use in-memory maps, local files, or remote databases. This interface
 * intentionally avoids tying to any specific vector or embedding system.
 */
public interface MemoryProvider {

    /** Persist a message entry. */
    void saveMessage(String conversationId, MessageEntry entry);

    /**
     * Retrieve the most recent messages for a conversation.
     *
     * @param conversationId conversation identifier
     * @param maxMessages maximum number of messages to return
     * @return ordered list (oldest first) of up to maxMessages entries
     */
    List<MessageEntry> getRecentMessages(String conversationId, int maxMessages);

    /** Save a long-term summary for a conversation. Replaces any existing summary. */
    void saveSummary(String conversationId, SummaryMemory summary);

    /** Load the long-term summary for a conversation. */
    Optional<SummaryMemory> loadSummary(String conversationId);

    /** Total message count for a conversation. */
    int messageCount(String conversationId);

    /** Delete all data for a conversation. */
    void deleteConversation(String conversationId);
}
