package com.agentscope.codewiki.retriever;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Orchestrates BM25, vector, dependency-graph, and summary retrieval into a ContextPack. */
public class HybridRetriever {

    private static final int DEFAULT_RETRIEVER_LIMIT = 12;

    private final Retriever bm25Retriever;
    private final Retriever vectorRetriever;
    private final Retriever dependencyGraphRetriever;
    private final Retriever summaryRetriever;
    private final ContextBuilder contextBuilder;

    public HybridRetriever(RetrievalCorpus corpus) {
        this(corpus, new HashingEmbeddingModel());
    }

    public HybridRetriever(RetrievalCorpus corpus, EmbeddingModel embeddingModel) {
        this(
                new Bm25Retriever(corpus),
                new VectorRetriever(corpus, embeddingModel),
                new DependencyGraphRetriever(corpus),
                new SummaryRetriever(corpus),
                new ContextBuilder(corpus));
    }

    public HybridRetriever(
            Retriever bm25Retriever,
            Retriever vectorRetriever,
            Retriever dependencyGraphRetriever,
            Retriever summaryRetriever,
            ContextBuilder contextBuilder) {
        this.bm25Retriever = bm25Retriever;
        this.vectorRetriever = vectorRetriever;
        this.dependencyGraphRetriever = dependencyGraphRetriever;
        this.summaryRetriever = summaryRetriever;
        this.contextBuilder = contextBuilder;
    }

    public ContextPack retrieve(String question) {
        return retrieve(question, DEFAULT_RETRIEVER_LIMIT);
    }

    public ContextPack retrieve(String question, int perRetrieverLimit) {
        int limit = Math.max(1, perRetrieverLimit);
        List<RetrievalHit> hits = new ArrayList<>();
        hits.addAll(bm25Retriever.retrieve(question, limit));
        hits.addAll(vectorRetriever.retrieve(question, limit));
        hits.addAll(dependencyGraphRetriever.retrieve(question, limit));
        hits.addAll(summaryRetriever.retrieve(question, limit));
        hits.sort(Comparator.comparingDouble(RetrievalHit::score).reversed());
        return contextBuilder.build(question, hits);
    }
}
