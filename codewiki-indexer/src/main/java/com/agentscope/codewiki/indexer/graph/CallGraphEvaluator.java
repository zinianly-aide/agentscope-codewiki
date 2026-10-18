package com.agentscope.codewiki.indexer.graph;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Evaluates explicit source-anchored call graph edges against an independent gold set. */
public final class CallGraphEvaluator {
    public record CallKey(
            String callerPath,
            int callerDeclarationLine,
            String targetPath,
            int targetDeclarationLine) {}

    public record Metrics(
            int truePositives,
            int falsePositives,
            int falseNegatives,
            double precision,
            double recall,
            double f1) {}

    private CallGraphEvaluator() {}

    public static Metrics evaluate(CodeGraph graph, Set<CallKey> gold) {
        if (graph == null || gold == null) {
            throw new IllegalArgumentException("graph and manual gold labels required");
        }
        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        for (GraphNode node : graph.getNodes()) {
            nodes.put(node.getId(), node);
        }
        Set<CallKey> predicted =
                graph.getEdges().stream()
                        .filter(edge -> edge.getType() == GraphRelation.CALLS)
                        .map(
                                edge -> {
                                    GraphNode source = nodes.get(edge.getSourceId());
                                    GraphNode target = nodes.get(edge.getTargetId());
                                    return source == null || target == null
                                            ? null
                                            : new CallKey(
                                                    source.getFilePath(),
                                                    source.getStartLine(),
                                                    target.getFilePath(),
                                                    target.getStartLine());
                                })
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet());
        long truePositives = predicted.stream().filter(gold::contains).count();
        int tp = Math.toIntExact(truePositives);
        int fp = predicted.size() - tp;
        int fn = gold.size() - tp;
        double precision = tp + fp == 0 ? (gold.isEmpty() ? 1D : 0D) : (double) tp / (tp + fp);
        double recall = tp + fn == 0 ? 1D : (double) tp / (tp + fn);
        double f1 = precision + recall == 0D ? 0D : 2D * precision * recall / (precision + recall);
        return new Metrics(tp, fp, fn, precision, recall, f1);
    }
}
