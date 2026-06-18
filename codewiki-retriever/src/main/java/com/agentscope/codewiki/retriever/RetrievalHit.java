package com.agentscope.codewiki.retriever;

/** A scored retrieval result. */
public record RetrievalHit(
        CodeDocument document, RetrievalType type, double score, String explanation) {

    public RetrievalHit {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        explanation = explanation == null ? "" : explanation;
    }
}
