package com.agentscope.codewiki.cli;

import com.agentscope.codewiki.agent.DiffReviewAgent;
import com.agentscope.codewiki.agent.DiffReviewReport;
import com.agentscope.codewiki.dify.DifyChatModel;
import com.agentscope.codewiki.indexer.graph.CodeGraph;
import com.agentscope.codewiki.indexer.graph.CodeGraphBuilder;
import com.agentscope.codewiki.indexer.impact.ImpactAnalyzer;
import com.agentscope.codewiki.indexer.impact.ImpactReport;
import com.agentscope.codewiki.indexer.incremental.IncrementalReport;
import com.agentscope.codewiki.parser.CodeSymbol;
import com.agentscope.codewiki.parser.TreeSitterSymbolExtractor;
import com.agentscope.codewiki.retriever.CodeDocument;
import com.agentscope.codewiki.retriever.CodeDocumentChunker;
import com.agentscope.codewiki.retriever.RetrievalCorpus;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** CLI wiring for the existing AI Diff Review Engine. */
final class DiffReviewCliRunner {

    private static final Pattern JAVA_TYPE_PATTERN =
            Pattern.compile("\\b(class|interface|enum|record)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern JAVA_METHOD_PATTERN =
            Pattern.compile(
                    "(?:public|protected|private|static|final|synchronized|native|abstract|\\s)+"
                            + "[\\w<>\\[\\], ?]+\\s+(\\w+)\\s*\\(");
    private static final Pattern PYTHON_SYMBOL_PATTERN =
            Pattern.compile("^\\s*(class|def)\\s+([A-Za-z_][A-Za-z0-9_]*)", Pattern.MULTILINE);

    void run(AnalysisCommandOptions options) throws IOException {
        try {
            Files.createDirectories(options.outputDirectory());
            String diff = git(options.repoPath(), "diff", options.baseRef(), options.headRef());
            IncrementalReport incrementalReport = diffNameStatus(options);
            List<String> changedFiles = changedFiles(incrementalReport);
            if (changedFiles.isEmpty()) {
                DiffReviewAgent.noCodeChangesReport().writeMarkdown(options.outputDirectory());
                incrementalReport.writeJson(options.outputDirectory());
                new ImpactReport().writeJson(options.outputDirectory());
                return;
            }
            List<SourceFileAnalysis> analyses =
                    analyzeSourceFiles(options.repoPath(), changedFiles, options.changedOnly());
            List<SourceFileAnalysis> changedAnalyses = changedAnalyses(analyses, changedFiles);
            List<CodeSymbol> symbols =
                    analyses.stream().flatMap(file -> file.symbols().stream()).toList();
            List<String> changedSymbolNames =
                    changedAnalyses.stream()
                            .flatMap(file -> file.symbols().stream())
                            .map(CodeSymbol::getName)
                            .toList();
            CodeGraph graph = new CodeGraphBuilder().build(symbols);
            ImpactReport impactReport =
                    new ImpactAnalyzer(graph).analyze(incrementalReport, changedSymbolNames);
            DiffReviewAgent agent = createAgent(options, changedAnalyses);
            DiffReviewReport report = agent.review(diff, changedFiles, impactReport);
            report.writeMarkdown(options.outputDirectory());
            incrementalReport.writeJson(options.outputDirectory());
            impactReport.writeJson(options.outputDirectory());
        } catch (IOException | RuntimeException exception) {
            throw new IOException(
                    "Diff review failed for repo "
                            + options.repoPath()
                            + " between "
                            + options.baseRef()
                            + " and "
                            + options.headRef()
                            + ": "
                            + exception.getMessage(),
                    exception);
        }
    }

    private DiffReviewAgent createAgent(
            AnalysisCommandOptions options, List<SourceFileAnalysis> analyses) {
        RetrievalCorpus corpus =
                RetrievalCorpus.of(
                        CodeDocumentChunker.chunkAll(
                                analyses.stream().map(SourceFileAnalysis::toDocument).toList()));
        if (!options.difyEnabled()) {
            return DiffReviewAgent.mockMode(corpus, options.maxContextTokens());
        }
        if (options.difyBaseUrl() == null || options.difyApiKey() == null) {
            throw new IllegalArgumentException(
                    "Diff review Dify mode requires --dify-base-url and --dify-api-key or DIFY_BASE_URL/DIFY_API_KEY");
        }
        DifyChatModel model =
                new DifyChatModel(
                        options.difyBaseUrl(),
                        options.difyApiKey(),
                        options.difyUser(),
                        options.sessionId(),
                        Duration.ofSeconds(options.difyTimeoutSeconds()),
                        options.difyResponseMode());
        return DiffReviewAgent.difyMode(corpus, model, options.maxContextTokens());
    }

    private IncrementalReport diffNameStatus(AnalysisCommandOptions options) throws IOException {
        String status =
                git(
                        options.repoPath(),
                        "diff",
                        "--name-status",
                        options.baseRef(),
                        options.headRef());
        IncrementalReport report = new IncrementalReport();
        List<String> added = new ArrayList<>();
        List<String> modified = new ArrayList<>();
        List<String> deleted = new ArrayList<>();
        List<IncrementalReport.Rename> renamed = new ArrayList<>();
        for (String line : status.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\t");
            if (parts.length < 2) {
                continue;
            }
            String kind = parts[0];
            if (kind.startsWith("A")) {
                added.add(parts[1]);
            } else if (kind.startsWith("M")) {
                modified.add(parts[1]);
            } else if (kind.startsWith("D")) {
                deleted.add(parts[1]);
            } else if (kind.startsWith("R") && parts.length >= 3) {
                renamed.add(new IncrementalReport.Rename(parts[1], parts[2]));
            }
        }
        report.setAdded(added);
        report.setModified(modified);
        report.setDeleted(deleted);
        report.setRenamed(renamed);
        return report;
    }

    private List<String> changedFiles(IncrementalReport report) {
        Set<String> files = new LinkedHashSet<>();
        files.addAll(report.getAdded());
        files.addAll(report.getModified());
        report.getRenamed().forEach(rename -> files.add(rename.getTo()));
        return files.stream().sorted().toList();
    }

    private List<SourceFileAnalysis> changedAnalyses(
            List<SourceFileAnalysis> analyses, List<String> changedFiles) {
        Set<String> changed = new LinkedHashSet<>(changedFiles);
        return analyses.stream()
                .filter(analysis -> changed.contains(analysis.relativePath()))
                .toList();
    }

    private List<SourceFileAnalysis> analyzeSourceFiles(
            Path repo, List<String> changedFiles, boolean changedOnly) throws IOException {
        Set<String> changed = new LinkedHashSet<>(changedFiles);
        if (changedOnly && changed.isEmpty()) {
            return List.of();
        }
        TreeSitterSymbolExtractor extractor = createExtractor();
        List<SourceFileAnalysis> analyses = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(repo)) {
            for (Path file :
                    stream.filter(Files::isRegularFile)
                            .filter(path -> !isIgnored(repo, path))
                            .filter(this::isSupportedSource)
                            .sorted()
                            .toList()) {
                String relative = repo.relativize(file).toString().replace('\\', '/');
                if (changedOnly && !changed.isEmpty() && !changed.contains(relative)) {
                    continue;
                }
                analyses.add(
                        new SourceFileAnalysis(repo, file, extractSymbols(repo, file, extractor)));
            }
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
                // Fall back to deterministic local parsing so CLI review can still run.
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

    private String git(Path repo, String... args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(repo.toFile()).start();
        String stdout;
        String stderr;
        try (BufferedReader output =
                        new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader error =
                        new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            stdout = String.join("\n", output.lines().toList());
            stderr = String.join("\n", error.lines().toList());
        }
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException(
                        "git "
                                + String.join(" ", args)
                                + " failed with exit code "
                                + exitCode
                                + ": "
                                + stderr);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Git process interrupted", exception);
        }
        return stdout;
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
                            "Diff review parsed symbols: "
                                    + symbols.stream().map(CodeSymbol::getName).toList())
                    .dependencies(List.of())
                    .build();
        }

        private static String language(Path file) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".java")) {
                return "Java";
            }
            if (name.endsWith(".py")) {
                return "Python";
            }
            if (name.endsWith(".ts") || name.endsWith(".tsx")) {
                return "TypeScript";
            }
            return "unknown";
        }

        private static String read(Path file) {
            try {
                return Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                return "";
            }
        }
    }
}
