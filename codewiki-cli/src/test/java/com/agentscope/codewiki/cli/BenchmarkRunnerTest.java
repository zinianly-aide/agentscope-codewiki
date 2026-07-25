package com.agentscope.codewiki.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkRunnerTest {

    @TempDir private Path tempDir;

    @Test
    void benchmarkRunnerWritesReportForRepository() throws Exception {
        Path repo = createRepository();
        initGitRepository(repo);
        Path output = tempDir.resolve("benchmark");

        BenchmarkReport report = new BenchmarkRunner().run(repo, output);

        Path markdown = output.resolve("benchmark-report.md");
        assertTrue(Files.exists(markdown));
        assertEquals(markdown, report.writeMarkdown(output));
        String content = Files.readString(markdown);
        assertTrue(content.contains("# Benchmark Report"));
        assertTrue(content.contains("Repository Size"));
        assertTrue(content.contains("File Count"));
        assertTrue(content.contains("Language Distribution"));
        assertTrue(content.contains("Parse Time"));
        assertTrue(content.contains("Graph Build Time"));
        assertTrue(content.contains("Index Time"));
        assertTrue(content.contains("Impact Analysis Time"));
        assertTrue(content.contains("Review Time"));
        assertTrue(content.contains("Wiki Generation Time"));
        assertTrue(content.contains("Peak Memory"));
        assertTrue(content.contains("Context Size"));
        assertTrue(content.contains("Token Usage"));
        assertTrue(content.contains("Dify Calls"));
        assertTrue(content.contains("Review Findings Count"));
        assertTrue(content.contains("32k Context Metrics"));
        assertTrue(content.contains("Context Limit Tokens"));
        assertTrue(content.contains("Max Prompt Tokens"));
        assertEquals(
                AnalysisCommandOptions.DEFAULT_MAX_CONTEXT_TOKENS, report.contextLimitTokens());
        assertTrue(report.maxPromptTokens() <= report.contextLimitTokens());
        assertTrue(content.contains("Top 10 Largest Files"));
        assertTrue(content.contains("Top 10 Most Connected Symbols"));
        assertTrue(content.contains("Top 10 High Risk Modules"));
        assertTrue(content.contains("src/main/java/example/App.java"));
        assertTrue(report.fileCount() >= 2);
        assertTrue(report.repositorySizeBytes() > 0);
    }

    @Test
    void benchmarkUsesTrackedFilesInsteadOfIgnoredGeneratedArtifacts() throws Exception {
        Path repo = createRepository();
        Files.writeString(repo.resolve(".gitignore"), "wiki/\n");
        initGitRepository(repo);
        Files.createDirectories(repo.resolve("wiki/codewiki"));
        Files.writeString(repo.resolve("wiki/codewiki/generated.md"), "x".repeat(20_000));
        Path output = tempDir.resolve("tracked-benchmark");

        BenchmarkReport report = new BenchmarkRunner().run(repo, output);
        String content = Files.readString(output.resolve("benchmark-report.md"));

        assertEquals(4, report.fileCount());
        assertFalse(content.contains("wiki/codewiki/generated.md"));
    }

    @Test
    void benchmarkCliCommandWritesBenchmarkReport() throws Exception {
        Path repo = createRepository();
        initGitRepository(repo);
        Path output = tempDir.resolve("cli-benchmark");

        int exitCode =
                new CodewikiCli()
                        .execute(
                                new String[] {
                                    "benchmark",
                                    "--repo",
                                    repo.toString(),
                                    "--output",
                                    output.toString()
                                });

        assertEquals(0, exitCode);
        String content = Files.readString(output.resolve("benchmark-report.md"));
        assertTrue(content.contains("# Benchmark Report"));
        assertTrue(content.contains("Dify Calls"));
        assertTrue(content.contains("32k Context Metrics"));
    }

    private Path createRepository() throws IOException {
        Path repo = tempDir.resolve("repo");
        Path source = repo.resolve("src/main/java/example");
        Files.createDirectories(source);
        Files.writeString(
                source.resolve("App.java"),
                "package example;\n"
                        + "import example.Service;\n"
                        + "public class App {\n"
                        + "  private final Service service = new Service();\n"
                        + "  public String run() { return service.call(); }\n"
                        + "}\n");
        Files.writeString(
                source.resolve("Service.java"),
                "package example;\n"
                        + "public class Service {\n"
                        + "  public String call() { return \"ok\"; }\n"
                        + "}\n");
        Files.writeString(repo.resolve("README.md"), "# fixture\n");
        return repo;
    }

    private void initGitRepository(Path repo) throws Exception {
        run(repo, "git", "init", "-b", "main");
        run(repo, "git", "config", "user.email", "codewiki@example.test");
        run(repo, "git", "config", "user.name", "CodeWiki Test");
        run(repo, "git", "add", ".");
        run(repo, "git", "commit", "-m", "fixture");
    }

    private void run(Path repo, String... command) throws Exception {
        Process process = new ProcessBuilder(List.of(command)).directory(repo.toFile()).start();
        int exitCode = process.waitFor();
        assertEquals(0, exitCode, String.join(" ", command));
    }
}
