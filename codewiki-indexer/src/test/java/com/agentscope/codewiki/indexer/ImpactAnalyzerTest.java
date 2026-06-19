package com.agentscope.codewiki.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentscope.codewiki.indexer.graph.CodeGraph;
import com.agentscope.codewiki.indexer.graph.CodeGraphBuilder;
import com.agentscope.codewiki.indexer.impact.ImpactAnalyzer;
import com.agentscope.codewiki.indexer.impact.ImpactEdge;
import com.agentscope.codewiki.indexer.impact.ImpactReport;
import com.agentscope.codewiki.parser.CodeSymbol;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImpactAnalyzerTest {

    @Test
    void shouldReportSymbolImpactWithDirectAndTransitiveCallers() {
        CodeGraph graph =
                new CodeGraphBuilder()
                        .build(
                                List.of(
                                        symbol(
                                                "login",
                                                "method",
                                                "void login()",
                                                "service/src/UserService.java",
                                                1,
                                                "void login() {}"),
                                        symbol(
                                                "AuthController",
                                                "class",
                                                "class AuthController",
                                                "api/src/AuthController.java",
                                                1,
                                                "class AuthController { void auth() { login(); } }"),
                                        symbol(
                                                "PermissionService",
                                                "class",
                                                "class PermissionService",
                                                "permission/src/PermissionService.java",
                                                1,
                                                "class PermissionService { void check() { AuthController(); } }")));
        ImpactReport report = new ImpactAnalyzer(graph).analyze(List.of(), List.of("login"));

        assertEquals(List.of("login"), report.getChangedSymbols());
        assertEquals(List.of("AuthController"), targetNames(report.getDirectImpacts()));
        assertTrue(targetNames(report.getTransitiveImpacts()).contains("PermissionService"));
        assertFalse(report.toJson().contains(ImpactReport.DEFAULT_FILE_NAME));
        assertTrue(report.toJson().contains("changedSymbols"));
    }

    @Test
    void shouldReportFileImpactUsingDependentFiles() {
        CodeGraph graph =
                new CodeGraphBuilder()
                        .build(
                                List.of(
                                        symbol(
                                                "store",
                                                "method",
                                                "void store()",
                                                "storage/src/Store.java",
                                                1,
                                                "void store() {}"),
                                        symbol(
                                                "service",
                                                "method",
                                                "void service()",
                                                "service/src/Service.java",
                                                1,
                                                "void service() { store(); }"),
                                        symbol(
                                                "Api",
                                                "class",
                                                "class Api",
                                                "api/src/Api.java",
                                                1,
                                                "class Api { void route() { service(); } }")));
        ImpactAnalyzer analyzer = new ImpactAnalyzer(graph);

        assertEquals(
                List.of("api/src/Api.java"), analyzer.impactedFiles("service/src/Service.java"));
        ImpactReport report = analyzer.analyze(List.of("storage/src/Store.java"), List.of());
        assertEquals(List.of("service"), targetNames(report.getDirectImpacts()));
        assertTrue(targetNames(report.getTransitiveImpacts()).contains("Api"));
    }

    @Test
    void shouldReportModuleImpactThroughDependencyChains() {
        CodeGraph graph =
                new CodeGraphBuilder()
                        .build(
                                List.of(
                                        symbol(
                                                "Repository",
                                                "class",
                                                "class Repository",
                                                "repository/src/Repository.java",
                                                1,
                                                "class Repository {}"),
                                        symbol(
                                                "service",
                                                "method",
                                                "void service()",
                                                "service/src/Service.java",
                                                1,
                                                "void service() { Repository(); }"),
                                        symbol(
                                                "Api",
                                                "class",
                                                "class Api",
                                                "api/src/Api.java",
                                                1,
                                                "class Api { void route() { service(); } }")));
        ImpactAnalyzer analyzer = new ImpactAnalyzer(graph);
        ImpactReport report =
                analyzer.analyze(List.of("repository/src/Repository.java"), List.of());

        assertFalse(analyzer.impactedModules("api").isEmpty());
        assertEquals(List.of("api", "repository", "service"), report.getAffectedModules());
    }

    @Test
    void shouldCalculateRiskFromImpactCountModulesAndDepth() {
        ImpactAnalyzer analyzer = new ImpactAnalyzer(new CodeGraph(List.of(), List.of()));

        assertEquals(ImpactReport.RiskLevel.LOW, analyzer.calculateRiskLevel(1, 1, 1));
        assertEquals(ImpactReport.RiskLevel.MEDIUM, analyzer.calculateRiskLevel(4, 1, 1));
        assertEquals(ImpactReport.RiskLevel.HIGH, analyzer.calculateRiskLevel(1, 3, 1));
        assertEquals(ImpactReport.RiskLevel.CRITICAL, analyzer.calculateRiskLevel(1, 1, 5));
    }

    private List<String> targetNames(List<ImpactEdge> edges) {
        return edges.stream().map(edge -> edge.getTarget().getName()).toList();
    }

    private CodeSymbol symbol(
            String name,
            String type,
            String signature,
            String filePath,
            int startLine,
            String sourceCode) {
        CodeSymbol symbol = new CodeSymbol();
        symbol.setName(name);
        symbol.setType(type);
        symbol.setSignature(signature);
        symbol.setFilePath(filePath);
        symbol.setStartLine(startLine);
        symbol.setEndLine(startLine + Math.max(1, sourceCode.split("\\R").length));
        symbol.setSourceCode(sourceCode);
        return symbol;
    }
}
