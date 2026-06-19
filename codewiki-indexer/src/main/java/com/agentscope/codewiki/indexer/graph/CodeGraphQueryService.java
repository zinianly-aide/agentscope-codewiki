package com.agentscope.codewiki.indexer.graph;

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

/** Query service for symbol, file impact, and module dependency traversal. */
public class CodeGraphQueryService {

    private final CodeGraph graph;
    private final Map<String, GraphNode> nodesById;
    private final Map<String, List<GraphEdge>> outgoingEdges;
    private final Map<String, List<GraphEdge>> incomingEdges;

    public CodeGraphQueryService(CodeGraph graph) {
        this.graph = graph;
        this.nodesById = indexNodes(graph);
        this.outgoingEdges = indexEdges(graph.getEdges(), true);
        this.incomingEdges = indexEdges(graph.getEdges(), false);
    }

    /** Returns symbols directly related to a symbol name or symbol id. */
    public List<GraphNode> relatedSymbols(String symbol) {
        Set<String> relatedIds = new LinkedHashSet<>();
        for (GraphNode node : matchingSymbolNodes(symbol)) {
            for (GraphEdge edge : outgoingEdges.getOrDefault(node.getId(), List.of())) {
                relatedIds.add(edge.getTargetId());
            }
            for (GraphEdge edge : incomingEdges.getOrDefault(node.getId(), List.of())) {
                relatedIds.add(edge.getSourceId());
            }
        }
        return relatedIds.stream()
                .map(nodesById::get)
                .filter(n -> n != null)
                .sorted(this::sortNodes)
                .toList();
    }

    /**
     * Returns files impacted by a changed file through incoming and outgoing graph relationships.
     */
    public List<String> impactedFiles(String filePath) {
        Set<String> impacted = new LinkedHashSet<>();
        Set<String> frontier =
                graph.getNodes().stream()
                        .filter(node -> normalize(node.getFilePath()).equals(normalize(filePath)))
                        .map(GraphNode::getId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String nodeId : frontier) {
            GraphNode node = nodesById.get(nodeId);
            if (node != null) {
                impacted.add(node.getFilePath());
            }
            for (GraphEdge edge : outgoingEdges.getOrDefault(nodeId, List.of())) {
                addFile(edge.getTargetId(), impacted);
            }
            for (GraphEdge edge : incomingEdges.getOrDefault(nodeId, List.of())) {
                addFile(edge.getSourceId(), impacted);
            }
        }
        return impacted.stream().sorted().toList();
    }

    /** Returns module dependency chains starting from the requested module. */
    public List<List<String>> dependencyChain(String module) {
        Map<String, Set<String>> moduleGraph = buildModuleGraph();
        List<List<String>> chains = new ArrayList<>();
        Queue<List<String>> queue = new ArrayDeque<>();
        queue.add(List.of(module));
        while (!queue.isEmpty()) {
            List<String> chain = queue.poll();
            String current = chain.get(chain.size() - 1);
            Set<String> nextModules = moduleGraph.getOrDefault(current, Set.of());
            if (nextModules.isEmpty()) {
                chains.add(chain);
                continue;
            }
            for (String next : nextModules) {
                if (chain.contains(next)) {
                    List<String> cycle = new ArrayList<>(chain);
                    cycle.add(next);
                    chains.add(cycle);
                } else {
                    List<String> expanded = new ArrayList<>(chain);
                    expanded.add(next);
                    queue.add(expanded);
                }
            }
        }
        return chains.stream()
                .sorted(Comparator.comparing(chain -> String.join("/", chain)))
                .toList();
    }

    private List<GraphNode> matchingSymbolNodes(String symbol) {
        String normalized = normalize(symbol);
        return graph.getNodes().stream()
                .filter(
                        node ->
                                node.getId().equals(symbol)
                                        || node.getName().equals(symbol)
                                        || node.getName().equals(normalized))
                .sorted(this::sortNodes)
                .toList();
    }

    private Map<String, Set<String>> buildModuleGraph() {
        Map<String, Set<String>> modules = new LinkedHashMap<>();
        for (GraphEdge edge : graph.getEdges()) {
            GraphNode source = nodesById.get(edge.getSourceId());
            GraphNode target = nodesById.get(edge.getTargetId());
            if (source == null || target == null || source.getModule().equals(target.getModule())) {
                continue;
            }
            modules.computeIfAbsent(source.getModule(), ignored -> new LinkedHashSet<>())
                    .add(target.getModule());
        }
        return modules;
    }

    private Map<String, GraphNode> indexNodes(CodeGraph graph) {
        Map<String, GraphNode> index = new LinkedHashMap<>();
        for (GraphNode node : graph.getNodes()) {
            index.put(node.getId(), node);
        }
        return index;
    }

    private Map<String, List<GraphEdge>> indexEdges(List<GraphEdge> edges, boolean outgoing) {
        Map<String, List<GraphEdge>> index = new LinkedHashMap<>();
        for (GraphEdge edge : edges) {
            String key = outgoing ? edge.getSourceId() : edge.getTargetId();
            index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(edge);
        }
        return index;
    }

    private void addFile(String nodeId, Set<String> files) {
        GraphNode node = nodesById.get(nodeId);
        if (node != null) {
            files.add(node.getFilePath());
        }
    }

    private int sortNodes(GraphNode left, GraphNode right) {
        return Comparator.comparing(GraphNode::getFilePath)
                .thenComparing(GraphNode::getStartLine)
                .thenComparing(GraphNode::getName)
                .compare(left, right);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\\', '/');
    }
}
