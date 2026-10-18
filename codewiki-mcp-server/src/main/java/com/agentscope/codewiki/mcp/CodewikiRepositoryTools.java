package com.agentscope.codewiki.mcp;

import com.agentscope.codewiki.indexer.graph.GraphNode;
import com.agentscope.codewiki.indexer.impact.ImpactAnalyzer;
import com.agentscope.codewiki.indexer.impact.ImpactReport;
import com.agentscope.codewiki.retriever.CodeDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final RepositorySnapshotIndex index;

    CodewikiRepositoryTools(String configuredRoot, boolean enabled) {
        this(configuredRoot, enabled, "", "");
    }

    @Autowired
    public CodewikiRepositoryTools(
            @Value("${codewiki.analysis.repository-root:}") String configuredRoot,
            @Value("${codewiki.analysis.enabled:false}") boolean enabled,
            @Value("${codewiki.analysis.tree-sitter-bridge:}") String configuredBridge,
            @Value("${codewiki.analysis.cache-file:}") String configuredCache) {
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
        Path diskCache = null;
        if (root != null && configuredCache != null && !configuredCache.isBlank()) {
            Path requested = Path.of(configuredCache).toAbsolutePath().normalize();
            if (Files.isSymbolicLink(requested)) {
                throw new IllegalArgumentException(
                        "analysis cache file must not be a symbolic link");
            }
            Path parent;
            try {
                parent = requested.getParent().toRealPath();
            } catch (IOException ex) {
                throw new IllegalArgumentException("analysis cache parent must exist", ex);
            }
            Path candidate = parent.resolve(requested.getFileName());
            // The cache contains extracted source code and must never be inside the indexed repo.
            if (candidate.startsWith(root)) {
                throw new IllegalArgumentException(
                        "analysis cache file must be outside repository root");
            }
            diskCache = candidate;
        }
        index = root == null ? null : new RepositorySnapshotIndex(root, bridgeScript, diskCache);
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
                        "codewiki_index_status",
                        "Report active source files, cached symbols and incremental parser work",
                        Map.of("type", "object", "properties", Map.of())),
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
                case "codewiki_index_status" ->
                        McpToolCallResponse.success(name, index.status(sourceFiles()));
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
        RepositorySnapshotIndex.Snapshot current = index.refresh(sourceFiles());
        var contexts = current.retriever().retrieve(query, 16).relevantCode();
        // Exact class names explicitly named in a question must take precedence over
        // semantically similar test cases, reports and imported references.
        java.util.LinkedHashMap<String, Map<String, Object>> matches =
                new java.util.LinkedHashMap<>();
        String queryLower = query.toLowerCase(Locale.ROOT);
        current.documents().stream()
                .filter(
                        document -> {
                            String path = document.getPath();
                            String baseName = path.substring(path.lastIndexOf('/') + 1);
                            int dot = baseName.lastIndexOf('.');
                            String name = dot < 0 ? baseName : baseName.substring(0, dot);
                            return name.length() >= 4
                                    && queryLower.contains(name.toLowerCase(Locale.ROOT));
                        })
                .sorted(
                        Comparator.comparingInt(
                                        (CodeDocument document) ->
                                                document.getPath().contains("/src/main/") ? 0 : 1)
                                .thenComparing(CodeDocument::getPath))
                .limit(5)
                .forEach(
                        document -> {
                            String path = document.getPath();
                            matches.put(
                                    path,
                                    evidence(
                                            path,
                                            document.getModule(),
                                            1000D,
                                            document.getContent(),
                                            query));
                        });
        contexts.stream()
                .sorted(
                        Comparator.comparingDouble(
                                        (com.agentscope.codewiki.retriever.CodeContext c) ->
                                                c.score()
                                                        + (c.path().contains("/src/main/")
                                                                ? 25D
                                                                : 0D))
                                .reversed())
                .forEach(
                        c -> {
                            if (matches.size() < 5) {
                                matches.putIfAbsent(
                                        c.path(),
                                        evidence(
                                                c.path(),
                                                c.module(),
                                                c.score(),
                                                c.content(),
                                                query));
                            }
                        });
        return McpToolCallResponse.success(
                "codewiki_search_code",
                Map.of(
                        "results",
                        List.copyOf(matches.values()),
                        "fileCount",
                        current.documents().size()));
    }

    private McpToolCallResponse<?> searchSymbol(Map<String, Object> args) throws IOException {
        String query = requiredText(args, "query").toLowerCase(Locale.ROOT);
        List<Map<String, Object>> matches =
                index.refresh(sourceFiles()).symbols().stream()
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
                new ImpactAnalyzer(index.refresh(sourceFiles()).graph())
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

    private static Map<String, Object> evidence(
            String path, String module, double score, String content, String query) {
        SnippetWindow window = relevantSnippet(content, query, 1100);
        return Map.of(
                "path",
                path,
                "module",
                module,
                "score",
                score,
                "snippet",
                window.text(),
                "startLine",
                window.startLine(),
                "endLine",
                window.endLine());
    }

    private record SnippetWindow(String text, int startLine, int endLine) {}

    /** Select a compact window near query terms, with verifiable source line numbers. */
    private static SnippetWindow relevantSnippet(String content, String query, int window) {
        if (content.length() <= window) {
            return new SnippetWindow(
                    content, 1, 1 + (int) content.chars().filter(ch -> ch == '\n').count());
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
        int lineStart = content.lastIndexOf('\n', bestStart);
        if (lineStart >= 0 && bestStart - lineStart <= 140) {
            bestStart = lineStart + 1;
        }
        int end = Math.min(content.length(), bestStart + window);
        String excerpt =
                (bestStart > 0 ? "...\n" : "")
                        + content.substring(bestStart, end)
                        + (end < content.length() ? "\n..." : "");
        int firstLine =
                1 + (int) content.substring(0, bestStart).chars().filter(ch -> ch == '\n').count();
        int lastLine = 1 + (int) content.substring(0, end).chars().filter(ch -> ch == '\n').count();
        return new SnippetWindow(excerpt, firstLine, lastLine);
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
