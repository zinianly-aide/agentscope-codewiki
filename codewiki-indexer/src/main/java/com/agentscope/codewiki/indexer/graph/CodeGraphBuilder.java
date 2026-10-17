package com.agentscope.codewiki.indexer.graph;

import com.agentscope.codewiki.parser.CodeSymbol;
import com.agentscope.codewiki.parser.RepositoryScanner;
import com.agentscope.codewiki.parser.TreeSitterSymbolExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Builds a code knowledge graph from existing symbol extraction output. */
public class CodeGraphBuilder {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".java", ".ts", ".tsx", ".py");

    private final RepositoryScanner repositoryScanner;
    private final TreeSitterSymbolExtractor symbolExtractor;

    public CodeGraphBuilder() {
        this(new RepositoryScanner(), null);
    }

    public CodeGraphBuilder(
            RepositoryScanner repositoryScanner, TreeSitterSymbolExtractor symbolExtractor) {
        this.repositoryScanner = repositoryScanner;
        this.symbolExtractor = symbolExtractor;
    }

    /**
     * Scans a repository and builds a graph by reusing RepositoryScanner and
     * TreeSitterSymbolExtractor.
     */
    public CodeGraph build(Path repositoryRoot) throws IOException {
        repositoryScanner.scan(repositoryRoot);
        TreeSitterSymbolExtractor extractor =
                symbolExtractor == null ? new TreeSitterSymbolExtractor() : symbolExtractor;
        List<CodeSymbol> symbols = new ArrayList<>();
        try (var stream = Files.walk(repositoryRoot)) {
            List<Path> files =
                    stream.filter(Files::isRegularFile)
                            .filter(this::isSupportedSource)
                            .sorted()
                            .toList();
            for (Path file : files) {
                List<CodeSymbol> extracted = extractor.extract(file);
                String relativePath = repositoryRoot.relativize(file).toString().replace('\\', '/');
                for (CodeSymbol symbol : extracted) {
                    if (symbol.getFilePath() == null || symbol.getFilePath().isBlank()) {
                        symbol.setFilePath(relativePath);
                    }
                    symbols.add(symbol);
                }
            }
        }
        return build(symbols);
    }

    /** Builds a graph from already extracted symbols without reparsing source files. */
    public CodeGraph build(List<CodeSymbol> symbols) {
        Map<String, GraphNode> nodesById = new LinkedHashMap<>();
        for (CodeSymbol symbol : sortedSymbols(symbols)) {
            GraphNode node = GraphNode.fromSymbol(symbol);
            nodesById.putIfAbsent(node.getId(), node);
        }

        Map<String, List<GraphNode>> nodesByName = indexByName(nodesById.values());
        Map<String, GraphEdge> edgesById = new LinkedHashMap<>();
        for (CodeSymbol symbol : sortedSymbols(symbols)) {
            GraphNode source = nodesById.get(GraphNode.stableId(symbol));
            if (source == null) {
                continue;
            }
            String text = searchableText(symbol);
            String lowerText = text.toLowerCase(Locale.ROOT);
            for (GraphNode target : nodesById.values()) {
                if (source.equals(target)) {
                    continue;
                }
                addDetectedEdges(source, target, text, lowerText, symbol, edgesById);
            }
            addImportEdges(source, text, nodesByName, edgesById);
        }

        return new CodeGraph(
                new ArrayList<>(nodesById.values()), new ArrayList<>(edgesById.values()));
    }

    private void addDetectedEdges(
            GraphNode source,
            GraphNode target,
            String text,
            String lowerText,
            CodeSymbol sourceSymbol,
            Map<String, GraphEdge> edgesById) {
        String targetName = target.getName();
        if (targetName == null
                || targetName.isBlank()
                || !lowerText.contains(targetName.toLowerCase(Locale.ROOT))) {
            return;
        }
        if (containsInheritance(text, targetName, "implements")) {
            addEdge(
                    source,
                    target,
                    GraphRelation.IMPLEMENTS,
                    "implements " + targetName,
                    edgesById);
        }
        if (containsInheritance(text, targetName, "extends")
                || containsPythonBaseClass(sourceSymbol, targetName)) {
            addEdge(source, target, GraphRelation.EXTENDS, "extends " + targetName, edgesById);
        }
        if (containsCall(text, targetName)) {
            addEdge(source, target, GraphRelation.CALLS, targetName + "(...) call", edgesById);
        }
        if (containsWord(text, targetName)) {
            if (isTypeLike(target)) {
                addEdge(source, target, GraphRelation.USES, "type usage: " + targetName, edgesById);
            } else {
                addEdge(
                        source,
                        target,
                        GraphRelation.REFERENCES,
                        "reference: " + targetName,
                        edgesById);
            }
        }
    }

    private void addImportEdges(
            GraphNode source,
            String text,
            Map<String, List<GraphNode>> nodesByName,
            Map<String, GraphEdge> edgesById) {
        for (String importedName : importedNames(text)) {
            for (GraphNode target : nodesByName.getOrDefault(importedName, List.of())) {
                if (!source.equals(target)) {
                    addEdge(
                            source,
                            target,
                            GraphRelation.IMPORTS,
                            "import " + importedName,
                            edgesById);
                }
            }
        }
    }

    private void addEdge(
            GraphNode source,
            GraphNode target,
            GraphRelation type,
            String evidence,
            Map<String, GraphEdge> edgesById) {
        GraphEdge edge = new GraphEdge(source.getId(), target.getId(), type, evidence);
        edgesById.putIfAbsent(edge.getId(), edge);
    }

    private Map<String, List<GraphNode>> indexByName(Iterable<GraphNode> nodes) {
        Map<String, List<GraphNode>> index = new LinkedHashMap<>();
        for (GraphNode node : nodes) {
            index.computeIfAbsent(node.getName(), ignored -> new ArrayList<>()).add(node);
        }
        return index;
    }

    private List<CodeSymbol> sortedSymbols(List<CodeSymbol> symbols) {
        return symbols.stream()
                .sorted(
                        Comparator.comparing(
                                        (CodeSymbol symbol) -> nullToEmpty(symbol.getFilePath()))
                                .thenComparingInt(CodeSymbol::getStartLine)
                                .thenComparing(symbol -> nullToEmpty(symbol.getName())))
                .toList();
    }

    private boolean isSupportedSource(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private String searchableText(CodeSymbol symbol) {
        return String.join(
                "\n",
                nullToEmpty(symbol.getName()),
                nullToEmpty(symbol.getSignature()),
                nullToEmpty(symbol.getSourceCode()));
    }

    private boolean containsCall(String text, String name) {
        return Pattern.compile("(?<![\\w$])" + Pattern.quote(name) + "\\s*\\(")
                .matcher(text)
                .find();
    }

    private boolean containsWord(String text, String name) {
        return Pattern.compile("(?<![\\w$])" + Pattern.quote(name) + "(?![\\w$])")
                .matcher(text)
                .find();
    }

    private boolean containsInheritance(String text, String targetName, String keyword) {
        Pattern pattern =
                Pattern.compile(
                        "(?i)\\b"
                                + keyword
                                + "\\b[^\\n{(]*\\b"
                                + Pattern.quote(targetName)
                                + "\\b");
        return pattern.matcher(text).find();
    }

    private boolean containsPythonBaseClass(CodeSymbol sourceSymbol, String targetName) {
        String signature = nullToEmpty(sourceSymbol.getSignature());
        Pattern pattern =
                Pattern.compile("class\\s+\\w+\\s*\\([^)]*\\b" + Pattern.quote(targetName));
        return pattern.matcher(signature).find();
    }

    private boolean isTypeLike(GraphNode node) {
        String type = nullToEmpty(node.getType()).toLowerCase(Locale.ROOT);
        return type.contains("class") || type.contains("interface") || type.contains("enum");
    }

    private Set<String> importedNames(String text) {
        Set<String> names = new LinkedHashSet<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("import ")) {
                collectImportTail(trimmed, names);
            } else if (trimmed.startsWith("from ")) {
                int importIndex = trimmed.indexOf(" import ");
                if (importIndex > 0) {
                    String imported = trimmed.substring(importIndex + " import ".length());
                    for (String part : imported.split(",")) {
                        names.add(cleanImportName(part));
                    }
                }
            }
        }
        names.removeIf(String::isBlank);
        return names;
    }

    private void collectImportTail(String importLine, Set<String> names) {
        String tail = importLine.substring("import ".length()).replace(";", "").trim();
        if (tail.startsWith("{") && tail.contains("}")) {
            tail = tail.substring(1, tail.indexOf('}'));
            for (String part : tail.split(",")) {
                names.add(cleanImportName(part));
            }
        } else if (!tail.startsWith("static ")) {
            names.add(cleanImportName(tail));
        }
    }

    private String cleanImportName(String imported) {
        String value = imported.trim();
        int asIndex = value.indexOf(" as ");
        if (asIndex > 0) {
            value = value.substring(0, asIndex).trim();
        }
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('.'));
        if (slash >= 0 && slash + 1 < value.length()) {
            value = value.substring(slash + 1);
        }
        return value.replace("'", "").replace("\"", "").trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
