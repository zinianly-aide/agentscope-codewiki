package com.agentscope.codewiki.retriever;

/** Module-level context derived from retrieved documents. */
public record ModuleContext(String module, long matchedDocuments, double score) {

    public ModuleContext {
        module = module == null ? "" : module;
    }
}
