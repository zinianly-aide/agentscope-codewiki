package com.agentscope.codewiki.retriever;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Retrieves nodes that are adjacent to lexical matches in the code dependency graph. */
public class DependencyGraphRetriever implements Retriever {

    private static final int DEFAULT_GRAPH_DEPTH = 2;

    private final RetrievalCorpus corpus;
    private final Bm25Retriever seedRetriever;
    private final int graphDepth;

    public DependencyGraphRetriever(RetrievalCorpus corpus) {
        this(corpus, DEFAULT_GRAPH_DEPTH);
    }

    public DependencyGraphRetriever(RetrievalCorpus corpus, int graphDepth) {
        this.corpus = corpus;
        this.seedRetriever = new Bm25Retriever(corpus);
        this.graphDepth = Math.max(1, graphDepth);
    }

    @Override
    public List<RetrievalHit> retrieve(String question, int limit) {
        if (question == null || question.isBlank() || limit <= 0) {
            return List.of();
        }
        List<RetrievalHit> seeds = seedRetriever.retrieve(question, Math.max(limit, 3));
        List<RetrievalHit> hits = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RetrievalHit seed : seeds) {
            collectAdjacent(seed.document(), seed.score(), hits, seen);
        }
        return hits.stream()
                .sorted(Comparator.comparingDouble(RetrievalHit::score).reversed())
                .limit(limit)
                .toList();
    }

    public List<DependencyChain> findChains(List<CodeDocument> seeds, int maxDepth, int limit) {
        List<DependencyChain> chains = new ArrayList<>();
        int depth = Math.max(1, maxDepth);
        for (CodeDocument seed : seeds) {
            Deque<String> path = new ArrayDeque<>();
            collectChains(seed, depth, path, chains);
        }
        return chains.stream()
                .sorted(Comparator.comparingDouble(DependencyChain::score).reversed())
                .limit(Math.max(0, limit))
                .toList();
    }

    private void collectAdjacent(
            CodeDocument seed, double seedScore, List<RetrievalHit> hits, Set<String> seen) {
        for (String dependencyId : seed.getDependencies()) {
            corpus.findById(dependencyId)
                    .ifPresent(
                            dependency -> {
                                if (seen.add(dependency.getId())) {
                                    hits.add(
                                            new RetrievalHit(
                                                    dependency,
                                                    RetrievalType.DEPENDENCY_GRAPH,
                                                    seedScore * 0.75D,
                                                    "Dependency adjacent to " + seed.getId()));
                                }
                            });
        }
    }

    private void collectChains(
            CodeDocument document,
            int remainingDepth,
            Deque<String> path,
            List<DependencyChain> chains) {
        path.addLast(document.getId());
        if (remainingDepth == 0 || document.getDependencies().isEmpty()) {
            chains.add(new DependencyChain(List.copyOf(path), 1D / path.size()));
            path.removeLast();
            return;
        }
        boolean expanded = false;
        for (String dependencyId : document.getDependencies()) {
            if (path.contains(dependencyId)) {
                continue;
            }
            CodeDocument dependency = corpus.findById(dependencyId).orElse(null);
            if (dependency != null) {
                expanded = true;
                collectChains(dependency, remainingDepth - 1, path, chains);
            }
        }
        if (!expanded) {
            chains.add(new DependencyChain(List.copyOf(path), 1D / path.size()));
        }
        path.removeLast();
    }
}
