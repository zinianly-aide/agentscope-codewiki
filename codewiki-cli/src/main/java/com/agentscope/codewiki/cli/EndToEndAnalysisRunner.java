package com.agentscope.codewiki.cli;

import com.agentscope.codewiki.agent.RepositoryWikiGenerator;
import com.agentscope.codewiki.agent.WikiGenerationResult;
import com.agentscope.codewiki.core.ClassSummary;
import com.agentscope.codewiki.core.FileSummary;
import com.agentscope.codewiki.core.ModuleSummary;
import com.agentscope.codewiki.core.RepositorySummary;
import com.agentscope.codewiki.dify.DifyChatModel;
import com.agentscope.codewiki.parser.CodeSymbol;
import com.agentscope.codewiki.parser.RepositoryMetadata;
import com.agentscope.codewiki.parser.RepositoryScanner;
import com.agentscope.codewiki.parser.TreeSitterSymbolExtractor;
import com.agentscope.codewiki.retriever.CodeDocument;
import com.agentscope.codewiki.retriever.ContextPack;
import com.agentscope.codewiki.retriever.HybridRetriever;
import com.agentscope.codewiki.retriever.RetrievalCorpus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Runs the repository scan → retrieval → workflow → wiki generation acceptance pipeline. */
final class EndToEndAnalysisRunner {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Pattern JAVA_TYPE_PATTERN =
            Pattern.compile("\\b(class|interface|enum|record)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern PYTHON_TYPE_PATTERN =
            Pattern.compile("^\\s*(class|def)\\s+([A-Za-z_][A-Za-z0-9_]*)", Pattern.MULTILINE);

    void run(AnalysisCommandOptions options) throws IOException {
        RunLogger logger = new RunLogger();
        logger.step("1. scanning repository: " + options.repoPath());
        RepositoryMetadata metadata = new RepositoryScanner().scan(options.repoPath());

        logger.step("2. parsing code symbols");
        List<SourceFileAnalysis> sourceFiles = analyzeSourceFiles(options.repoPath(), logger);

        logger.step("3. generating or reading repository summary");
        RepositorySummary summary = loadOrCreateSummary(options, metadata, sourceFiles, logger);

        logger.step("4. building retrieval context");
        ContextPack contextPack = buildRetrievalContext(sourceFiles);
        ContextBudgetReport budgetReport =
                ContextBudgetReport.from(
                        summary, contextPack, options.maxContextTokens(), options.sessionId());
        logger.step(budgetReport.logLines());

        logger.step("5. invoking AgentScope workflow");
        String workflowResult = invokeWorkflow(budgetReport, options, logger);
        summary.setSummary(summary.getSummary() + " " + workflowResult);

        logger.step("6. generating wiki pages");
        if (options.dryRun()) {
            logger.step("dry-run enabled: wiki files and summary cache were not written");
        } else {
            Files.createDirectories(options.outputDirectory());
            saveSummary(options.outputDirectory(), summary);
            WikiGenerationResult result =
                    new RepositoryWikiGenerator()
                            .generate(
                                    summary,
                                    options.outputDirectory(),
                                    options.wikiGenerationOptions());
            logger.step(
                    "wiki pages written="
                            + result.writtenCount()
                            + ", skipped="
                            + result.skippedCount());
        }
        logger.write(options.logPath());
    }

    private List<SourceFileAnalysis> analyzeSourceFiles(Path repo, RunLogger logger)
            throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.walk(repo)) {
            files =
                    stream.filter(Files::isRegularFile)
                            .filter(path -> !isIgnored(repo, path))
                            .filter(this::isSupportedSource)
                            .sorted()
                            .toList();
        }
        TreeSitterSymbolExtractor extractor = createExtractor(logger);
        List<SourceFileAnalysis> analyses = new ArrayList<>();
        for (Path file : files) {
            List<CodeSymbol> symbols = extractSymbols(repo, file, extractor, logger);
            analyses.add(new SourceFileAnalysis(repo, file, symbols));
        }
        logger.step("parsed source files=" + analyses.size());
        return analyses;
    }

    private TreeSitterSymbolExtractor createExtractor(RunLogger logger) {
        try {
            return new TreeSitterSymbolExtractor();
        } catch (IOException exception) {
            logger.step(
                    "tree-sitter unavailable, using local fallback parser: "
                            + exception.getMessage());
            return null;
        }
    }

    private List<CodeSymbol> extractSymbols(
            Path repo, Path file, TreeSitterSymbolExtractor extractor, RunLogger logger)
            throws IOException {
        if (extractor != null) {
            try {
                return extractor.extract(file);
            } catch (IllegalArgumentException | IOException exception) {
                logger.step(
                        "tree-sitter failed for "
                                + repo.relativize(file)
                                + ", using fallback parser: "
                                + exception.getMessage());
            }
        }
        return fallbackSymbols(repo, file);
    }

    private RepositorySummary loadOrCreateSummary(
            AnalysisCommandOptions options,
            RepositoryMetadata metadata,
            List<SourceFileAnalysis> sourceFiles,
            RunLogger logger)
            throws IOException {
        Path cachePath = options.outputDirectory().resolve(".codewiki-summary.json");
        if (!options.dryRun() && Files.exists(cachePath)) {
            RepositorySummary cached =
                    MAPPER.readValue(cachePath.toFile(), RepositorySummary.class);
            if (metadata.getCommitId().equals(cached.getCommitId())) {
                logger.step("loaded summary cache: " + cachePath);
                return cached;
            }
            logger.step("summary cache commit differs; regenerating");
        }
        return createSummary(metadata, sourceFiles);
    }

    private RepositorySummary createSummary(
            RepositoryMetadata metadata, List<SourceFileAnalysis> sourceFiles) {
        RepositorySummary summary = new RepositorySummary();
        summary.setName(metadata.getRepositoryName());
        summary.setBranch(metadata.getBranch());
        summary.setCommitId(metadata.getCommitId());
        summary.setLanguages(metadata.getLanguages());
        summary.setFileCount(metadata.getFileCount());
        summary.setReadmePath(metadata.getReadmeLocation());
        summary.setBuildFiles(metadata.getBuildFiles());
        summary.setModules(List.of(createModule(metadata, sourceFiles)));
        summary.setSummary(
                "CodeWiki analyzed "
                        + metadata.getRepositoryName()
                        + " with "
                        + sourceFiles.size()
                        + " source files and "
                        + sourceFiles.stream().mapToInt(file -> file.symbols().size()).sum()
                        + " parsed symbols.");
        return summary;
    }

    private ModuleSummary createModule(
            RepositoryMetadata metadata, List<SourceFileAnalysis> sourceFiles) {
        ModuleSummary module = new ModuleSummary();
        module.setName(metadata.getRepositoryName());
        module.setPath(".");
        module.setBuildSystem(String.join(", ", metadata.getBuildFiles()));
        module.setFiles(sourceFiles.stream().map(SourceFileAnalysis::toFileSummary).toList());
        module.setSummary(
                "Repository module containing "
                        + sourceFiles.size()
                        + " supported source files for analysis.");
        return module;
    }

    private ContextPack buildRetrievalContext(List<SourceFileAnalysis> sourceFiles) {
        List<CodeDocument> documents =
                sourceFiles.stream().map(SourceFileAnalysis::toDocument).toList();
        return new HybridRetriever(RetrievalCorpus.of(documents))
                .retrieve(
                        "Generate repository wiki overview architecture modules dataflow review",
                        6);
    }

    private String invokeWorkflow(
            ContextBudgetReport budgetReport, AnalysisCommandOptions options, RunLogger logger) {
        if (options.mockOnly()) {
            return mockWorkflowResult(budgetReport, "mock mode enabled");
        }
        if (options.difyBaseUrl() == null || options.difyApiKey() == null) {
            String reason = "missing Dify base URL or API key";
            logger.step("Dify fallback reason: " + reason);
            return mockWorkflowResult(budgetReport, reason);
        }
        try {
            DifyChatModel model =
                    new DifyChatModel(
                            options.difyBaseUrl(),
                            options.difyApiKey(),
                            options.difyUser(),
                            options.sessionId(),
                            Duration.ofSeconds(options.difyTimeoutSeconds()));
            return model.chat(budgetReport.prompt(), budgetReport.asDifyInputs()).getAnswer();
        } catch (RuntimeException | IOException exception) {
            String reason = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            logger.step("Dify fallback reason: " + reason);
            return mockWorkflowResult(budgetReport, reason);
        }
    }

    private String mockWorkflowResult(ContextBudgetReport budgetReport, String reason) {
        return "AgentScope mock analyzer used (reason: "
                + reason
                + "). Budget total tokens="
                + budgetReport.totalTokens()
                + ", trimmed="
                + budgetReport.trimmed()
                + ".";
    }

    private void saveSummary(Path outputDirectory, RepositorySummary summary) throws IOException {
        MAPPER.writeValue(outputDirectory.resolve(".codewiki-summary.json").toFile(), summary);
    }

    private List<CodeSymbol> fallbackSymbols(Path repo, Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        Pattern pattern = file.toString().endsWith(".py") ? PYTHON_TYPE_PATTERN : JAVA_TYPE_PATTERN;
        Matcher matcher = pattern.matcher(content);
        List<CodeSymbol> symbols = new ArrayList<>();
        while (matcher.find()) {
            CodeSymbol symbol = new CodeSymbol();
            symbol.setType(matcher.group(1));
            symbol.setName(matcher.group(2));
            symbol.setFilePath(repo.relativize(file).toString());
            symbol.setStartLine(lineNumber(content, matcher.start()));
            symbol.setEndLine(symbol.getStartLine());
            symbol.setSignature(matcher.group());
            symbols.add(symbol);
        }
        return symbols;
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

    private record SourceFileAnalysis(Path repo, Path file, List<CodeSymbol> symbols) {

        FileSummary toFileSummary() {
            FileSummary summary = new FileSummary();
            Path relative = repo.relativize(file);
            summary.setName(file.getFileName().toString());
            summary.setPath(relative.toString());
            summary.setLanguage(language(file));
            summary.setLinesOfCode(countLines(file));
            summary.setClasses(symbols.stream().map(SourceFileAnalysis::toClassSummary).toList());
            summary.setSummary("Parsed " + symbols.size() + " symbols from " + relative + ".");
            return summary;
        }

        CodeDocument toDocument() {
            Path relative = repo.relativize(file);
            return CodeDocument.builder()
                    .id(relative.toString())
                    .module(repo.getFileName().toString())
                    .path(relative.toString())
                    .language(language(file))
                    .content(annotatedContent(relative, symbols, read(file)))
                    .summary("Parsed symbols: " + symbolNames())
                    .dependencies(List.of())
                    .build();
        }

        private String symbolNames() {
            return symbols.stream()
                    .map(CodeSymbol::getName)
                    .sorted(Comparator.naturalOrder())
                    .toList()
                    .toString();
        }

        private static ClassSummary toClassSummary(CodeSymbol symbol) {
            ClassSummary summary = new ClassSummary();
            summary.setName(symbol.getName());
            summary.setKind(symbol.getType());
            summary.setFilePath(symbol.getFilePath());
            summary.setStartLine(symbol.getStartLine());
            summary.setEndLine(symbol.getEndLine());
            summary.setSummary(
                    "Parsed symbol " + symbol.getName() + " of type " + symbol.getType() + ".");
            return summary;
        }

        private static String annotatedContent(
                Path relative, List<CodeSymbol> symbols, String content) {
            StringBuilder builder = new StringBuilder();
            builder.append("file=").append(relative).append(System.lineSeparator());
            for (CodeSymbol symbol : symbols) {
                builder.append("symbol=")
                        .append(symbol.getName())
                        .append(" lines=")
                        .append(symbol.getStartLine())
                        .append("-")
                        .append(symbol.getEndLine())
                        .append(System.lineSeparator());
            }
            builder.append(content);
            return builder.toString();
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

        private static int countLines(Path file) {
            return read(file).split("\\R", -1).length;
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
