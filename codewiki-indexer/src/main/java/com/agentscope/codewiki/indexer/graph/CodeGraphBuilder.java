package com.agentscope.codewiki.indexer.graph;

import com.agentscope.codewiki.parser.CodeReference;
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
    private static final Pattern IDENTIFIER = Pattern.compile("[\\p{L}_$][\\p{L}\\p{N}_$]*");

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
                    // The bridge emits absolute filesystem paths. Graph IDs and evidence
                    // must remain repository-relative across machines and cache restarts.
                    symbol.setFilePath(relativePath);
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
        Map<String, List<GraphNode>> namesLower = new LinkedHashMap<>();
        for (GraphNode node : nodesById.values()) {
            namesLower
                    .computeIfAbsent(
                            node.getName().toLowerCase(Locale.ROOT), ignored -> new ArrayList<>())
                    .add(node);
        }
        Map<String, GraphEdge> edgesById = new LinkedHashMap<>();
        for (CodeSymbol symbol : sortedSymbols(symbols)) {
            GraphNode source = nodesById.get(GraphNode.stableId(symbol));
            if (source == null) {
                continue;
            }
            String text = stripNonCode(searchableText(symbol), languageOf(source.getFilePath()));
            String lowerText = text.toLowerCase(Locale.ROOT);
            Set<String> tokens = new LinkedHashSet<>();
            var identifiers = IDENTIFIER.matcher(text);
            while (identifiers.find()) {
                tokens.add(identifiers.group().toLowerCase(Locale.ROOT));
            }
            Set<String> visited = new LinkedHashSet<>();
            for (String token : tokens) {
                for (GraphNode target :
                        resolveTargets(source, text, namesLower.getOrDefault(token, List.of()))) {
                    if (!source.equals(target) && visited.add(target.getId())) {
                        addDetectedEdges(
                                source,
                                target,
                                text,
                                lowerText,
                                symbol,
                                "java".equals(languageOf(source.getFilePath()))
                                        && symbol.getReferences() != null,
                                edgesById);
                    }
                }
            }
            addImportEdges(source, text, nodesByName, edgesById);
        }

        addAstCallEdges(symbols, nodesById, nodesByName, edgesById);
        return new CodeGraph(
                new ArrayList<>(nodesById.values()), new ArrayList<>(edgesById.values()));
    }

    /**
     * Conservative linking policy: no cross-language guesses and no broadcast links to unrelated
     * same-name symbols. Same-file bindings win, then explicit Java imports; unresolved ambiguities
     * remain unlinked rather than inflating impact reports.
     */
    private List<GraphNode> resolveTargets(
            GraphNode source, String text, List<GraphNode> candidates) {
        if (isCallable(source) && candidates.stream().anyMatch(source::equals)) {
            // A method declaration with this name is not a call to another
            // same-named method in some unrelated module.
            return List.of();
        }
        String language = languageOf(source.getFilePath());
        List<GraphNode> compatible =
                candidates.stream()
                        .filter(n -> language.equals(languageOf(n.getFilePath())))
                        .filter(n -> !source.equals(n))
                        .toList();
        if (compatible.size() <= 1) {
            return compatible;
        }
        List<GraphNode> types = compatible.stream().filter(this::isTypeLike).toList();
        if (types.size() == 1
                && compatible.stream()
                        .allMatch(
                                n ->
                                        isTypeLike(n)
                                                || (n.getFilePath()
                                                                .equals(types.get(0).getFilePath())
                                                        && "constructor".equals(n.getType())))) {
            return types;
        }
        List<GraphNode> sameFile =
                compatible.stream()
                        .filter(n -> n.getFilePath().equals(source.getFilePath()))
                        .toList();
        if (!sameFile.isEmpty()) {
            return sameFile;
        }
        if ("java".equals(language)) {
            List<GraphNode> imported =
                    compatible.stream()
                            .filter(n -> isTypeLike(n) && isExplicitlyImported(text, n))
                            .toList();
            if (!imported.isEmpty()) {
                return imported.size() == 1 ? imported : List.of();
            }
        }
        // Without type/receiver resolution, picking every overload with the same
        // simple method name would produce false positive call/impact edges.
        return List.of();
    }

    private boolean isExplicitlyImported(String text, GraphNode node) {
        String path = node.getFilePath().replace('\\', '/');
        var imports =
                Pattern.compile(
                                "(?m)^\\s*import\\s+([a-zA-Z_$][\\w$]*(?:\\.[a-zA-Z_$][\\w$]*)+)\\s*;?")
                        .matcher(text);
        while (imports.find()) {
            String fullyQualified = imports.group(1);
            if (fullyQualified.endsWith("." + node.getName())
                    && ("/" + path).endsWith("/" + fullyQualified.replace('.', '/') + ".java")) {
                return true;
            }
        }
        return false;
    }

    private static String languageOf(String path) {
        if (path == null) {
            return "";
        }
        String value = path.toLowerCase(Locale.ROOT);
        if (value.endsWith(".ts") || value.endsWith(".tsx")) {
            return "typescript";
        }
        if (value.endsWith(".java")) {
            return "java";
        }
        if (value.endsWith(".py")) {
            return "python";
        }
        return "";
    }

    private boolean isCallable(GraphNode node) {
        String type = nullToEmpty(node.getType()).toLowerCase(Locale.ROOT);
        return type.contains("method") || type.contains("function") || type.contains("constructor");
    }

    /** Remove literal/comment text while preserving line breaks and token boundaries. */
    private static String stripNonCode(String source, String language) {
        char[] chars = source.toCharArray();
        int i = 0;
        while (i < chars.length) {
            char ch = chars[i];
            boolean hash = "python".equals(language) && ch == '#';
            boolean line = ch == '/' && i + 1 < chars.length && chars[i + 1] == '/';
            boolean block = ch == '/' && i + 1 < chars.length && chars[i + 1] == '*';
            if (hash || line) {
                while (i < chars.length && chars[i] != '\n') {
                    chars[i++] = ' ';
                }
                continue;
            }
            if (block) {
                int begin = i;
                i += 2;
                while (i + 1 < chars.length && !(chars[i] == '*' && chars[i + 1] == '/')) {
                    i++;
                }
                i = Math.min(chars.length, i + 2);
                blank(chars, begin, i);
                continue;
            }
            if (ch == '\'' || ch == '"' || ch == '`') {
                int begin = i;
                int quoteLength =
                        "python".equals(language)
                                        && i + 2 < chars.length
                                        && chars[i + 1] == ch
                                        && chars[i + 2] == ch
                                ? 3
                                : 1;
                i += quoteLength;
                while (i < chars.length) {
                    if (chars[i] == '\\' && quoteLength == 1) {
                        i = Math.min(chars.length, i + 2);
                        continue;
                    }
                    if (i + quoteLength <= chars.length) {
                        boolean closed = true;
                        for (int j = 0; j < quoteLength; j++) {
                            if (chars[i + j] != ch) {
                                closed = false;
                                break;
                            }
                        }
                        if (closed) {
                            i += quoteLength;
                            break;
                        }
                    }
                    i++;
                }
                blank(chars, begin, i);
                continue;
            }
            i++;
        }
        return new String(chars);
    }

    private static void blank(char[] chars, int start, int end) {
        for (int i = start; i < end; i++) {
            if (chars[i] != '\n' && chars[i] != '\r') {
                chars[i] = ' ';
            }
        }
    }

    /**
     * Emit only resolved Java AST call edges. Unbound instance receivers are deliberately omitted.
     */
    private void addAstCallEdges(
            List<CodeSymbol> symbols,
            Map<String, GraphNode> nodesById,
            Map<String, List<GraphNode>> nodesByName,
            Map<String, GraphEdge> edgesById) {
        Map<String, CodeSymbol> symbolsById = new LinkedHashMap<>();
        for (CodeSymbol symbol : sortedSymbols(symbols)) {
            symbolsById.putIfAbsent(GraphNode.stableId(symbol), symbol);
        }
        for (CodeSymbol caller : sortedSymbols(symbols)) {
            if (caller.getReferences() == null
                    || !"java".equals(languageOf(caller.getFilePath()))) {
                continue;
            }
            GraphNode source = nodesById.get(GraphNode.stableId(caller));
            if (source == null || !isCallable(source)) {
                continue;
            }
            for (CodeReference reference : caller.getReferences()) {
                if (reference == null
                        || !"call".equals(reference.kind())
                        || reference.name() == null
                        || reference.name().isBlank()) {
                    continue;
                }
                List<GraphNode> targets =
                        nodesByName.getOrDefault(reference.name(), List.of()).stream()
                                .filter(this::isCallable)
                                .filter(node -> !node.equals(source))
                                .filter(node -> "java".equals(languageOf(node.getFilePath())))
                                .filter(
                                        node ->
                                                astReceiverMatches(
                                                        caller,
                                                        reference,
                                                        symbolsById.get(node.getId()),
                                                        node))
                                .toList();
                if (targets.size() == 1) {
                    GraphNode target = targets.get(0);
                    GraphEdge edge =
                            new GraphEdge(
                                    source.getId(),
                                    target.getId(),
                                    GraphRelation.CALLS,
                                    "AST invocation: "
                                            + reference.name()
                                            + "(...) at L"
                                            + reference.line(),
                                    "AST",
                                    "HIGH",
                                    reference.line());
                    edgesById.put(edge.getId(), edge);
                }
            }
        }
    }

    private boolean astReceiverMatches(
            CodeSymbol caller, CodeReference reference, CodeSymbol callee, GraphNode target) {
        if (callee == null || callee.getOwnerType() == null || caller.getOwnerType() == null) {
            return false;
        }
        String receiver = reference.receiver();
        if (receiver == null || "this".equals(receiver)) {
            return target.getFilePath().equals(caller.getFilePath())
                    && caller.getOwnerType().equals(callee.getOwnerType());
        }
        // Only static-style ClassName.method() with a provably identified owner.
        // Variable.method() would require scope-aware type binding and is omitted.
        if (!receiver.equals(callee.getOwnerType())) {
            return false;
        }
        if (target.getFilePath().equals(caller.getFilePath())) {
            return true;
        }
        if (caller.getImports() == null) {
            return false;
        }
        return caller.getImports().stream()
                .anyMatch(
                        importStatement -> {
                            String imported =
                                    importStatement
                                            .trim()
                                            .replaceFirst("^import\\s+(?:static\\s+)?", "")
                                            .replace(";", "");
                            if (!imported.endsWith("." + receiver)) {
                                return false;
                            }
                            return ("/" + target.getFilePath().replace('\\', '/'))
                                    .endsWith("/" + imported.replace('.', '/') + ".java");
                        });
    }

    private void addDetectedEdges(
            GraphNode source,
            GraphNode target,
            String text,
            String lowerText,
            CodeSymbol sourceSymbol,
            boolean astManaged,
            Map<String, GraphEdge> edgesById) {
        String targetName = target.getName();
        if (targetName == null
                || targetName.isBlank()
                || !lowerText.contains(targetName.toLowerCase(Locale.ROOT))) {
            return;
        }
        if (isTypeLike(target) && containsInheritance(text, targetName, "implements")) {
            addEdge(
                    source,
                    target,
                    GraphRelation.IMPLEMENTS,
                    "implements " + targetName,
                    edgesById);
        }
        if (isTypeLike(target)
                && (containsInheritance(text, targetName, "extends")
                        || containsPythonBaseClass(sourceSymbol, targetName))) {
            addEdge(source, target, GraphRelation.EXTENDS, "extends " + targetName, edgesById);
        }
        boolean observedCall = !astManaged && isCallable(target) && containsCall(text, targetName);
        if (observedCall) {
            addEdge(source, target, GraphRelation.CALLS, targetName + "(...) call", edgesById);
        }
        if (containsWord(text, targetName)) {
            if (isTypeLike(target)) {
                addEdge(source, target, GraphRelation.USES, "type usage: " + targetName, edgesById);
            } else if (!observedCall && !astManaged) {
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
            for (GraphNode target :
                    resolveTargets(
                            source, text, nodesByName.getOrDefault(importedName, List.of()))) {
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
