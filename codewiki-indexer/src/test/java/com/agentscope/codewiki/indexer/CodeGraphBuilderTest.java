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
