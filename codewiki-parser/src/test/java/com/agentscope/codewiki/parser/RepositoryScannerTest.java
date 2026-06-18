package com.agentscope.codewiki.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryScannerTest {

    @TempDir Path tempDir;

    private void initGitRepo(Path dir) throws Exception {
        runCmd(dir, "git", "init");
        runCmd(dir, "git", "config", "user.email", "test@test.com");
        runCmd(dir, "git", "config", "user.name", "Test");
    }

    private void runCmd(Path dir, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile());
        Process p = pb.start();
        int exit = p.waitFor();
        if (exit != 0) {
            throw new RuntimeException("Command failed: " + String.join(" ", cmd));
        }
    }

    @Test
    void shouldRejectNonGitDirectory() {
        assertThrows(IllegalArgumentException.class, () -> new RepositoryScanner().scan(tempDir));
    }

    @Test
    void shouldRejectNonDirectory() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.createFile(file);
        assertThrows(IllegalArgumentException.class, () -> new RepositoryScanner().scan(file));
    }

    @Test
    void shouldScanEmptyGitRepo() throws Exception {
        initGitRepo(tempDir);
        // initial commit so rev-parse HEAD works
        Files.createFile(tempDir.resolve("dummy"));
        runCmd(tempDir, "git", "add", ".");
        runCmd(tempDir, "git", "commit", "-m", "init");

        RepositoryMetadata meta = new RepositoryScanner().scan(tempDir);

        assertNotNull(meta.getRepositoryName());
        assertTrue(meta.getRepositoryName().length() > 0);
        assertEquals("main", meta.getBranch());
        assertNotNull(meta.getCommitId());
        assertEquals(40, meta.getCommitId().length(), "commit hash should be 40 hex chars");
        assertTrue(meta.getLanguages().isEmpty());
        assertEquals(1, meta.getFileCount());
        assertNotNull(meta.getDirectoryTree());
        assertTrue(meta.getBuildFiles().isEmpty());
    }

    @Test
    void shouldDetectLanguages() throws Exception {
        initGitRepo(tempDir);

        Path src = tempDir.resolve("src");
        Files.createDirectory(src);
        Files.createFile(src.resolve("Main.java"));
        Files.createFile(src.resolve("Utils.kt"));
        Files.createFile(tempDir.resolve("script.py"));
        Files.createFile(tempDir.resolve("data.json"));

        runCmd(tempDir, "git", "add", ".");
        runCmd(tempDir, "git", "commit", "-m", "add sources");

        RepositoryMetadata meta = new RepositoryScanner().scan(tempDir);

        assertTrue(meta.getLanguages().contains("Java"));
        assertTrue(meta.getLanguages().contains("Kotlin"));
        assertTrue(meta.getLanguages().contains("Python"));
        assertTrue(meta.getLanguages().contains("JSON"));
        assertEquals(4, meta.getFileCount());
    }

    @Test
    void shouldFindReadmeAndBuildFiles() throws Exception {
        initGitRepo(tempDir);

        Files.createFile(tempDir.resolve("README.md"));
        Files.createFile(tempDir.resolve("pom.xml"));
        Files.createFile(tempDir.resolve("package.json"));

        runCmd(tempDir, "git", "add", ".");
        runCmd(tempDir, "git", "commit", "-m", "add build files");

        RepositoryMetadata meta = new RepositoryScanner().scan(tempDir);

        assertEquals("README.md", meta.getReadmeLocation());
        assertEquals(2, meta.getBuildFiles().size());
        assertTrue(meta.getBuildFiles().contains("pom.xml"));
        assertTrue(meta.getBuildFiles().contains("package.json"));
    }

    @Test
    void shouldExcludeIgnoredDirectoriesFromFileCount() throws Exception {
        initGitRepo(tempDir);

        Files.createFile(tempDir.resolve("README.md"));
        Path target = tempDir.resolve("target");
        Files.createDirectory(target);
        Files.createFile(target.resolve("app.jar"));

        runCmd(tempDir, "git", "add", ".");
        runCmd(tempDir, "git", "commit", "-m", "with target");

        RepositoryMetadata meta = new RepositoryScanner().scan(tempDir);

        // README.md is counted; target/app.jar is excluded
        assertEquals(1, meta.getFileCount());
    }

    @Test
    void shouldProduceJsonOutput() throws Exception {
        initGitRepo(tempDir);
        Files.createFile(tempDir.resolve("README.md"));
        runCmd(tempDir, "git", "add", ".");
        runCmd(tempDir, "git", "commit", "-m", "init");

        RepositoryMetadata meta = new RepositoryScanner().scan(tempDir);
        String json = meta.toJson();

        assertNotNull(json);
        assertTrue(json.contains("repositoryName"));
        assertTrue(json.contains("branch"));
        assertTrue(json.contains("commitId"));
        assertTrue(json.contains("directoryTree"));
        assertTrue(json.contains("README.md"));
    }
}
