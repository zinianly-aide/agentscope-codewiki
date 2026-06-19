package com.agentscope.codewiki.indexer.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Serializable code knowledge graph with symbol nodes and relationship edges. */
public class CodeGraph {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private List<GraphNode> nodes = new ArrayList<>();
    private List<GraphEdge> edges = new ArrayList<>();

    public CodeGraph() {}

    public CodeGraph(List<GraphNode> nodes, List<GraphEdge> edges) {
        this.nodes = new ArrayList<>(nodes);
        this.edges = new ArrayList<>(edges);
    }

    public List<GraphNode> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    public void setNodes(List<GraphNode> nodes) {
        this.nodes = new ArrayList<>(nodes);
    }

    public List<GraphEdge> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    public void setEdges(List<GraphEdge> edges) {
        this.edges = new ArrayList<>(edges);
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize code graph", e);
        }
    }

    public void writeJson(Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve("graph.json"), toJson());
    }
}
