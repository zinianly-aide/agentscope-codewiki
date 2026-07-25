package com.agentscope.codewiki.retriever;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CodeDocumentChunkerTest {

    @Test
    void shouldKeepSmallDocumentsUnchanged() {
        CodeDocument document =
                CodeDocument.builder()
                        .id("src/App.java")
                        .module("app")
                        .path("src/App.java")
                        .language("Java")
                        .content("class App {}")
                        .summary("small")
                        .build();

        List<CodeDocument> chunks = CodeDocumentChunker.chunk(document, 100, 10);

        assertEquals(List.of(document), chunks);
    }

    @Test
    void shouldSplitLargeDocumentsWithStableChunkIds() {
        CodeDocument document =
                CodeDocument.builder()
                        .id("src/Large.java")
                        .module("app")
                        .path("src/Large.java")
                        .language("Java")
                        .content("a".repeat(260))
                        .summary("large")
                        .dependencies(List.of("src/Dep.java"))
                        .build();

        List<CodeDocument> chunks = CodeDocumentChunker.chunk(document, 100, 20);

        assertEquals(3, chunks.size());
        assertEquals("src/Large.java#chunk-0", chunks.get(0).getId());
        assertEquals("src/Large.java#chunk-1", chunks.get(1).getId());
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getContent().length() <= 100));
        assertTrue(chunks.get(0).getSummary().contains("chunk 1"));
        assertEquals(List.of("src/Dep.java"), chunks.get(0).getDependencies());
    }
}
