package com.agentscope.codewiki.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentscope.codewiki.indexer.graph.CodeGraph;
import com.agentscope.codewiki.indexer.graph.CodeGraphBuilder;
import com.agentscope.codewiki.indexer.graph.GraphEdge;
import com.agentscope.codewiki.indexer.graph.GraphNode;
import com.agentscope.codewiki.indexer.graph.GraphRelation;
import com.agentscope.codewiki.indexer.impact.ImpactAnalyzer;
import com.agentscope.codewiki.parser.CodeSymbol;
import com.agentscope.codewiki.parser.TreeSitterSymbolExtractor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Fixed gold call-link fixture: actual Tree-sitter AST -> graph -> precision/recall. */
class CodeGraphAstIntegrationTest {
    @TempDir Path directory;

    @Test
    void javaCallGraphUsesAstWithQualifiedImportsAndSkipsUnknownReceivers() throws Exception {
        Path app = directory.resolve("app/Controller.java");
        Path billing = directory.resolve("com/billing/PaymentService.java");
        Path other = directory.resolve("com/other/PaymentService.java");
        Files.createDirectories(app.getParent());
        Files.createDirectories(billing.getParent());
        Files.createDirectories(other.getParent());
        Files.writeString(
                app,
                "package app;\nimport com.billing.PaymentService;\n"
                        + "class Controller {\n"
                        + "  void dispatch() { local(); PaymentService.charge(); unknown.charge();\n"
                        + "    String decoy = \"charge()\"; // charge()\n"
                        + "  }\n  void local() {}\n}\n");
        Files.writeString(
                billing,
                "package com.billing;\n" + "class PaymentService { static void charge() {} }\n");
        Files.writeString(
                other,
                "package com.other;\n" + "class PaymentService { static void charge() {} }\n");

        TreeSitterSymbolExtractor extractor = new TreeSitterSymbolExtractor();
        List<CodeSymbol> symbols = extractor.extractBatch(List.of(app, billing, other));
        for (CodeSymbol symbol : symbols) {
            symbol.setFilePath(
                    directory
                            .relativize(Path.of(symbol.getFilePath()))
                            .toString()
                            .replace('\\', '/'));
        }
        CodeGraph graph = new CodeGraphBuilder().build(symbols);
        Map<String, GraphNode> nodes =
                graph.getNodes().stream().collect(Collectors.toMap(GraphNode::getId, node -> node));
        List<GraphEdge> actual =
                graph.getEdges().stream()
                        .filter(edge -> edge.getType() == GraphRelation.CALLS)
                        .toList();
        Set<String> predicted =
                actual.stream()
                        .map(
                                edge ->
                                        nodes.get(edge.getSourceId()).getFilePath()
                                                + "#"
                                                + nodes.get(edge.getSourceId()).getName()
                                                + "->"
                                                + nodes.get(edge.getTargetId()).getFilePath()
                                                + "#"
                                                + nodes.get(edge.getTargetId()).getName())
                        .collect(Collectors.toSet());
        Set<String> gold =
                Set.of(
                        "app/Controller.java#dispatch->app/Controller.java#local",
                        "app/Controller.java#dispatch->com/billing/PaymentService.java#charge");
        long truePositives = predicted.stream().filter(gold::contains).count();
        long falsePositives = predicted.size() - truePositives;
        long falseNegatives = gold.size() - truePositives;
        assertEquals(2L, truePositives, "AST precision/recall gold true positives");
        assertEquals(0L, falsePositives, "no invented call targets");
        assertEquals(0L, falseNegatives, "known local and imported calls recovered");
        assertTrue(
                actual.stream()
                        .allMatch(
                                e ->
                                        "AST".equals(e.getOrigin())
                                                && "HIGH".equals(e.getConfidence())
                                                && e.getEvidenceLine() > 0));
    }

    @Test
    void javaReceiverBindingRecoversLocalParameterAndFieldCallsWithoutCrossPackageGuesses()
            throws Exception {
        Path source = directory.resolve("app/Controller.java");
        Path target = directory.resolve("com/billing/PaymentService.java");
        Path rival = directory.resolve("com/other/PaymentService.java");
        Files.createDirectories(source.getParent());
        Files.createDirectories(target.getParent());
        Files.createDirectories(rival.getParent());
        Files.writeString(
                source,
                "package app;\nimport com.billing.PaymentService;\n"
                        + "class Controller {\n"
                        + "  PaymentService field;\n"
                        + "  void dispatch(PaymentService arg) {\n"
                        + "    PaymentService local = new PaymentService();\n"
                        + "    local.process(); arg.process(); field.process(); this.field.process();\n"
                        + "    missing.process();\n"
                        + "    { PaymentService scoped = new PaymentService(); scoped.process(); }\n"
                        + "    scoped.process();\n"
                        + "    local = new PaymentService(); local.process();\n"
                        + "  }\n"
                        + "}\n");
        Files.writeString(
                target, "package com.billing;\nclass PaymentService { void process() {} }\n");
        Files.writeString(
                rival, "package com.other;\nclass PaymentService { void process() {} }\n");
        TreeSitterSymbolExtractor extractor = new TreeSitterSymbolExtractor();
        List<CodeSymbol> symbols = extractor.extractBatch(List.of(source, target, rival));
        for (CodeSymbol symbol : symbols) {
            symbol.setFilePath(
                    directory
                            .relativize(Path.of(symbol.getFilePath()))
                            .toString()
                            .replace('\\', '/'));
        }
        CodeGraph graph = new CodeGraphBuilder().build(symbols);
        var nodes = graph.getNodes().stream().collect(Collectors.toMap(GraphNode::getId, n -> n));
        var calls =
                graph.getEdges().stream()
                        .filter(e -> e.getType() == GraphRelation.CALLS)
                        .filter(e -> "dispatch".equals(nodes.get(e.getSourceId()).getName()))
                        .toList();
        // Five call sites to the *same* method become one dependency edge with
        // distinct source evidence lines. Unknown and out-of-scope receivers and
        // post-reassignment references must not create additional edges.
        assertEquals(1, calls.size());
        assertTrue(
                calls.stream()
                        .allMatch(
                                e ->
                                        "com/billing/PaymentService.java"
                                                        .equals(
                                                                nodes.get(e.getTargetId())
                                                                        .getFilePath())
                                                && "AST".equals(e.getOrigin())
                                                && "MEDIUM".equals(e.getConfidence())
                                                && e.getEvidenceLine() > 0));
        assertEquals(List.of(7, 9), calls.get(0).getEvidenceLines());
        var impact = new ImpactAnalyzer(graph).analyze(List.of(), List.of("process"));
        assertTrue(
                impact.getDirectImpacts().stream()
                        .anyMatch(
                                edge ->
                                        "MEDIUM".equals(edge.getConfidence())
                                                && edge.getEvidenceLines().equals(List.of(7, 9))));
    }
}
