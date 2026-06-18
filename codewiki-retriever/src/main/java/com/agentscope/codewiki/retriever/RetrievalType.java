package com.agentscope.codewiki.retriever;

/** Retrieval signal families used by the hybrid retriever. */
public enum RetrievalType {
    BM25,
    VECTOR,
    DEPENDENCY_GRAPH,
    SUMMARY
}
