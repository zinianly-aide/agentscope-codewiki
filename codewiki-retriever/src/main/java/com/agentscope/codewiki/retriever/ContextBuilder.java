package com.agentscope.codewiki.retriever;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds a compact answer context from ranked hybrid retrieval hits. */
public class ContextBuilder {

    private static final int DEFAULT_CODE_LIMIT = 8;
    private static final int DEFAULT_SUMMARY_LIMIT = 8;
    private static final int DEFAULT_CHAIN_LIMIT = 6;
    private static final int DEFAULT_MODULE_LIMIT = 6;

    private final RetrievalCorpus corpus;
    private final DependencyGraphRetriever dependencyGraphRetriever;

    public ContextBuilder(RetrievalCorpus corpus) {
        this.corpus = corpus;
        this.dependencyGraphRetriever = new DependencyGraphRetriever(corpus);
    }

    public ContextPack build(String question, List<RetrievalHit> hits) {
        return build(
                question,
                hits,
                DEFAULT_CODE_LIMIT,
                DEFAULT_SUMMARY_LIMIT,
                DEFAULT_CHAIN_LIMIT,
                DEFAULT_MODULE_LIMIT);
    }

    public ContextPack build(
            String question,
            List<RetrievalHit> hits,
            int codeLimit,
            int summaryLimit,
            int chainLimit,
            int moduleLimit) {
        Map<String, AggregatedHit> aggregatedHits = aggregate(hits);
        List<AggregatedHit> rankedHits =
                aggregatedHits.values().stream()
                        .sorted(Comparator.comparingDouble(AggregatedHit::score).reversed())
                        .toList();

        List<CodeContext> code =
                rankedHits.stream()
                        .limit(Math.max(0, codeLimit))
                        .map(AggregatedHit::toCodeContext)
                        .toList();
        List<SummaryContext> summaries =
                rankedHits.stream()
                        .filter(hit -> !hit.document().getSummary().isBlank())
                        .limit(Math.max(0, summaryLimit))
                        .map(AggregatedHit::toSummaryContext)
                        .toList();
        List<DependencyChain> chains =
                dependencyGraphRetriever.findChains(
                        rankedHits.stream().map(AggregatedHit::document).toList(), 2, chainLimit);
        List<ModuleContext> modules = buildModules(rankedHits, moduleLimit);
        return new ContextPack(question, code, summaries, chains, modules);
    }

    private Map<String, AggregatedHit> aggregate(List<RetrievalHit> hits) {
        Map<String, AggregatedHit> aggregated = new LinkedHashMap<>();
        for (RetrievalHit hit : hits == null ? List.<RetrievalHit>of() : hits) {
            aggregated.compute(
                    hit.document().getId(),
                    (id, existing) -> {
                        if (existing == null) {
                            return new AggregatedHit(hit.document(), normalizedScore(hit));
                        }
                        return existing.add(normalizedScore(hit));
                    });
        }
        if (aggregated.isEmpty()) {
            for (CodeDocument document : corpus.getDocuments()) {
                aggregated.put(document.getId(), new AggregatedHit(document, 0D));
            }
        }
        return aggregated;
    }

    private List<ModuleContext> buildModules(List<AggregatedHit> rankedHits, int moduleLimit) {
        Map<String, List<AggregatedHit>> byModule =
                rankedHits.stream()
                        .collect(Collectors.groupingBy(hit -> hit.document().getModule()));
        List<ModuleContext> modules = new ArrayList<>();
        for (Map.Entry<String, List<AggregatedHit>> entry : byModule.entrySet()) {
            double score = entry.getValue().stream().mapToDouble(AggregatedHit::score).sum();
            modules.add(new ModuleContext(entry.getKey(), entry.getValue().size(), score));
        }
        return modules.stream()
                .sorted(Comparator.comparingDouble(ModuleContext::score).reversed())
                .limit(Math.max(0, moduleLimit))
                .toList();
    }

    private double normalizedScore(RetrievalHit hit) {
        return switch (hit.type()) {
            case BM25 -> hit.score();
            case VECTOR -> hit.score() * 1.2D;
            case DEPENDENCY_GRAPH -> hit.score() * 0.8D;
            case SUMMARY -> hit.score();
        };
    }

    private record AggregatedHit(CodeDocument document, double score) {

        AggregatedHit add(double additionalScore) {
            return new AggregatedHit(document, score + additionalScore);
        }

        CodeContext toCodeContext() {
            return new CodeContext(
                    document.getId(),
                    document.getModule(),
                    document.getPath(),
                    document.getLanguage(),
                    document.getContent(),
                    score);
        }

        SummaryContext toSummaryContext() {
            return new SummaryContext(
                    document.getId(),
                    document.getModule(),
                    document.getPath(),
                    document.getSummary(),
                    score);
        }
    }
}
