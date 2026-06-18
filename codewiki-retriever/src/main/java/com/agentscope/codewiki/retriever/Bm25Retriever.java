package com.agentscope.codewiki.retriever;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** In-memory BM25 retriever over code, paths, modules, dependencies, and summaries. */
public class Bm25Retriever implements Retriever {

    private static final double K1 = 1.5D;
    private static final double B = 0.75D;

    private final RetrievalCorpus corpus;
    private final Map<String, Map<String, Integer>> termFrequenciesByDocument = new HashMap<>();
    private final Map<String, Integer> documentFrequencies = new HashMap<>();
    private final Map<String, Integer> lengthsByDocument = new HashMap<>();
    private final double averageDocumentLength;

    public Bm25Retriever(RetrievalCorpus corpus) {
        this.corpus = corpus;
        int totalLength = 0;
        for (CodeDocument document : corpus.getDocuments()) {
            List<String> tokens = TextTokenizer.tokenize(document.searchableText());
            totalLength += tokens.size();
            lengthsByDocument.put(document.getId(), tokens.size());
            Map<String, Integer> frequencies = new HashMap<>();
            Set<String> uniqueTerms = new HashSet<>();
            for (String token : tokens) {
                frequencies.merge(token, 1, Integer::sum);
                uniqueTerms.add(token);
            }
            termFrequenciesByDocument.put(document.getId(), frequencies);
            for (String term : uniqueTerms) {
                documentFrequencies.merge(term, 1, Integer::sum);
            }
        }
        averageDocumentLength =
                corpus.getDocuments().isEmpty()
                        ? 0D
                        : (double) totalLength / corpus.getDocuments().size();
    }

    @Override
    public List<RetrievalHit> retrieve(String question, int limit) {
        List<String> queryTerms = TextTokenizer.tokenize(question);
        if (queryTerms.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<RetrievalHit> hits = new ArrayList<>();
        for (CodeDocument document : corpus.getDocuments()) {
            double score = score(document, queryTerms);
            if (score > 0D) {
                hits.add(
                        new RetrievalHit(
                                document, RetrievalType.BM25, score, "BM25 lexical match"));
            }
        }
        return hits.stream()
                .sorted(Comparator.comparingDouble(RetrievalHit::score).reversed())
                .limit(limit)
                .toList();
    }

    private double score(CodeDocument document, List<String> queryTerms) {
        double score = 0D;
        int documentCount = corpus.getDocuments().size();
        int documentLength = lengthsByDocument.getOrDefault(document.getId(), 0);
        Map<String, Integer> frequencies =
                termFrequenciesByDocument.getOrDefault(document.getId(), Map.of());
        for (String term : queryTerms) {
            int termFrequency = frequencies.getOrDefault(term, 0);
            if (termFrequency == 0) {
                continue;
            }
            int documentFrequency = documentFrequencies.getOrDefault(term, 0);
            double idf =
                    Math.log(
                            1D
                                    + (documentCount - documentFrequency + 0.5D)
                                            / (documentFrequency + 0.5D));
            double denominator =
                    termFrequency
                            + K1
                                    * (1D
                                            - B
                                            + B
                                                    * documentLength
                                                    / Math.max(1D, averageDocumentLength));
            score += idf * (termFrequency * (K1 + 1D)) / denominator;
        }
        return score;
    }
}
