package com.agentscope.codewiki.retriever;

import java.util.ArrayList;
import java.util.List;

/** Splits large code documents into bounded retrieval chunks. */
public final class CodeDocumentChunker {

    public static final int DEFAULT_MAX_CHARS = 6_000;
    public static final int DEFAULT_OVERLAP_CHARS = 400;

    private CodeDocumentChunker() {}

    public static List<CodeDocument> chunk(CodeDocument document) {
        return chunk(document, DEFAULT_MAX_CHARS, DEFAULT_OVERLAP_CHARS);
    }

    public static List<CodeDocument> chunk(CodeDocument document, int maxChars, int overlapChars) {
        if (document == null) {
            return List.of();
        }
        int effectiveMax = Math.max(64, maxChars);
        int effectiveOverlap = Math.max(0, Math.min(overlapChars, effectiveMax / 2));
        String content = document.getContent();
        if (content.length() <= effectiveMax) {
            return List.of(document);
        }
        List<CodeDocument> chunks = new ArrayList<>();
        int index = 0;
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(content.length(), start + effectiveMax);
            String chunk = content.substring(start, end);
            String id = document.getId() + "#chunk-" + index;
            chunks.add(
                    CodeDocument.builder()
                            .id(id)
                            .module(document.getModule())
                            .path(document.getPath())
                            .language(document.getLanguage())
                            .content(chunk)
                            .summary(
                                    document.getSummary()
                                            + " [chunk "
                                            + (index + 1)
                                            + ", chars "
                                            + start
                                            + "-"
                                            + end
                                            + "]")
                            .dependencies(document.getDependencies())
                            .embedding(document.getEmbedding())
                            .build());
            if (end == content.length()) {
                break;
            }
            start = Math.max(end - effectiveOverlap, start + 1);
            index++;
        }
        return chunks;
    }

    public static List<CodeDocument> chunkAll(List<CodeDocument> documents) {
        List<CodeDocument> chunks = new ArrayList<>();
        for (CodeDocument document : documents == null ? List.<CodeDocument>of() : documents) {
            chunks.addAll(chunk(document));
        }
        return chunks;
    }
}
