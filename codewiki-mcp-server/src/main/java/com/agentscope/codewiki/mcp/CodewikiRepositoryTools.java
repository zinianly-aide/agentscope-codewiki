package com.agentscope.codewiki.mcp;

import com.agentscope.codewiki.indexer.graph.CodeGraph;
import com.agentscope.codewiki.indexer.graph.CodeGraphBuilder;
import com.agentscope.codewiki.indexer.graph.GraphNode;
import com.agentscope.codewiki.indexer.impact.ImpactAnalyzer;
import com.agentscope.codewiki.indexer.impact.ImpactReport;
import com.agentscope.codewiki.parser.CodeSymbol;
import com.agentscope.codewiki.parser.TreeSitterSymbolExtractor;
import com.agentscope.codewiki.retriever.CodeDocument;
import com.agentscope.codewiki.retriever.CodeDocumentChunker;
import com.agentscope.codewiki.retriever.HybridRetriever;
import com.agentscope.codewiki.retriever.RetrievalCorpus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Read-only adapter for a single explicitly configured repository. */
@Service
public class CodewikiRepositoryTools {
    private static final int MAX_FILES = 800;
    private static final int MAX_FILE_BYTES = 128 * 1024;
    private static final int MAX_TOTAL_BYTES = 4 * 1024 * 1024;
    private static final int MAX_SYMBOLS = 3500;
    private static final Set<String> EXTENSIONS = Set.of(".java", ".py", ".ts", ".tsx");
    private static final Set<String> EXCLUDED =
            Set.of(".git", ".idea", "node_modules", "target", "build", "dist", ".venv", "venv");
    private final Path root;
    private final Path bridgeScript;

    CodewikiRepositoryTools(String configuredRoot, boolean enabled) {
        this(configuredRoot, enabled, "");
    }

    @Autowired
    public CodewikiRepositoryTools(
            @Value("${codewiki.analysis.repository-root:}") String configuredRoot,
            @Value("${codewiki.analysis.enabled:false}") boolean enabled,
            @Value("${codewiki.analysis.tree-sitter-bridge:}") String configuredBridge) {
        Path resolved = null;
        if (enabled && configuredRoot != null && !configuredRoot.isBlank()) {
            try {
                Path candidate = Path.of(configuredRoot).toRealPath();
                if (Files.isDirectory(candidate)) {
                    resolved = candidate;
                }
            } catch (IOException | IllegalArgumentException ignored) {
                // Fail closed on invalid configuration.
            }
        }
        root = resolved;
        bridgeScript =
                configuredBridge == null || configuredBridge.isBlank()
                        ? null
                        : Path.of(configuredBridge).toAbsolutePath().normalize();
    }

    public List<McpToolDescriptor> descriptors() {
        if (root == null) {
            return List.of();
        }
        return List.of(
                new McpToolDescriptor(
                        "codewiki_search_code",
                        "Search relevant code snippets in the configured repository",
                        schema("query")),
                new McpToolDescriptor(
                        "codewiki_search_symbol",
                        "Find symbols and source locations",
                        schema("query")),
                new McpToolDescriptor(
                        "codewiki_analyze_impact",
                        "Find downstream dependents of a changed file or symbol",
                        Map.of(
                                "type",
                                "object",
                                "properties",
                                Map.of(
                                        "file", Map.of("type", "string"),
                                        "symbol", Map.of("type", "string")))));
    }

    private static Map<String, Object> schema(String required) {
        return Map.of(
                "type",
                "object",
                "properties",
                Map.of(required, Map.of("type", "string")),
                "required",
                List.of(required));
    }

    public McpToolCallResponse<?> call(String name, Map<String, Object> args) {
        if (root == null) {
            return McpToolCallResponse.failure(name, "TOOL_NOT_FOUND", "unknown MCP tool");
        }
        Map<String, Object> input = args == null ? Map.of() : args;
        try {
            return switch (name) {
                case "codewiki_search_code" -> searchCode(input);
                case "codewiki_search_symbol" -> searchSymbol(input);
                case "codewiki_analyze_impact" -> analyzeImpact(input);
                default -> McpToolCallResponse.failure(name, "TOOL_NOT_FOUND", "unknown MCP tool");
            };
        } catch (IllegalArgumentException ex) {
            return McpToolCallResponse.failure(name, "INVALID_ARGUMENTS", ex.getMessage());
        } catch (RepositoryLimitException ex) {
            return McpToolCallResponse.failure(name, "REPOSITORY_LIMIT", ex.getMessage());
        } catch (IOException ex) {
            return McpToolCallResponse.failure(
                    name, "REPOSITORY_READ_FAILED", "unable to read source");
        } catch (RuntimeException ex) {
            return McpToolCallResponse.failure(
                    name, "ANALYSIS_FAILED", "repository analysis failed");
        }
    }

    private McpToolCallResponse<?> searchCode(Map<String, Object> args) throws IOException {
        String query = requiredText(args, "query");
        List<CodeDocument> documents = new ArrayList<>();
        for (Path file : sourceFiles()) {
            String path = relative(file);
            String module = path.contains("/") ? path.substring(0, path.indexOf('/')) : "root";
            documents.add(
                    CodeDocument.builder()
                            .id(path)
                            .module(module)
                            .path(path)
                            .language(extension(file))
                            .content(Files.readString(file, StandardCharsets.UTF_8))
                            .summary("")
                            .dependencies(List.of())
                            .build());
        }
        var contexts =
                new HybridRetriever(RetrievalCorpus.of(CodeDocumentChunker.chunkAll(documents)))
                        .retrieve(query, 5)
                        .relevantCode();
        // Preserve hybrid scores, but prefer implementation source over examples
        // when the question matches both (especially for self-analysis benchmarks).
        List<Map<String, Object>> matches =
                contexts.stream()
                        .sorted(
                                Comparator.comparingDouble(
                                                (com.agentscope.codewiki.retriever.CodeContext c) ->
                                                        c.score()
                                                                + (c.path().contains("/src/main/")
                                                                        ? 25D
                                                                        : 0D))
                                        .reversed())
                        .limit(5)
                        .map(
                                c ->
                                        Map.<String, Object>of(
                                                "path",
                                                c.path(),
                                                "module",
                                                c.module(),
                                                "score",
                                                c.score(),
                                                "snippet",
                                                relevantSnippet(c.content(), query, 1100)))
                        .toList();
        return McpToolCallResponse.success(
                "codewiki_search_code", Map.of("results", matches, "fileCount", documents.size()));
    }

    private McpToolCallResponse<?> searchSymbol(Map<String, Object> args) throws IOException {
        String query = requiredText(args, "query").toLowerCase(Locale.ROOT);
        List<Map<String, Object>> matches =
                symbols().stream()
                        .map(GraphNode::fromSymbol)
                        .filter(
                                n ->
                                        n.getName() != null
                                                && n.getName()
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(query))
                        .sorted(
                                Comparator.comparing(GraphNode::getFilePath)
                                        .thenComparingInt(GraphNode::getStartLine))
                        .limit(20)
                        .map(
                                n ->
                                        Map.<String, Object>of(
                                                "id",
                                                n.getId(),
                                                "name",
                                                n.getName(),
                                                "type",
                                                nullToEmpty(n.getType()),
                                                "path",
                                                n.getFilePath(),
                                                "startLine",
                                                n.getStartLine(),
                                                "endLine",
                                                n.getEndLine()))
                        .toList();
        return McpToolCallResponse.success("codewiki_search_symbol", Map.of("results", matches));
    }

    private McpToolCallResponse<?> analyzeImpact(Map<String, Object> args) throws IOException {
        String file = optionalText(args, "file");
        String symbol = optionalText(args, "symbol");
        if (file.isBlank() && symbol.isBlank()) {
            throw new IllegalArgumentException("file or symbol is required");
        }
        if (!file.isBlank()
                && (Path.of(file).isAbsolute()
                        || file.replace('\\', '/').startsWith("../")
                        || file.contains("..\\")
                        || file.contains("/../"))) {
            throw new IllegalArgumentException("file must be repository-relative");
        }
        ImpactReport report =
                new ImpactAnalyzer(graph())
                        .analyze(
                                file.isBlank() ? List.of() : List.of(file.replace('\\', '/')),
                                symbol.isBlank() ? List.of() : List.of(symbol));
        return McpToolCallResponse.success(
                "codewiki_analyze_impact",
                Map.of(
                        "riskLevel",
                        report.getRiskLevel(),
                        "affectedModules",
                        report.getAffectedModules(),
                        "directImpacts",
                        report.getDirectImpacts().stream().limit(40).toList(),
                        "transitiveImpacts",
                        report.getTransitiveImpacts().stream().limit(40).toList(),
                        "directTotal",
                        report.getDirectImpacts().size(),
                        "transitiveTotal",
                        report.getTransitiveImpacts().size()));
    }

    private CodeGraph graph() throws IOException {
        return new CodeGraphBuilder().build(symbols());
    }

    private List<CodeSymbol> symbols() throws IOException {
        // Exploded/test resources work by default. Fat JAR needs an external bridge path.
        TreeSitterSymbolExtractor extractor =
                bridgeScript == null
                        ? new TreeSitterSymbolExtractor()
                        : new TreeSitterSymbolExtractor(bridgeScript);
        List<CodeSymbol> symbols = extractor.extractBatch(sourceFiles());
        if (symbols.size() > MAX_SYMBOLS) {
            throw new RepositoryLimitException("symbol limit exceeded; narrow repository scope");
        }
        for (CodeSymbol symbol : symbols) {
            Path source = Path.of(symbol.getFilePath()).normalize();
            if (!source.startsWith(root)) {
                throw new IOException("bridge returned an out-of-scope source");
            }
            symbol.setFilePath(relative(source));
        }
        return symbols;
    }

    private List<Path> sourceFiles() throws IOException {
        // Walk never follows directory links; explicit exclusion also covers linked files.
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> files =
                    paths.filter(p -> !Files.isSymbolicLink(p))
                            .filter(Files::isRegularFile)
                            .filter(p -> allowedRelative(p) && EXTENSIONS.contains(extension(p)))
                            .sorted()
                            .toList();
            if (files.size() > MAX_FILES) {
                throw new RepositoryLimitException("file limit exceeded; narrow repository scope");
            }
            long total = 0;
            for (Path file : files) {
                long size = Files.size(file);
                if (size > MAX_FILE_BYTES) {
                    throw new RepositoryLimitException("source file size limit exceeded");
                }
                total += size;
                if (total > MAX_TOTAL_BYTES) {
                    throw new RepositoryLimitException("repository size limit exceeded");
                }
            }
            return files;
        }
    }

    private boolean allowedRelative(Path file) {
        for (Path component : root.relativize(file)) {
            String name = component.toString();
            if (name.startsWith(".") || EXCLUDED.contains(name)) {
                return false;
            }
        }
        return true;
    }

    private String relative(Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }

    private static String requiredText(Map<String, Object> args, String key) {
        String text = optionalText(args, key);
        if (text.isBlank()) {
            throw new IllegalArgumentException(key + " must be a nonempty string");
        }
        return text;
    }

    private static String optionalText(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return "";
        }
        if (!(value instanceof String text) || text.length() > 256) {
            throw new IllegalArgumentException(key + " must be a string of at most 256 characters");
        }
        return text.strip();
    }

    /** Selects a compact evidence window containing query identifiers instead of only imports. */
    private static String relevantSnippet(String content, String query, int window) {
        if (content.length() <= window) {
            return content;
        }
        List<String> terms =
                java.util.Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+"))
                        .filter(term -> term.length() >= 5)
                        .filter(
                                term ->
                                        !Set.of(
                                                        "which",
                                                        "where",
                                                        "there",
                                                        "class",
                                                        "source",
                                                        "repository",
                                                        "these",
                                                        "would",
                                                        "about",
                                                        "their",
                                                        "does",
                                                        "direct",
                                                        "java")
                                                .contains(term))
                        .distinct()
                        .toList();
        String lower = content.toLowerCase(Locale.ROOT);
        int bestStart = 0;
        int bestScore = -1;
        int stride = Math.max(100, window / 4);
        for (int start = 0; start < content.length(); start += stride) {
            int end = Math.min(content.length(), start + window);
            int score = 0;
            for (String term : terms) {
                int at = lower.indexOf(term, start);
                while (at >= start && at < end) {
                    score++;
                    at = lower.indexOf(term, at + term.length());
                }
            }
            if (score > bestScore) {
                bestStart = start;
                bestScore = score;
            }
        }
        return (bestStart > 0 ? "...\n" : "")
                + content.substring(bestStart, Math.min(content.length(), bestStart + window))
                + (bestStart + window < content.length() ? "\n..." : "");
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private static class RepositoryLimitException extends RuntimeException {
        RepositoryLimitException(String message) {
            super(message);
        }
    }
}
