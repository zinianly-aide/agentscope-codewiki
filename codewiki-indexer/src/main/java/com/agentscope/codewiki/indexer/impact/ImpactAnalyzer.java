package com.agentscope.codewiki.indexer.impact;

import com.agentscope.codewiki.indexer.graph.CodeGraph;
import com.agentscope.codewiki.indexer.graph.CodeGraphQueryService;
import com.agentscope.codewiki.indexer.graph.GraphEdge;
import com.agentscope.codewiki.indexer.graph.GraphNode;
import com.agentscope.codewiki.indexer.incremental.IncrementalReport;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computes symbol, file, and module impact reports from the existing code graph and query service.
 */
public class ImpactAnalyzer {

    private static final int DEFAULT_MAX_DEPTH = 8;

    private final CodeGraph graph;
    private final CodeGraphQueryService queryService;
    private final Map<String, GraphNode> nodesById;
    private final Map<String, List<GraphEdge>> incomingEdges;

    public ImpactAnalyzer(CodeGraph graph) {
        this.graph = graph;
        this.queryService = new CodeGraphQueryService(graph);
        this.nodesById = indexNodes(graph);
        this.incomingEdges = indexIncomingEdges(graph.getEdges());
    }

    public ImpactReport analyze(List<String> changedFiles, List<String> changedSymbols) {
        return analyze(changedFiles, changedSymbols, DEFAULT_MAX_DEPTH);
    }

    public ImpactReport analyze(
            List<String> changedFiles, List<String> changedSymbols, int maxDepth) {
        Set<String> changedSymbolSet = orderedStrings(changedSymbols);
        Set<String> seedIds = seedIds(changedFiles, changedSymbolSet);
        TraversalResult traversal = traverseDependents(seedIds, Math.max(1, maxDepth));
        List<ImpactEdge> direct = edgesAtDepth(traversal.edges(), 1);
        List<ImpactEdge> transitive =
                traversal.edges().stream().filter(edge -> edge.getDepth() > 1).toList();
        Set<String> modules = affectedModules(seedIds, traversal.affectedNodeIds());

        ImpactReport report = new ImpactReport();
        report.setChangedSymbols(new ArrayList<>(changedSymbolSet));
        report.setDirectImpacts(direct);
        report.setTransitiveImpacts(transitive);
        report.setAffectedModules(new ArrayList<>(modules));
        report.setRiskLevel(
                calculateRiskLevel(
                                traversal.affectedNodeIds().size(),
                                modules.size(),
                                traversal.maxDepth())
                        .name());
        return report;
    }

    public ImpactReport analyze(IncrementalReport incrementalReport, List<String> changedSymbols) {
        Set<String> changedFiles = new LinkedHashSet<>();
        if (incrementalReport != null) {
            changedFiles.addAll(incrementalReport.getAdded());
            changedFiles.addAll(incrementalReport.getModified());
            changedFiles.addAll(incrementalReport.getDeleted());
            incrementalReport.getRenamed().forEach(rename -> changedFiles.add(rename.getTo()));
        }
        return analyze(new ArrayList<>(changedFiles), changedSymbols);
    }

    public ImpactReport.RiskLevel calculateRiskLevel(
            int impactedNodeCount, int crossModuleCount, int callChainDepth) {
        if (impactedNodeCount >= 20 || crossModuleCount >= 5 || callChainDepth >= 5) {
            return ImpactReport.RiskLevel.CRITICAL;
        }
        if (impactedNodeCount >= 10 || crossModuleCount >= 3 || callChainDepth >= 4) {
            return ImpactReport.RiskLevel.HIGH;
        }
        if (impactedNodeCount >= 4 || crossModuleCount >= 2 || callChainDepth >= 2) {
            return ImpactReport.RiskLevel.MEDIUM;
        }
        return ImpactReport.RiskLevel.LOW;
    }

    public List<String> impactedFiles(String changedFile) {
        ImpactReport report = analyze(List.of(changedFile), List.of());
        return java.util.stream.Stream.concat(
                        report.getDirectImpacts().stream(), report.getTransitiveImpacts().stream())
                .map(edge -> edge.getTarget().getFilePath())
                .distinct()
                .sorted()
                .toList();
    }

    public List<List<String>> impactedModules(String changedModule) {
        return queryService.dependencyChain(changedModule);
    }

    private Set<String> seedIds(List<String> changedFiles, Set<String> changedSymbols) {
        Set<String> seeds = new LinkedHashSet<>();
        Set<String> normalizedFiles =
                orderedStrings(changedFiles).stream()
                        .map(this::normalize)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        for (GraphNode node : sortedNodes(graph.getNodes())) {
            if (normalizedFiles.contains(normalize(node.getFilePath()))
                    || matchesAnySymbol(node, changedSymbols)) {
                seeds.add(node.getId());
            }
        }
        return seeds;
    }

    private TraversalResult traverseDependents(Set<String> seedIds, int maxDepth) {
        List<ImpactEdge> impacts = new ArrayList<>();
        Set<String> affected = new LinkedHashSet<>();
        Set<String> seenEdges = new LinkedHashSet<>();
        Map<String, Integer> bestDepth = new LinkedHashMap<>();
        Queue<TraversalStep> queue = new ArrayDeque<>();
        seedIds.forEach(seed -> queue.add(new TraversalStep(seed, 0)));

        while (!queue.isEmpty()) {
            TraversalStep step = queue.poll();
            if (step.depth() >= maxDepth) {
                continue;
            }
            for (GraphEdge edge : incomingEdges.getOrDefault(step.nodeId(), List.of())) {
                GraphNode source = nodesById.get(edge.getSourceId());
                GraphNode target = nodesById.get(edge.getTargetId());
                if (source == null || target == null || seedIds.contains(source.getId())) {
                    continue;
                }
                int nextDepth = step.depth() + 1;
                String impactId = target.getId() + "->" + source.getId() + ":" + nextDepth;
                if (seenEdges.add(impactId)) {
                    ImpactEdge impact =
                            new ImpactEdge(
                                    ImpactNode.fromGraphNode(target),
                                    ImpactNode.fromGraphNode(source),
                                    edge.getType().name(),
                                    nextDepth,
                                    edge.getOrigin(),
                                    edge.getConfidence(),
                                    edge.getEvidenceLine());
                    impact.setEvidenceLines(edge.getEvidenceLines());
                    impacts.add(impact);
                }
                Integer previousBest = bestDepth.get(source.getId());
                if (previousBest == null || nextDepth < previousBest) {
                    bestDepth.put(source.getId(), nextDepth);
                    affected.add(source.getId());
                    queue.add(new TraversalStep(source.getId(), nextDepth));
                }
            }
        }
        impacts.sort(
                Comparator.comparingInt(ImpactEdge::getDepth)
                        .thenComparing(edge -> edge.getTarget().getModule())
                        .thenComparing(edge -> edge.getTarget().getFilePath())
                        .thenComparing(edge -> edge.getTarget().getName()));
        int depth = impacts.stream().mapToInt(ImpactEdge::getDepth).max().orElse(0);
        return new TraversalResult(impacts, affected, depth);
    }

    private List<ImpactEdge> edgesAtDepth(List<ImpactEdge> edges, int depth) {
        return edges.stream().filter(edge -> edge.getDepth() == depth).toList();
    }

    private Set<String> affectedModules(Set<String> seedIds, Set<String> affectedNodeIds) {
        Set<String> modules = new LinkedHashSet<>();
        seedIds.stream()
                .map(nodesById::get)
                .filter(node -> node != null)
                .forEach(node -> modules.add(node.getModule()));
        affectedNodeIds.stream()
                .map(nodesById::get)
                .filter(node -> node != null)
                .forEach(node -> modules.add(node.getModule()));
        return modules.stream().sorted().collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean matchesAnySymbol(GraphNode node, Set<String> changedSymbols) {
        for (String symbol : changedSymbols) {
            String normalized = normalize(symbol);
            if (node.getId().equals(symbol)
                    || node.getName().equals(symbol)
                    || node.getName().equals(normalized)
                    || nullToEmpty(node.getSignature()).contains(symbol)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, GraphNode> indexNodes(CodeGraph graph) {
        Map<String, GraphNode> index = new LinkedHashMap<>();
        for (GraphNode node : sortedNodes(graph.getNodes())) {
            index.put(node.getId(), node);
        }
        return index;
    }

    private Map<String, List<GraphEdge>> indexIncomingEdges(List<GraphEdge> edges) {
        Map<String, List<GraphEdge>> index = new LinkedHashMap<>();
        edges.stream()
                .sorted(Comparator.comparing(GraphEdge::getId))
                .forEach(
                        edge ->
                                index.computeIfAbsent(
                                                edge.getTargetId(), ignored -> new ArrayList<>())
                                        .add(edge));
        return index;
    }

    private List<GraphNode> sortedNodes(List<GraphNode> nodes) {
        return nodes.stream()
                .sorted(
                        Comparator.comparing(GraphNode::getFilePath)
                                .thenComparing(GraphNode::getStartLine)
                                .thenComparing(GraphNode::getName))
                .toList();
    }

    private Set<String> orderedStrings(List<String> values) {
        if (values == null) {
            return new LinkedHashSet<>();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::normalize)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalize(String value) {
        return nullToEmpty(value).replace('\\', '/');
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record TraversalStep(String nodeId, int depth) {}

    private record TraversalResult(
            List<ImpactEdge> edges, Set<String> affectedNodeIds, int maxDepth) {}
}
