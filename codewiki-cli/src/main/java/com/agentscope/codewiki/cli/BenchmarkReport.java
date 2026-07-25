package com.agentscope.codewiki.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Markdown benchmark report for profiling a real repository analysis run. */
public record BenchmarkReport(
        long repositorySizeBytes,
        long fileCount,
        Map<String, Long> languageDistribution,
        long parseTimeMillis,
        long graphBuildTimeMillis,
        long indexTimeMillis,
        long impactAnalysisTimeMillis,
        long reviewTimeMillis,
        long wikiGenerationTimeMillis,
        long peakMemoryBytes,
        long contextSizeBytes,
        long tokenUsage,
        long contextLimitTokens,
        long maxPromptTokens,
        long averagePromptTokens,
        long promptsOverContextLimit,
        long difyCalls,
        long reviewFindingsCount,
        List<FileMetric> largestFiles,
        List<SymbolMetric> mostConnectedSymbols,
        List<ModuleRiskMetric> highRiskModules) {

    public static final String DEFAULT_FILE_NAME = "benchmark-report.md";

    public BenchmarkReport {
        languageDistribution =
                languageDistribution == null ? Map.of() : Map.copyOf(languageDistribution);
        largestFiles = largestFiles == null ? List.of() : List.copyOf(largestFiles);
        mostConnectedSymbols =
                mostConnectedSymbols == null ? List.of() : List.copyOf(mostConnectedSymbols);
        highRiskModules = highRiskModules == null ? List.of() : List.copyOf(highRiskModules);
    }

    public Path writeMarkdown(Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        Path output = outputDirectory.resolve(DEFAULT_FILE_NAME);
        Files.writeString(output, toMarkdown());
        return output;
    }

    public String toMarkdown() {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Benchmark Report\n\n");
        appendMetric(markdown, "Repository Size", formatBytes(repositorySizeBytes));
        appendMetric(markdown, "File Count", Long.toString(fileCount));
        markdown.append("\n## Language Distribution\n\n");
        if (languageDistribution.isEmpty()) {
            markdown.append("- None\n");
        } else {
            languageDistribution.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByKey())
                    .forEach(
                            entry ->
                                    markdown.append("- ")
                                            .append(entry.getKey())
                                            .append(": ")
                                            .append(entry.getValue())
                                            .append('\n'));
        }
        markdown.append('\n');
        appendMetric(markdown, "Parse Time", parseTimeMillis + " ms");
        appendMetric(markdown, "Graph Build Time", graphBuildTimeMillis + " ms");
        appendMetric(markdown, "Index Time", indexTimeMillis + " ms");
        appendMetric(markdown, "Impact Analysis Time", impactAnalysisTimeMillis + " ms");
        appendMetric(markdown, "Review Time", reviewTimeMillis + " ms");
        appendMetric(markdown, "Wiki Generation Time", wikiGenerationTimeMillis + " ms");
        appendMetric(markdown, "Peak Memory", formatBytes(peakMemoryBytes));
        appendMetric(markdown, "Context Size", formatBytes(contextSizeBytes));
        appendMetric(markdown, "Token Usage", Long.toString(tokenUsage));
        appendMetric(markdown, "Dify Calls", Long.toString(difyCalls));
        appendMetric(markdown, "Review Findings Count", Long.toString(reviewFindingsCount));

        markdown.append("\n## 32k Context Metrics\n\n");
        appendMetric(markdown, "Context Limit Tokens", Long.toString(contextLimitTokens));
        appendMetric(markdown, "Max Prompt Tokens", Long.toString(maxPromptTokens));
        appendMetric(markdown, "Average Prompt Tokens", Long.toString(averagePromptTokens));
        appendMetric(
                markdown, "Prompts Over Context Limit", Long.toString(promptsOverContextLimit));

        markdown.append("\n## Top 10 Largest Files\n\n");
        appendLargestFiles(markdown);
        markdown.append("\n## Top 10 Most Connected Symbols\n\n");
        appendConnectedSymbols(markdown);
        markdown.append("\n## Top 10 High Risk Modules\n\n");
        appendHighRiskModules(markdown);
        return markdown.toString();
    }

    private void appendMetric(StringBuilder markdown, String label, String value) {
        markdown.append("- ").append(label).append(": ").append(value).append('\n');
    }

    private void appendLargestFiles(StringBuilder markdown) {
        if (largestFiles.isEmpty()) {
            markdown.append("- None\n");
            return;
        }
        largestFiles.forEach(
                file ->
                        markdown.append("- ")
                                .append(file.path())
                                .append(": ")
                                .append(formatBytes(file.sizeBytes()))
                                .append('\n'));
    }

    private void appendConnectedSymbols(StringBuilder markdown) {
        if (mostConnectedSymbols.isEmpty()) {
            markdown.append("- None\n");
            return;
        }
        mostConnectedSymbols.forEach(
                symbol ->
                        markdown.append("- ")
                                .append(symbol.name())
                                .append(" (")
                                .append(symbol.filePath())
                                .append("): ")
                                .append(symbol.connectionCount())
                                .append(" connection(s)\n"));
    }

    private void appendHighRiskModules(StringBuilder markdown) {
        if (highRiskModules.isEmpty()) {
            markdown.append("- None\n");
            return;
        }
        highRiskModules.forEach(
                module ->
                        markdown.append("- ")
                                .append(module.module())
                                .append(": riskScore=")
                                .append(module.riskScore())
                                .append(", symbols=")
                                .append(module.symbolCount())
                                .append(", edges=")
                                .append(module.edgeCount())
                                .append('\n'));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KiB", bytes / 1024.0);
        }
        return String.format("%.1f MiB", bytes / 1024.0 / 1024.0);
    }

    public record FileMetric(String path, long sizeBytes) {
        public FileMetric {
            path = path == null ? "" : path;
        }
    }

    public record SymbolMetric(String name, String filePath, long connectionCount) {
        public SymbolMetric {
            name = name == null ? "" : name;
            filePath = filePath == null ? "" : filePath;
        }
    }

    public record ModuleRiskMetric(
            String module, long riskScore, long symbolCount, long edgeCount) {
        public ModuleRiskMetric {
            module = module == null ? "" : module;
        }
    }
}
