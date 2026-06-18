package com.agentscope.codewiki.retriever;

/** Produces embeddings for free-form text. */
public interface EmbeddingModel {

    double[] embed(String text);
}
