package com.agentscope.codewiki.retriever;

/** Code snippet selected for an answer context. */
public record CodeContext(
        String documentId,
        String module,
        String path,
        String language,
        String content,
        double score) {

    public CodeContext {
        documentId = documentId == null ? "" : documentId;
        module = module == null ? "" : module;
        path = path == null ? "" : path;
        language = language == null ? "" : language;
        content = content == null ? "" : content;
    }
}
