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

class DiffReviewCliEndToEndTest {

    @TempDir private Path tempDir;

    @Test
    void noOpReviewDiffWritesZeroFindingReportAndSkipsDify() throws Exception {
        Path repo = copyFixtureRepository();
        initGitRepository(repo);
        Path output = tempDir.resolve("noop-review");

        int exitCode =
                new CodewikiCli()
                        .execute(
                                new String[] {
                                    "analyze",
                                    "--repo",
                                    repo.toString(),
                                    "--output",
                                    output.toString(),
                                    "--review-diff",
                                    "--base-ref",
                                    "HEAD",
                                    "--head-ref",
                                    "HEAD",
                                    "--dify"
                                });

        assertEquals(0, exitCode);
        String markdown = Files.readString(output.resolve("diff-review.md"));
        assertTrue(markdown.contains("No code changes detected."));
        assertTrue(markdown.contains("Risk level: LOW"));
        assertTrue(markdown.contains("Changed symbols: []"));
        assertFalse(markdown.contains("No changed files were supplied."));
        String impactJson = Files.readString(output.resolve("impact-report.json"));
        assertTrue(impactJson.contains("\"changedSymbols\" : [ ]"));
        assertTrue(impactJson.contains("\"riskLevel\" : " + "\"LOW\""));
    }

    @Test
    void changedSymbolsOnlyComeFromChangedFiles() throws Exception {
        Path repo = copyFixtureRepository();
        Files.writeString(
                repo.resolve("src/main/java/com/example/UnchangedOnly.java"),
                "package com.example;\npublic class UnchangedOnly { public void untouched() {} }\n");
        initGitRepository(repo);
        Files.writeString(
                repo.resolve("src/main/java/com/example/HelloService.java"),
                Files.readString(repo.resolve("src/main/java/com/example/HelloService.java"))
                        + "\nclass ReviewTarget { public boolean login(String token) { return token != null; } }\n");
        run(repo, "git", "add", ".");
        run(repo, "git", "commit", "-m", "change for diff review");
        Path output = tempDir.resolve("changed-symbols-review");

        int exitCode =
                new CodewikiCli()
                        .execute(
                                new String[] {
                                    "analyze",
                                    "--repo",
                                    repo.toString(),
                                    "--output",
                                    output.toString(),
                                    "--review-diff",
                                    "--base-ref",
                                    "HEAD~1",
                                    "--head-ref",
                                    "HEAD",
                                    "--mock"
                                });

        assertEquals(0, exitCode);
        String impactJson = Files.readString(output.resolve("impact-report.json"));
        assertFalse(impactJson.contains("UnchangedOnly"));
        assertFalse(impactJson.contains("untouched"));
        assertTrue(impactJson.contains("ReviewTarget") || impactJson.contains("HelloService"));
    }

    @Test
    void analyzeReviewDiffWritesDiffReviewMarkdown() throws Exception {
        Path repo = copyFixtureRepository();
        initGitRepository(repo);
        Files.writeString(
                repo.resolve("src/main/java/com/example/HelloService.java"),
                Files.readString(repo.resolve("src/main/java/com/example/HelloService.java"))
                        + "\nclass ReviewTarget { public boolean login(String token) { return token != null; } }\n");
        run(repo, "git", "add", ".");
        run(repo, "git", "commit", "-m", "change for diff review");
        Path output = tempDir.resolve("review");

        int exitCode =
                new CodewikiCli()
                        .execute(
                                new String[] {
                                    "analyze",
                                    "--repo",
                                    repo.toString(),
                                    "--output",
                                    output.toString(),
                                    "--review-diff",
                                    "--base-ref",
                                    "HEAD~1",
                                    "--head-ref",
                                    "HEAD",
                                    "--changed-only",
                                    "--mock"
                                });

        assertEquals(0, exitCode);
        Path report = output.resolve("diff-review.md");
        assertTrue(Files.exists(report));
        String markdown = Files.readString(report);
        assertTrue(markdown.contains("# Summary"));
        assertTrue(markdown.contains("# Changed Files"));
        assertTrue(markdown.contains("HelloService.java"));
        assertTrue(markdown.contains("ReviewTarget"));
        assertTrue(markdown.contains("login"));
        assertTrue(markdown.contains("Evidence:"));
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
