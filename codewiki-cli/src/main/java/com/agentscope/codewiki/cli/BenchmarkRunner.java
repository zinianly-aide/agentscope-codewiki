package com.agentscope.codewiki.cli;

import com.agentscope.codewiki.agent.DiffReviewAgent;
import com.agentscope.codewiki.agent.DiffReviewReport;
import com.agentscope.codewiki.agent.RepositoryWikiGenerator;
import com.agentscope.codewiki.agent.WikiGenerationOptions;
import com.agentscope.codewiki.core.ClassSummary;
import com.agentscope.codewiki.core.FileSummary;
import com.agentscope.codewiki.core.ModuleSummary;
import com.agentscope.codewiki.core.RepositorySummary;
import com.agentscope.codewiki.indexer.graph.CodeGraph;
import com.agentscope.codewiki.indexer.graph.CodeGraphBuilder;
import com.agentscope.codewiki.indexer.graph.GraphEdge;
import com.agentscope.codewiki.indexer.graph.GraphNode;
import com.agentscope.codewiki.indexer.impact.ImpactAnalyzer;
import com.agentscope.codewiki.indexer.impact.ImpactReport;
import com.agentscope.codewiki.parser.CodeSymbol;
import com.agentscope.codewiki.parser.RepositoryMetadata;
import com.agentscope.codewiki.parser.RepositoryScanner;
import com.agentscope.codewiki.parser.TreeSitterSymbolExtractor;
import com.agentscope.codewiki.retriever.CodeDocument;
import com.agentscope.codewiki.retriever.ContextPack;
import com.agentscope.codewiki.retriever.HybridRetriever;
import com.agentscope.codewiki.retriever.RetrievalCorpus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Runs benchmark/profiling over a real repository without changing agent, graph, or retrieval code.
 */
public class BenchmarkRunner {

    private static final Pattern JAVA_TYPE_PATTERN =
            Pattern.compile("\\b(class|interface|enum|record)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern JAVA_METHOD_PATTERN =
            Pattern.compile(
                    "(?:public|protected|private|static|final|synchronized|native|abstract|\\s)+"
                            + "[\\w<>\\[\\], ?]+\\s+(\\w+)\\s*\\(");
    private static final Pattern PYTHON_SYMBOL_PATTERN =
            Pattern.compile("^\\s*(class|def)\\s+([A-Za-z_][A-Za-z0-9_]*)", Pattern.MULTILINE);

    public BenchmarkReport run(Path repositoryPath, Path outputDirectory) throws IOException {
        Path repo = repositoryPath.toAbsolutePath().normalize();
        long peakMemory = usedMemory();
        Files.createDirectories(outputDirectory);

        List<Path> files = listFiles(repo);
        long repositorySize = files.stream().mapToLong(this::size).sum();
        Map<String, Long> languages = languageDistribution(files);
        List<BenchmarkReport.FileMetric> largestFiles = largestFiles(repo, files);

        Timed<List<SourceFileAnalysis>> parse = timed(() -> analyzeSourceFiles(repo, files));
        peakMemory = Math.max(peakMemory, usedMemory());

        List<CodeSymbol> symbols =
                parse.value().stream().flatMap(file -> file.symbols().stream()).toList();
        Timed<CodeGraph> graph = timed(() -> new CodeGraphBuilder().build(symbols));
        peakMemory = Math.max(peakMemory, usedMemory());

        Timed<ContextPack> index = timed(() -> buildRetrievalContext(parse.value()));
        peakMemory = Math.max(peakMemory, usedMemory());

        List<String> changedFiles =
                parse.value().stream().map(SourceFileAnalysis::relativePath).toList();
        List<String> changedSymbols =
                symbols.stream().map(CodeSymbol::getName).filter(name -> name != null).toList();
        Timed<ImpactReport> impact =
                timed(
                        () ->
                                new ImpactAnalyzer(graph.value())
                                        .analyze(changedFiles, changedSymbols));
        peakMemory = Math.max(peakMemory, usedMemory());

        RetrievalCorpus corpus =
                RetrievalCorpus.of(
                        parse.value().stream().map(SourceFileAnalysis::toDocument).toList());
        Timed<DiffReviewReport> review =
                timed(
                        () ->
                                DiffReviewAgent.mockMode(
                                                corpus,
                                                AnalysisCommandOptions.DEFAULT_MAX_CONTEXT_TOKENS)
                                        .review("", changedFiles, impact.value()));
        peakMemory = Math.max(peakMemory, usedMemory());

        Timed<Void> wiki = timedVoid(() -> generateWiki(repo, parse.value(), outputDirectory));
        peakMemory = Math.max(peakMemory, usedMemory());

        BenchmarkReport report =
                new BenchmarkReport(
                        repositorySize,
                        files.size(),
                        languages,
                        parse.millis(),
                        graph.millis(),
                        index.millis(),
                        impact.millis(),
                        review.millis(),
                        wiki.millis(),
                        peakMemory,
                        renderContext(index.value()).getBytes(StandardCharsets.UTF_8).length,
                        estimateTokens(renderContext(index.value())),
                        0,
                        review.value().getFindings().size(),
                        largestFiles,
                        mostConnectedSymbols(graph.value()),
                        highRiskModules(graph.value()));
        report.writeMarkdown(outputDirectory);
        return report;
    }

    private List<SourceFileAnalysis> analyzeSourceFiles(Path repo, List<Path> files)
            throws IOException {
        TreeSitterSymbolExtractor extractor = createExtractor();
        List<SourceFileAnalysis> analyses = new ArrayList<>();
        for (Path file : files.stream().filter(this::isSupportedSource).sorted().toList()) {
            List<CodeSymbol> symbols = extractSymbols(repo, file, extractor);
            analyses.add(new SourceFileAnalysis(repo, file, symbols));
        }
        return analyses;
    }

    private TreeSitterSymbolExtractor createExtractor() {
        try {
            return new TreeSitterSymbolExtractor();
        } catch (IOException exception) {
            return null;
        }
    }

    private List<CodeSymbol> extractSymbols(
            Path repo, Path file, TreeSitterSymbolExtractor extractor) throws IOException {
        if (extractor != null) {
            try {
                return extractor.extract(file).stream()
                        .peek(symbol -> normalizeSymbol(repo, file, symbol))
                        .toList();
            } catch (IllegalArgumentException | IOException exception) {
                // Fall back to deterministic local parsing for benchmark robustness.
            }
        }
        return fallbackSymbols(repo, file);
    }

    private void normalizeSymbol(Path repo, Path file, CodeSymbol symbol) {
        if (symbol.getFilePath() == null || symbol.getFilePath().isBlank()) {
            symbol.setFilePath(repo.relativize(file).toString().replace('\\', '/'));
        }
        if (symbol.getSourceCode() == null) {
            symbol.setSourceCode(read(file));
        }
    }

    private List<CodeSymbol> fallbackSymbols(Path repo, Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        Pattern pattern =
                file.toString().endsWith(".py") ? PYTHON_SYMBOL_PATTERN : JAVA_TYPE_PATTERN;
        Matcher matcher = pattern.matcher(content);
        List<CodeSymbol> symbols = new ArrayList<>();
        while (matcher.find()) {
            symbols.add(
                    symbol(
                            repo,
                            file,
                            matcher.group(2),
                            matcher.group(1),
                            matcher.group(),
                            content,
                            matcher.start()));
        }
        if (file.toString().endsWith(".java")) {
            Matcher methodMatcher = JAVA_METHOD_PATTERN.matcher(content);
            while (methodMatcher.find()) {
                symbols.add(
                        symbol(
                                repo,
                                file,
                                methodMatcher.group(1),
                                "method",
                                methodMatcher.group(),
                                content,
                                methodMatcher.start()));
            }
        }
        return symbols;
    }

    private CodeSymbol symbol(
            Path repo,
            Path file,
            String name,
            String type,
            String signature,
            String sourceCode,
            int start) {
        CodeSymbol symbol = new CodeSymbol();
        symbol.setName(name);
        symbol.setType(type);
        symbol.setSignature(signature);
        symbol.setSourceCode(sourceCode);
        symbol.setFilePath(repo.relativize(file).toString().replace('\\', '/'));
        symbol.setStartLine(lineNumber(sourceCode, start));
        symbol.setEndLine(symbol.getStartLine());
        return symbol;
    }

    private ContextPack buildRetrievalContext(List<SourceFileAnalysis> sourceFiles) {
        RetrievalCorpus corpus =
                RetrievalCorpus.of(
                        sourceFiles.stream().map(SourceFileAnalysis::toDocument).toList());
        return new HybridRetriever(corpus).retrieve("benchmark repository context", 10);
    }

    private void generateWiki(Path repo, List<SourceFileAnalysis> sourceFiles, Path outputDirectory)
            throws IOException {
        RepositoryMetadata metadata = new RepositoryScanner().scan(repo);
        RepositorySummary summary = new RepositorySummary();
        summary.setName(metadata.getRepositoryName());
        summary.setBranch(metadata.getBranch());
        summary.setCommitId(metadata.getCommitId());
        summary.setLanguages(metadata.getLanguages());
        summary.setFileCount(metadata.getFileCount());
        summary.setSummary("Benchmark wiki generation summary.");
        ModuleSummary module = new ModuleSummary();
        module.setName(metadata.getRepositoryName());
        module.setPath(".");
        module.setFiles(sourceFiles.stream().map(SourceFileAnalysis::toFileSummary).toList());
        module.setSummary("Benchmark module summary.");
        summary.setModules(List.of(module));
        Path scratch = Files.createTempDirectory(outputDirectory, ".benchmark-wiki-");
        new RepositoryWikiGenerator()
                .generate(summary, scratch, WikiGenerationOptions.incremental());
        deleteDirectory(scratch);
    }

    private List<Path> listFiles(Path repo) throws IOException {
        try (Stream<Path> stream = Files.walk(repo)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> !isIgnored(repo, path))
                    .sorted()
                    .toList();
        }
    }

    private Map<String, Long> languageDistribution(List<Path> files) {
        Map<String, Long> languages = new LinkedHashMap<>();
        for (Path file : files) {
            languages.merge(language(file), 1L, Long::sum);
        }
        return languages;
    }

    private List<BenchmarkReport.FileMetric> largestFiles(Path repo, List<Path> files) {
        return files.stream()
                .map(file -> new BenchmarkReport.FileMetric(relative(repo, file), size(file)))
                .sorted(Comparator.comparingLong(BenchmarkReport.FileMetric::sizeBytes).reversed())
                .limit(10)
                .toList();
    }

    private List<BenchmarkReport.SymbolMetric> mostConnectedSymbols(CodeGraph graph) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (GraphEdge edge : graph.getEdges()) {
            counts.merge(edge.getSourceId(), 1L, Long::sum);
            counts.merge(edge.getTargetId(), 1L, Long::sum);
        }
        return graph.getNodes().stream()
                .map(
                        node ->
                                new BenchmarkReport.SymbolMetric(
                                        node.getName(),
                                        node.getFilePath(),
                                        counts.getOrDefault(node.getId(), 0L)))
                .sorted(
                        Comparator.comparingLong(BenchmarkReport.SymbolMetric::connectionCount)
                                .reversed())
                .limit(10)
                .toList();
    }

    private List<BenchmarkReport.ModuleRiskMetric> highRiskModules(CodeGraph graph) {
        Map<String, Long> symbolsByModule = new LinkedHashMap<>();
        Map<String, String> moduleByNode = new LinkedHashMap<>();
        for (GraphNode node : graph.getNodes()) {
            moduleByNode.put(node.getId(), node.getModule());
            symbolsByModule.merge(node.getModule(), 1L, Long::sum);
        }
        Map<String, Long> edgesByModule = new LinkedHashMap<>();
        for (GraphEdge edge : graph.getEdges()) {
            String module = moduleByNode.getOrDefault(edge.getSourceId(), "root");
            edgesByModule.merge(module, 1L, Long::sum);
        }
        return symbolsByModule.entrySet().stream()
                .map(
                        entry -> {
                            long edges = edgesByModule.getOrDefault(entry.getKey(), 0L);
                            return new BenchmarkReport.ModuleRiskMetric(
                                    entry.getKey(),
                                    entry.getValue() + edges * 2,
                                    entry.getValue(),
                                    edges);
                        })
                .sorted(
                        Comparator.comparingLong(BenchmarkReport.ModuleRiskMetric::riskScore)
                                .reversed())
                .limit(10)
                .toList();
    }

    private String renderContext(ContextPack contextPack) {
        StringBuilder builder = new StringBuilder();
        builder.append(contextPack.question()).append('\n');
        contextPack
                .relevantCode()
                .forEach(
                        code ->
                                builder.append(code.path())
                                        .append('\n')
                                        .append(code.content())
                                        .append('\n'));
        contextPack
                .relevantSummaries()
                .forEach(summary -> builder.append(summary.summary()).append('\n'));
        contextPack
                .relevantDependencyChains()
                .forEach(chain -> builder.append(chain.nodes()).append('\n'));
        contextPack
                .relevantModules()
                .forEach(module -> builder.append(module.module()).append('\n'));
        return builder.toString();
    }

    private long estimateTokens(String text) {
        String clean = text == null ? "" : text.strip();
        return clean.isEmpty() ? 0 : Math.max(1, (long) Math.ceil(clean.length() / 4.0));
    }

    private long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private <T> Timed<T> timed(CheckedSupplier<T> supplier) throws IOException {
        long start = System.nanoTime();
        T value = supplier.get();
        return new Timed<>(value, elapsedMillis(start));
    }

    private Timed<Void> timedVoid(CheckedRunnable runnable) throws IOException {
        long start = System.nanoTime();
        runnable.run();
        return new Timed<>(null, elapsedMillis(start));
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
    }

    private long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException exception) {
            return 0;
        }
    }

    private int lineNumber(String content, int index) {
        int line = 1;
        for (int position = 0; position < index; position++) {
            if (content.charAt(position) == '\n') {
                line++;
            }
        }
        return line;
    }

    private boolean isSupportedSource(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".java")
                || name.endsWith(".py")
                || name.endsWith(".ts")
                || name.endsWith(".tsx");
    }

    private String language(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".java")) {
            return "Java";
        }
        if (name.endsWith(".py")) {
            return "Python";
        }
        if (name.endsWith(".ts") || name.endsWith(".tsx")) {
            return "TypeScript";
        }
        if (name.endsWith(".md")) {
            return "Markdown";
        }
        return "Other";
    }

    private String relative(Path repo, Path file) {
        return repo.relativize(file).toString().replace('\\', '/');
    }

    private boolean isIgnored(Path repo, Path path) {
        Path relative = repo.relativize(path);
        for (Path part : relative) {
            String name = part.toString();
            if (name.equals(".git")
                    || name.equals("target")
                    || name.equals("build")
                    || name.equals("node_modules")) {
                return true;
            }
        }
        return false;
    }

    private String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "";
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record Timed<T>(T value, long millis) {
        private Timed {
            millis = Math.max(0, millis);
        }
    }

    private record SourceFileAnalysis(Path repo, Path file, List<CodeSymbol> symbols) {
        private String relativePath() {
            return repo.relativize(file).toString().replace('\\', '/');
        }

        private CodeDocument toDocument() {
            return CodeDocument.builder()
                    .id(relativePath())
                    .module(repo.getFileName().toString())
                    .path(relativePath())
                    .language(language(file))
                    .content(read(file))
                    .summary(
                            "Benchmark parsed symbols: "
                                    + symbols.stream().map(CodeSymbol::getName).toList())
                    .dependencies(List.of())
                    .build();
        }

        private FileSummary toFileSummary() {
            FileSummary summary = new FileSummary();
            summary.setName(file.getFileName().toString());
            summary.setPath(relativePath());
            summary.setLanguage(language(file));
            summary.setLinesOfCode(read(file).split("\\R", -1).length);
            summary.setClasses(symbols.stream().map(SourceFileAnalysis::toClassSummary).toList());
            summary.setSummary("Benchmark parsed " + symbols.size() + " symbols.");
            return summary;
        }

        private static ClassSummary toClassSummary(CodeSymbol symbol) {
            ClassSummary summary = new ClassSummary();
            summary.setName(symbol.getName());
            summary.setKind(symbol.getType());
            summary.setFilePath(symbol.getFilePath());
            summary.setStartLine(symbol.getStartLine());
            summary.setEndLine(symbol.getEndLine());
            summary.setSummary("Benchmark symbol " + symbol.getName() + ".");
            return summary;
        }

        private static String language(Path path) {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".java")) {
                return "Java";
            }
            if (name.endsWith(".py")) {
                return "Python";
            }
            if (name.endsWith(".ts") || name.endsWith(".tsx")) {
                return "TypeScript";
            }
            return "Other";
        }

        private static String read(Path file) {
            try {
                return Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                return "";
            }
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws IOException;
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws IOException;
    }
}
