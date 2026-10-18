package com.agentscope.codewiki.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentscope.codewiki.indexer.graph.CodeGraph;
import com.agentscope.codewiki.indexer.graph.CodeGraphBuilder;
import com.agentscope.codewiki.indexer.graph.CodeGraphQueryService;
import com.agentscope.codewiki.indexer.graph.GraphEdge;
import com.agentscope.codewiki.indexer.graph.GraphNode;
import com.agentscope.codewiki.indexer.graph.GraphRelation;
import com.agentscope.codewiki.parser.CodeSymbol;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CodeGraphBuilderTest {

    @Test
    void shouldBuildGraphJsonWithSupportedRelations() {
        CodeSymbol service =
                symbol(
                        "OrderService",
                        "class",
                        "class OrderService extends BaseService implements Repository",
                        "orders/src/OrderService.java",
                        1,
                        "import common.Repository;\n"
                                + "class OrderService extends BaseService implements Repository {\n"
                                + "  Mapper mapper;\n"
                                + "  void save() { mapper.map(); }\n"
                                + "}");
        CodeSymbol repository =
                symbol(
                        "Repository",
                        "interface",
                        "interface Repository",
                        "common/src/Repository.java",
                        1,
                        "interface Repository {}");
        CodeSymbol base =
                symbol(
                        "BaseService",
                        "class",
                        "class BaseService",
                        "common/src/BaseService.java",
                        1,
                        "class BaseService {}");
        CodeSymbol mapper =
                symbol(
                        "mapper",
                        "method",
                        "void map()",
                        "mapping/src/Mapper.java",
                        10,
                        "void map() {}");
        mapper.setName("map");

        CodeGraph graph = new CodeGraphBuilder().build(List.of(service, repository, base, mapper));

        assertEquals(4, graph.getNodes().size());
        assertTrue(graph.toJson().contains("\"nodes\""));
        assertTrue(graph.toJson().contains("\"edges\""));
        assertEdge(graph, GraphRelation.IMPORTS, "OrderService", "Repository");
        assertEdge(graph, GraphRelation.IMPLEMENTS, "OrderService", "Repository");
        assertEdge(graph, GraphRelation.EXTENDS, "OrderService", "BaseService");
        assertEdge(graph, GraphRelation.CALLS, "OrderService", "map");
    }

    @Test
    void shouldTraverseCallChainThroughRelatedSymbols() {
        CodeSymbol controller =
                symbol(
                        "Controller",
                        "class",
                        "class Controller",
                        "api/Controller.java",
                        1,
                        "class Controller { void handle() { service(); } }");
        CodeSymbol service =
                symbol(
                        "service",
                        "method",
                        "void service()",
                        "service/Service.java",
                        1,
                        "void service() { repository(); }");
        CodeSymbol repository =
                symbol(
                        "repository",
                        "method",
                        "void repository()",
                        "repository/Repository.java",
                        1,
                        "void repository() {}");
        CodeGraph graph = new CodeGraphBuilder().build(List.of(controller, service, repository));
        CodeGraphQueryService queryService = new CodeGraphQueryService(graph);

        List<String> controllerRelated = names(queryService.relatedSymbols("Controller"));
        List<String> serviceRelated = names(queryService.relatedSymbols("service"));

        assertTrue(controllerRelated.contains("service"));
        assertTrue(serviceRelated.contains("Controller"));
        assertTrue(serviceRelated.contains("repository"));
    }

    @Test
    void shouldReturnImpactedFilesAndModuleDependencyChains() {
        CodeSymbol api =
                symbol(
                        "Api",
                        "class",
                        "class Api",
                        "api/src/Api.java",
                        1,
                        "class Api { void route() { service(); } }");
        CodeSymbol service =
                symbol(
                        "service",
                        "method",
                        "void service()",
                        "service/src/Service.java",
                        1,
                        "void service() { store(); }");
        CodeSymbol store =
                symbol(
                        "store",
                        "method",
                        "void store()",
                        "storage/src/Store.java",
                        1,
                        "void store() {}");
        CodeGraph graph = new CodeGraphBuilder().build(List.of(api, service, store));
        CodeGraphQueryService queryService = new CodeGraphQueryService(graph);

        List<String> impactedFiles = queryService.impactedFiles("service/src/Service.java");
        List<List<String>> chains = queryService.dependencyChain("api");

        assertEquals(
                List.of("api/src/Api.java", "service/src/Service.java", "storage/src/Store.java"),
                impactedFiles);
        assertFalse(chains.isEmpty());
        assertTrue(chains.contains(List.of("api", "service", "storage")));
    }

    @Test
    void shouldNotInferEdgesFromStringsAndComments() {
        CodeSymbol source =
                symbol(
                        "Controller",
                        "class",
                        "class Controller",
                        "app/src/Controller.java",
                        1,
                        "class Controller { // requestPayment()\n"
                                + " String sample = \"requestPayment()\";\n"
                                + " /* requestPayment() */\n"
                                + " void execute() {} } ");
        CodeSymbol method =
                symbol(
                        "requestPayment",
                        "method",
                        "void requestPayment()",
                        "payments/src/Payments.java",
                        1,
                        "void requestPayment() {} ");
        CodeGraph graph = new CodeGraphBuilder().build(List.of(source, method));
        assertFalse(
                graph.getEdges().stream()
                        .anyMatch(
                                edge ->
                                        node(graph, edge.getSourceId())
                                                        .getName()
                                                        .equals("Controller")
                                                && node(graph, edge.getTargetId())
                                                        .getName()
                                                        .equals("requestPayment")));
    }

    @Test
    void shouldRejectUnresolvedSameNameTargetsFromDifferentModules() {
        CodeSymbol source =
                symbol(
                        "Controller",
                        "class",
                        "class Controller",
                        "api/src/Controller.java",
                        1,
                        "class Controller { void execute() { process(); } }");
        CodeSymbol first =
                symbol(
                        "process",
                        "method",
                        "void process()",
                        "billing/src/Process.java",
                        1,
                        "void process() {} ");
        CodeSymbol second =
                symbol(
                        "process",
                        "method",
                        "void process()",
                        "reports/src/Process.java",
                        1,
                        "void process() {} ");
        CodeGraph graph = new CodeGraphBuilder().build(List.of(source, first, second));
        assertFalse(
                graph.getEdges().stream()
                        .anyMatch(
                                edge ->
                                        node(graph, edge.getSourceId())
                                                        .getName()
                                                        .equals("Controller")
                                                && node(graph, edge.getTargetId())
                                                        .getName()
                                                        .equals("process")));
    }

    @Test
    void shouldPreferSameFileMethodWhenOtherModulesShareItsName() {
        CodeSymbol source =
                symbol(
                        "Controller",
                        "class",
                        "class Controller",
                        "api/src/Controller.java",
                        1,
                        "class Controller { void execute() { process(); } }");
        CodeSymbol local =
                symbol(
                        "process",
                        "method",
                        "void process()",
                        "api/src/Controller.java",
                        12,
                        "void process() {} ");
        CodeSymbol remote =
                symbol(
                        "process",
                        "method",
                        "void process()",
                        "reports/src/Other.java",
                        1,
                        "void process() {} ");
        CodeGraph graph = new CodeGraphBuilder().build(List.of(source, local, remote));
        assertTrue(
                graph.getEdges().stream()
                        .anyMatch(
                                edge ->
                                        node(graph, edge.getSourceId())
                                                        .getName()
                                                        .equals("Controller")
                                                && node(graph, edge.getTargetId())
                                                        .getFilePath()
                                                        .equals("api/src/Controller.java")
                                                && node(graph, edge.getTargetId())
                                                        .getName()
                                                        .equals("process")));
        assertFalse(
                graph.getEdges().stream()
                        .anyMatch(
                                edge ->
                                        node(graph, edge.getSourceId())
                                                        .getName()
                                                        .equals("Controller")
                                                && node(graph, edge.getTargetId())
                                                        .getFilePath()
                                                        .equals("reports/src/Other.java")
                                                && node(graph, edge.getTargetId())
                                                        .getName()
                                                        .equals("process")));
    }

    @Test
    void shouldIgnorePythonCommentsAndDocstrings() {
        CodeSymbol source =
                symbol(
                        "run",
                        "function",
                        "def run():",
                        "python/app.py",
                        1,
                        "def run():\n  \"\"\"launch()\"\"\"\n  # launch()\n  pass");
        CodeSymbol target =
                symbol(
                        "launch",
                        "function",
                        "def launch():",
                        "python/other.py",
                        1,
                        "def launch(): pass");
        CodeGraph graph = new CodeGraphBuilder().build(List.of(source, target));
        assertTrue(graph.getEdges().isEmpty());
    }

    @Test
    void shouldCountAnObservedCallOnceInsteadOfBothCallAndReference() {
        CodeSymbol source =
                symbol(
                        "Api",
                        "class",
                        "class Api",
                        "api/Api.java",
                        1,
                        "class Api { void handle() { service(); } }");
        CodeSymbol target =
                symbol(
                        "service",
                        "method",
                        "void service()",
                        "business/Service.java",
                        1,
                        "void service() {} ");
        CodeGraph graph = new CodeGraphBuilder().build(List.of(source, target));
        long callEdges =
                graph.getEdges().stream()
                        .filter(
                                e ->
                                        node(graph, e.getSourceId()).getName().equals("Api")
                                                && node(graph, e.getTargetId())
                                                        .getName()
                                                        .equals("service")
                                                && e.getType() == GraphRelation.CALLS)
                        .count();
        long references =
                graph.getEdges().stream()
                        .filter(
                                e ->
                                        node(graph, e.getSourceId()).getName().equals("Api")
                                                && node(graph, e.getTargetId())
                                                        .getName()
                                                        .equals("service")
                                                && e.getType() == GraphRelation.REFERENCES)
                        .count();
        assertEquals(1L, callEdges);
        assertEquals(0L, references);
    }

    @Test
    void shouldAllowRealTypeScriptToTsxCall() {
        CodeSymbol source =
                symbol(
                        "screen",
                        "function",
                        "function screen()",
                        "app/core.ts",
                        1,
                        "function screen() { return renderPanel(); }");
        CodeSymbol target =
                symbol(
                        "renderPanel",
                        "function",
                        "function renderPanel()",
                        "app/view.tsx",
                        1,
                        "function renderPanel() { return null; }");
        CodeGraph graph = new CodeGraphBuilder().build(List.of(source, target));
        assertEdge(graph, GraphRelation.CALLS, "screen", "renderPanel");
    }

    @Test
    void shouldIgnoreTypeScriptCommentedCallsAndTemplateStrings() {
        CodeSymbol source =
                symbol(
                        "screen",
                        "function",
                        "function screen()",
                        "app/core.ts",
                        1,
                        "function screen() { // renderPanel()\n"
                                + " const msg = `renderPanel()`;\n /* renderPanel() */ return 0; }");
        CodeSymbol target =
                symbol(
                        "renderPanel",
                        "function",
                        "function renderPanel()",
                        "app/view.tsx",
                        1,
                        "function renderPanel() { return null; }");
        CodeGraph graph = new CodeGraphBuilder().build(List.of(source, target));
        assertTrue(graph.getEdges().isEmpty());
    }

    @Test
    void shouldNotLinkJavaCallsToPythonSameNameFunctions() {
        CodeSymbol source =
                symbol(
                        "Api",
                        "class",
                        "class Api",
                        "api/Api.java",
                        1,
                        "class Api { void go() { launch(); } }");
        CodeSymbol target =
                symbol(
                        "launch",
                        "function",
                        "def launch():",
                        "scripts/helper.py",
                        1,
                        "def launch(): pass");
        CodeGraph graph = new CodeGraphBuilder().build(List.of(source, target));
        assertTrue(graph.getEdges().isEmpty());
    }

    @Test
    void shouldUseFullyQualifiedImportToSelectMatchingJavaType() {
        CodeSymbol source =
                symbol(
                        "Handler",
                        "class",
                        "class Handler",
                        "app/src/main/java/app/Handler.java",
                        1,
                        "import com.alpha.User;\nclass Handler { User user; }");
        CodeSymbol alpha =
                symbol(
                        "User",
                        "class",
                        "class User",
                        "alpha/src/main/java/com/alpha/User.java",
                        1,
                        "class User {}");
        CodeSymbol beta =
                symbol(
                        "User",
                        "class",
                        "class User",
                        "beta/src/main/java/com/beta/User.java",
                        1,
                        "class User {}");
        CodeGraph graph = new CodeGraphBuilder().build(List.of(source, alpha, beta));
        assertTrue(
                graph.getEdges().stream()
                        .anyMatch(
                                edge ->
                                        edge.getType() == GraphRelation.IMPORTS
                                                && node(graph, edge.getTargetId())
                                                        .getFilePath()
                                                        .contains("com/alpha/User.java")));
        assertFalse(
                graph.getEdges().stream()
                        .anyMatch(
                                edge ->
                                        node(graph, edge.getSourceId()).getName().equals("Handler")
                                                && node(graph, edge.getTargetId())
                                                        .getFilePath()
                                                        .contains("com/beta/User.java")));
    }

    @Test
    void shouldPreferClassDefinitionOverSameNamedConstructorForReferencedType() {
        CodeSymbol caller =
                symbol(
                        "Entry",
                        "class",
                        "class Entry",
                        "api/src/Entry.java",
                        1,
                        "class Entry { void go() { new Analyzer(); } }");
        CodeSymbol clazz =
                symbol(
                        "Analyzer",
                        "class",
                        "class Analyzer",
                        "engine/src/Analyzer.java",
                        1,
                        "class Analyzer { Analyzer() {} }");
        CodeSymbol ctor =
                symbol(
                        "Analyzer",
                        "constructor",
                        "Analyzer()",
                        "engine/src/Analyzer.java",
                        2,
                        "Analyzer() {}");
        CodeGraph graph = new CodeGraphBuilder().build(List.of(caller, clazz, ctor));
        assertTrue(
                graph.getEdges().stream()
                        .anyMatch(
                                e ->
                                        node(graph, e.getSourceId()).getName().equals("Entry")
                                                && node(graph, e.getTargetId())
                                                        .getType()
                                                        .equals("class")
                                                && node(graph, e.getTargetId())
                                                        .getName()
                                                        .equals("Analyzer")));
    }

    @Test
    void shouldNotMistakeOwnMethodDeclarationForCallToAnotherSameNamedMethod() {
        CodeSymbol first =
                symbol(
                        "searchableText",
                        "method",
                        "String searchableText()",
                        "indexer/src/Builder.java",
                        1,
                        "String searchableText() { return this.toString(); }");
        CodeSymbol second =
                symbol(
                        "searchableText",
                        "method",
                        "String searchableText()",
                        "retriever/src/Document.java",
                        1,
                        "String searchableText() { return this.toString(); }");
        CodeGraph graph = new CodeGraphBuilder().build(List.of(first, second));
        assertTrue(graph.getEdges().isEmpty());
    }

    @Test
    void shouldAvoidInheritanceEdgesToMethodsThatShareTypeNames() {
        CodeSymbol child =
                symbol(
                        "Child",
                        "class",
                        "class Child extends Parent",
                        "app/src/Child.java",
                        1,
                        "class Child extends Parent {}");
        CodeSymbol fakeParent =
                symbol(
                        "Parent",
                        "method",
                        "void Parent()",
                        "app/src/Other.java",
                        1,
                        "void Parent() {} ");
        CodeGraph graph = new CodeGraphBuilder().build(List.of(child, fakeParent));
        assertFalse(
                graph.getEdges().stream()
                        .anyMatch(
                                edge ->
                                        edge.getType() == GraphRelation.EXTENDS
                                                || edge.getType() == GraphRelation.IMPLEMENTS));
    }

    private void assertEdge(
            CodeGraph graph, GraphRelation relation, String sourceName, String targetName) {
        Set<String> matchingEdges =
                graph.getEdges().stream()
                        .filter(edge -> edge.getType() == relation)
                        .filter(
                                edge ->
                                        node(graph, edge.getSourceId())
                                                .getName()
                                                .equals(sourceName))
                        .filter(
                                edge ->
                                        node(graph, edge.getTargetId())
                                                .getName()
                                                .equals(targetName))
                        .map(GraphEdge::getId)
                        .collect(Collectors.toSet());
        assertFalse(matchingEdges.isEmpty(), relation + " edge missing");
    }

    private GraphNode node(CodeGraph graph, String id) {
        return graph.getNodes().stream()
                .filter(candidate -> candidate.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private List<String> names(List<GraphNode> nodes) {
        return nodes.stream().map(GraphNode::getName).toList();
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
