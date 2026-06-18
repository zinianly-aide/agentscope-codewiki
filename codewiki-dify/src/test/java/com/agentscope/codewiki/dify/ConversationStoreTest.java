package com.agentscope.codewiki.dify;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConversationStoreTest {

    private ConversationStore store;

    @BeforeEach
    void setUp() {
        store = new ConversationStore();
    }

    @Test
    void shouldSaveAndRetrieveMessages() {
        store.saveMessage("conv-1", new MessageEntry(MessageEntry.Role.USER, "Hello"));
        store.saveMessage("conv-1", new MessageEntry(MessageEntry.Role.ASSISTANT, "Hi there!"));

        List<MessageEntry> recent = store.getRecentMessages("conv-1", 10);
        assertEquals(2, recent.size());
        assertEquals("Hello", recent.get(0).getContent());
        assertEquals("Hi there!", recent.get(1).getContent());
    }

    @Test
    void shouldReturnOnlyMostRecent() {
        for (int i = 0; i < 10; i++) {
            store.saveMessage("conv-1", new MessageEntry(MessageEntry.Role.USER, "msg-" + i));
        }

        List<MessageEntry> recent = store.getRecentMessages("conv-1", 3);
        assertEquals(3, recent.size());
        assertEquals("msg-7", recent.get(0).getContent());
        assertEquals("msg-8", recent.get(1).getContent());
        assertEquals("msg-9", recent.get(2).getContent());
    }

    @Test
    void shouldReturnEmptyForUnknownConversation() {
        List<MessageEntry> recent = store.getRecentMessages("nonexistent", 10);
        assertTrue(recent.isEmpty());
    }

    @Test
    void shouldCountMessages() {
        store.saveMessage("conv-1", new MessageEntry(MessageEntry.Role.USER, "a"));
        store.saveMessage("conv-1", new MessageEntry(MessageEntry.Role.USER, "b"));
        store.saveMessage("conv-2", new MessageEntry(MessageEntry.Role.USER, "c"));

        assertEquals(2, store.messageCount("conv-1"));
        assertEquals(1, store.messageCount("conv-2"));
        assertEquals(0, store.messageCount("conv-3"));
    }

    @Test
    void shouldSaveAndLoadSummary() {
        SummaryMemory summary =
                new SummaryMemory(
                        "conv-1",
                        "User asked about login flow, responded with OAuth steps.",
                        4,
                        java.time.Instant.now());

        store.saveSummary("conv-1", summary);

        assertTrue(store.loadSummary("conv-1").isPresent());
        assertEquals("conv-1", store.loadSummary("conv-1").get().getConversationId());
        assertTrue(store.loadSummary("conv-1").get().getSummary().contains("OAuth"));
    }

    @Test
    void shouldReturnEmptySummaryForUnknown() {
        assertTrue(store.loadSummary("nonexistent").isEmpty());
    }

    @Test
    void shouldDeleteConversation() {
        store.saveMessage("conv-1", new MessageEntry(MessageEntry.Role.USER, "hi"));
        store.saveSummary("conv-1", new SummaryMemory("conv-1", "...", 1, java.time.Instant.now()));

        store.deleteConversation("conv-1");

        assertEquals(0, store.messageCount("conv-1"));
        assertTrue(store.loadSummary("conv-1").isEmpty());
    }

    @Test
    void shouldTrackMultipleConversations() {
        store.saveMessage("a", new MessageEntry(MessageEntry.Role.USER, "a1"));
        store.saveMessage("b", new MessageEntry(MessageEntry.Role.USER, "b1"));
        store.saveMessage("a", new MessageEntry(MessageEntry.Role.USER, "a2"));

        assertEquals(2, store.conversationCount());
        assertEquals(2, store.messageCount("a"));
        assertEquals(1, store.messageCount("b"));
    }
}
