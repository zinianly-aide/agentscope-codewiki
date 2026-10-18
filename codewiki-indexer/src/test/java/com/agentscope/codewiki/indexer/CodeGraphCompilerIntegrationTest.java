package com.agentscope.codewiki.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentscope.codewiki.indexer.graph.CallGraphEvaluator;
import com.agentscope.codewiki.indexer.graph.CodeGraph;
import com.agentscope.codewiki.indexer.graph.CodeGraphBuilder;
import com.agentscope.codewiki.indexer.graph.GraphNode;
import com.agentscope.codewiki.indexer.graph.GraphRelation;
import com.agentscope.codewiki.parser.CodeSymbol;
import com.agentscope.codewiki.parser.TreeSitterSymbolExtractor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Compiler-verified calls vs a manually reviewed gold set in a self-contained Java fixture. */
class CodeGraphCompilerIntegrationTest {
    @TempDir Path root;

    @Test
    void compilerOverlayResolvesOverloadsAndInterfaceDispatchWithoutInventingImplementations()
            throws Exception {
        Path api = root.resolve("demo/Api.java");
        Path service = root.resolve("demo/Service.java");
        Files.createDirectories(api.getParent());
        Files.writeString(
                service,
                "package demo;\ninterface Service { void run(String text); }\n"
                        + "class Concrete implements Service {\n"
                        + " public void run(String text) {}\n"
                        + " public void run(int value) {}\n}\n");
        Files.writeString(
                api,
                "package demo;\nclass Api {\n"
                        + " void process(Service polymorphic, Concrete concrete) {\n"
                        + "   polymorphic.run(\"one\");\n"
                        + "   concrete.run(1);\n"
                        + "   concrete.run(\"two\");\n"
                        + " }\n}\n");
        List<Path> sources = List.of(api, service);
        List<CodeSymbol> symbols =
                new ArrayList<>(new TreeSitterSymbolExtractor().extractBatch(sources));
        for (CodeSymbol symbol : symbols) {
            symbol.setFilePath(
                    root.relativize(Path.of(symbol.getFilePath())).toString().replace('\\', '/'));
        }
        CodeGraph compiler =
                new CodeGraphBuilder().buildWithCompilerEvidence(symbols, root, sources);
        Map<String, GraphNode> nodes =
                compiler.getNodes().stream().collect(Collectors.toMap(GraphNode::getId, n -> n));
        var calls =
                compiler.getEdges().stream()
                        .filter(edge -> edge.getType() == GraphRelation.CALLS)
                        .filter(edge -> nodes.get(edge.getSourceId()).getName().equals("process"))
                        .toList();
        assertEquals(3, calls.size());
        assertTrue(
                calls.stream()
                        .allMatch(
                                e ->
                                        "JAVAC".equals(e.getOrigin())
                                                && "COMPILER".equals(e.getConfidence())));
        Set<String> predicted =
                calls.stream()
                        .map(
                                e ->
                                        nodes.get(e.getSourceId()).getName()
                                                + "@"
                                                + e.getEvidenceLine()
                                                + "->"
                                                + nodes.get(e.getTargetId()).getFilePath()
                                                + ":"
                                                + nodes.get(e.getTargetId()).getStartLine())
                        .collect(Collectors.toSet());
        Set<String> gold =
                Set.of(
                        "process@4->demo/Service.java:2",
                        "process@5->demo/Service.java:5",
                        "process@6->demo/Service.java:4");
        assertEquals(gold, predicted, "manually reviewed compiler-backed precision/recall fixture");
        var labels =
                Set.of(
                        new CallGraphEvaluator.CallKey("demo/Api.java", 3, "demo/Service.java", 2),
                        new CallGraphEvaluator.CallKey("demo/Api.java", 3, "demo/Service.java", 5),
                        new CallGraphEvaluator.CallKey("demo/Api.java", 3, "demo/Service.java", 4));
        var metrics = CallGraphEvaluator.evaluate(compiler, labels);
        assertEquals(3, metrics.truePositives());
        assertEquals(0, metrics.falsePositives());
        assertEquals(0, metrics.falseNegatives());
        assertEquals(1D, metrics.precision());
        assertEquals(1D, metrics.recall());
        assertFalse(
                compiler.getEdges().stream()
                        .anyMatch(
                                e ->
                                        e.getType() == GraphRelation.CALLS
                                                && "AST".equals(e.getOrigin())
                                                && nodes.get(e.getSourceId())
                                                        .getName()
                                                        .equals("process")),
                "no duplicate heuristic AST calls for compiler-covered sources");
    }

    @Test
    void sourcesOutsideRepositoryAreRejectedEvenWithValidJava() throws Exception {
        Path outside = Files.createTempFile("external-code", ".java");
        try {
            Files.writeString(outside, "class External {}\n");
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new CodeGraphBuilder()
                                    .buildWithCompilerEvidence(List.of(), root, List.of(outside)));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void incompleteCompilerBatchFallsBackToOriginalGraphWithoutVerifiedClaims() throws Exception {
        Path broken = root.resolve("Broken.java");
        Files.writeString(broken, "class Broken { void task() { missing.run(); } }\n");
        List<CodeSymbol> symbols = new TreeSitterSymbolExtractor().extractBatch(List.of(broken));
        for (CodeSymbol symbol : symbols) {
            symbol.setFilePath(root.relativize(Path.of(symbol.getFilePath())).toString());
        }
        CodeGraph graph =
                new CodeGraphBuilder().buildWithCompilerEvidence(symbols, root, List.of(broken));
        assertTrue(graph.getEdges().stream().noneMatch(e -> "JAVAC".equals(e.getOrigin())));
    }
}
