package com.agentscope.codewiki.retriever;

import java.util.List;

/** Component that retrieves ranked documents for a user question. */
public interface Retriever {

    List<RetrievalHit> retrieve(String question, int limit);
}
