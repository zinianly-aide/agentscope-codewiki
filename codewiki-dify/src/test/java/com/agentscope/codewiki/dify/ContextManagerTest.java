package com.agentscope.codewiki.dify;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContextManagerTest {

    private ConversationStore store;
    private ContextManager cm;

    @BeforeEach
    void setUp() {
        store = new ConversationStore();
        cm = new ContextManager(store, 4096, 20, 10);
    }

    @Test
    void shouldAssembleEmptyContextForNewConversation() {
        ContextPack pack = cm.assembleContext("new-conv", "What is Java?");

        assertEquals("new-conv", pack.getConversationId());
        assertEquals("What is Java?", pack.getCurrentQuery());
        assertTrue(pack.getRecentMessages().isEmpty());
        assertNull(pack.getLongTermSummary());
        assertTrue(pack.getEstimatedTokens() <= pack.getTokenBudget());
    }

    @Test
    void shouldIncludeRecentMessages() {
        // Populate with history
        store.saveMessage("conv-1", new MessageEntry(MessageEntry.Role.USER, "What is Spring?"));
        store.saveMessage(
                "conv-1",
                new MessageEntry(
                        MessageEntry.Role.ASSISTANT,
                        "Spring is a Java framework for building enterprise applications."));

        ContextPack pack = cm.assembleContext("conv-1", "Tell me more about Spring Boot.");

        assertEquals(2, pack.getRecentMessages().size());
        assertEquals("What is Spring?", pack.getRecentMessages().get(0).getContent());
        assertEquals("Tell me more about Spring Boot.", pack.getCurrentQuery());
    }

    @Test
    void shouldIncludeLongTermSummaryWhenAvailable() {
        // Seed summary
        SummaryMemory summary =
                new SummaryMemory(
                        "conv-1",
                        "User asked about dependency injection and configuration.",
                        5,
                        java.time.Instant.now());
        store.saveSummary("conv-1", summary);

        ContextPack pack = cm.assembleContext("conv-1", "How does @Autowired work?");

        assertNotNull(pack.getLongTermSummary());
        assertTrue(pack.getLongTermSummary().getSummary().contains("dependency injection"));
    }

    @Test
    void shouldRespectTokenBudget() {
        int tightBudget = 500;

        // Add many messages
        for (int i = 0; i < 30; i++) {
            store.saveMessage(
                    "conv-1",
                    new MessageEntry(
                            MessageEntry.Role.USER,
                            "This is a somewhat long message number "
                                    + i
                                    + " with extra padding to consume tokens xxxxxxxxxxxxxxxxxxxx"));
        }

        ContextPack pack = cm.assembleContext("conv-1", "final query", tightBudget);

        // Should not exceed budget
        assertTrue(
                pack.getEstimatedTokens() <= tightBudget,
                "estimated " + pack.getEstimatedTokens() + " should be <= " + tightBudget);
        // Should still include some messages
        assertFalse(pack.getRecentMessages().isEmpty());
    }

    @Test
    void shouldSaveTurnAndIncrementCount() {
        cm.saveTurn("conv-1", "What is DI?", "Dependency Injection is...", 10);

        assertEquals(2, store.messageCount("conv-1")); // user + assistant
        List<MessageEntry> recent = store.getRecentMessages("conv-1", 2);
        assertEquals(MessageEntry.Role.USER, recent.get(0).getRole());
        assertEquals(MessageEntry.Role.ASSISTANT, recent.get(1).getRole());
    }

    @Test
    void shouldTriggerSummarizationOnThreshold() {
        // Set a very low threshold
        ContextManager lowCm = new ContextManager(store, 4096, 6, 4);

        // Add enough messages to trigger summarization (6 messages = threshold)
        for (int i = 0; i < 3; i++) {
            lowCm.saveTurn(
                    "conv-1",
                    "question " + i,
                    "answer " + i + " with some extra text to make it meaningful",
                    5);
        }

        // Summary should have been auto-created
        assertTrue(store.loadSummary("conv-1").isPresent());
        assertTrue(store.loadSummary("conv-1").get().getSummarizedMessageCount() > 0);
    }

    @Test
    void shouldNotSummarizeBelowThreshold() {
        ContextManager highThreshold = new ContextManager(store, 4096, 100, 10);

        for (int i = 0; i < 3; i++) {
            highThreshold.saveTurn("conv-1", "q" + i, "a" + i, 5);
        }

        assertTrue(store.loadSummary("conv-1").isEmpty());
    }

    @Test
    void shouldTrackMessageRoles() {
        cm.saveTurn("conv-1", "User question?", "Assistant answer.", 5);

        List<MessageEntry> msgs = store.getRecentMessages("conv-1", 10);
        assertEquals(2, msgs.size());
        assertEquals(MessageEntry.Role.USER, msgs.get(0).getRole());
        assertEquals(MessageEntry.Role.ASSISTANT, msgs.get(1).getRole());
    }

    @Test
    void estimateTokensShouldReturnPositive() {
        assertEquals(0, ContextManager.estimateTokens(null));
        assertEquals(0, ContextManager.estimateTokens(""));
        assertEquals(1, ContextManager.estimateTokens("ab"));
        assertTrue(ContextManager.estimateTokens("hello world") > 0);
    }

    @Test
    void messageEntryTokenEstimate() {
        MessageEntry shortMsg = new MessageEntry(MessageEntry.Role.USER, "hi");
        assertEquals(1, shortMsg.estimatedTokens());

        MessageEntry longMsg = new MessageEntry(MessageEntry.Role.ASSISTANT, "a".repeat(1000));
        assertEquals(250, longMsg.estimatedTokens());
    }
}
