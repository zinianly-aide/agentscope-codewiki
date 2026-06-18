package com.agentscope.codewiki.retriever;

/** Natural-language summary selected for an answer context. */
public record SummaryContext(
        String documentId, String module, String path, String summary, double score) {

    public SummaryContext {
        documentId = documentId == null ? "" : documentId;
        module = module == null ? "" : module;
        path = path == null ? "" : path;
        summary = summary == null ? "" : summary;
    }
}
