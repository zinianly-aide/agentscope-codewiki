package com.agentscope.codewiki.retriever;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Vector retriever using cosine similarity. */
public class VectorRetriever implements Retriever {

    private final RetrievalCorpus corpus;
    private final EmbeddingModel embeddingModel;

    public VectorRetriever(RetrievalCorpus corpus, EmbeddingModel embeddingModel) {
        this.corpus = corpus;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<RetrievalHit> retrieve(String question, int limit) {
        if (question == null || question.isBlank() || limit <= 0) {
            return List.of();
        }
        double[] queryVector = embeddingModel.embed(question);
        List<RetrievalHit> hits = new ArrayList<>();
        for (CodeDocument document : corpus.getDocuments()) {
            double[] documentVector = document.getEmbedding();
            if (documentVector.length == 0) {
                documentVector = embeddingModel.embed(document.searchableText());
            }
            double score = cosine(queryVector, documentVector);
            if (score > 0D) {
                hits.add(
                        new RetrievalHit(
                                document,
                                RetrievalType.VECTOR,
                                score,
                                "Vector semantic similarity"));
            }
        }
        return hits.stream()
                .sorted(Comparator.comparingDouble(RetrievalHit::score).reversed())
                .limit(limit)
                .toList();
    }

    private double cosine(double[] left, double[] right) {
        int length = Math.min(left.length, right.length);
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int index = 0; index < length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0D || rightNorm == 0D) {
            return 0D;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
