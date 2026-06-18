package com.agentscope.codewiki.retriever;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Retriever focused on natural-language file and module summaries. */
public class SummaryRetriever implements Retriever {

    private final RetrievalCorpus corpus;

    public SummaryRetriever(RetrievalCorpus corpus) {
        this.corpus = corpus;
    }

    @Override
    public List<RetrievalHit> retrieve(String question, int limit) {
        Set<String> queryTerms = new HashSet<>(TextTokenizer.tokenize(question));
        if (queryTerms.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<RetrievalHit> hits = new ArrayList<>();
        for (CodeDocument document : corpus.getDocuments()) {
            List<String> summaryTerms = TextTokenizer.tokenize(document.getSummary());
            long overlaps = summaryTerms.stream().filter(queryTerms::contains).count();
            if (overlaps > 0L) {
                double score = overlaps / Math.sqrt(Math.max(1, summaryTerms.size()));
                hits.add(
                        new RetrievalHit(
                                document, RetrievalType.SUMMARY, score, "Summary overlap"));
            }
        }
        return hits.stream()
                .sorted(Comparator.comparingDouble(RetrievalHit::score).reversed())
                .limit(limit)
                .toList();
    }
}
