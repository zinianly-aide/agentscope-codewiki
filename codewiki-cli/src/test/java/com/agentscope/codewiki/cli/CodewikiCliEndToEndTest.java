package com.agentscope.codewiki.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end tests for the CodeWiki CLI analysis command. */
class CodewikiCliEndToEndTest {

    @TempDir private Path tempDir;

    @Test
    void analyzeCommandGeneratesWikiWithMockWorkflowAndLogs() throws Exception {
        Path repo = copyFixtureRepository();
        initGitRepository(repo);
        Path output = tempDir.resolve("wiki");
        Path log = tempDir.resolve("run.log");

        int exitCode =
                new CodewikiCli()
                        .execute(
                                new String[] {
                                    "analyze",
                                    "--repo",
                                    repo.toString(),
                                    "--output",
                                    output.toString(),
                                    "--mock",
                                    "--log",
                                    log.toString()
                                });

        assertEquals(0, exitCode);
        assertTrue(Files.exists(output.resolve("overview.md")));
        assertTrue(Files.exists(output.resolve("architecture.md")));
        assertTrue(Files.exists(output.resolve("modules.md")));
        assertTrue(Files.exists(output.resolve("dataflow.md")));
        assertTrue(Files.exists(output.resolve("review.md")));
        assertTrue(Files.exists(output.resolve(".codewiki-summary.json")));
        assertTrue(Files.readString(output.resolve("modules.md")).contains("HelloService"));
        assertTrue(Files.readString(log).contains("5. invoking AgentScope workflow"));
        assertTrue(Files.readString(log).contains("wiki pages written="));
    }

    @Test
    void dryRunWritesLogButDoesNotWriteWikiPages() throws Exception {
        Path repo = copyFixtureRepository();
        initGitRepository(repo);
        Path output = tempDir.resolve("dry-wiki");
        Path log = tempDir.resolve("dry-run.log");

        int exitCode =
                new CodewikiCli()
                        .execute(
                                new String[] {
                                    "analyze",
                                    "--repo",
                                    repo.toString(),
                                    "--output",
                                    output.toString(),
                                    "--dry-run",
                                    "--mock",
                                    "--log",
                                    log.toString()
                                });

        assertEquals(0, exitCode);
        assertFalse(Files.exists(output.resolve("overview.md")));
        assertTrue(Files.readString(log).contains("dry-run enabled"));
    }

    @Test
    void difyFailureFallsBackToMockAnalyzerWithoutFailingCli() throws Exception {
        Path repo = copyFixtureRepository();
        initGitRepository(repo);
        Path output = tempDir.resolve("dify-fallback-wiki");
        Path log = tempDir.resolve("dify-fallback.log");

        int exitCode =
                new CodewikiCli()
                        .execute(
                                new String[] {
                                    "analyze",
                                    "--repo",
                                    repo.toString(),
                                    "--output",
                                    output.toString(),
                                    "--dify",
                                    "--dify-base-url",
                                    "http://127.0.0.1:1",
                                    "--dify-api-key",
                                    "test-key",
                                    "--dify-timeout-seconds",
                                    "1",
                                    "--log",
                                    log.toString()
                                });

        assertEquals(0, exitCode);
        assertTrue(Files.exists(output.resolve("overview.md")));
        assertTrue(Files.readString(log).contains("Dify fallback reason:"));
    }

    private Path copyFixtureRepository() throws IOException {
        Path source = Path.of("src/test/resources/fixtures/small-repo");
        Path target = tempDir.resolve("small-repo");
        try (var paths = Files.walk(source)) {
            for (Path path : paths.sorted(Comparator.naturalOrder()).toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
        return target;
    }

    private void initGitRepository(Path repo) throws Exception {
        run(repo, "git", "init", "-b", "main");
        run(repo, "git", "config", "user.email", "codewiki@example.test");
        run(repo, "git", "config", "user.name", "CodeWiki Test");
        run(repo, "git", "add", ".");
        run(repo, "git", "commit", "-m", "fixture");
    }

    private void run(Path repo, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(repo.toFile()).start();
        int exitCode = process.waitFor();
        assertEquals(0, exitCode, String.join(" ", command));
    }
}
