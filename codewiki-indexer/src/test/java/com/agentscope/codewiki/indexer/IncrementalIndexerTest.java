package com.agentscope.codewiki.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentscope.codewiki.indexer.incremental.FileFingerprint;
import com.agentscope.codewiki.indexer.incremental.IncrementalIndexer;
import com.agentscope.codewiki.indexer.incremental.IncrementalReport;
import com.agentscope.codewiki.indexer.incremental.RepositoryFingerprint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IncrementalIndexerTest {

    @TempDir private Path tempDir;

    @Test
    void shouldReportAddedFilesAndRunParseSummaryEmbedding() throws IOException {
        Path repo = tempDir.resolve("repo-added");
        Files.createDirectories(repo);
        RepositoryFingerprint previous = RepositoryFingerprint.scan(repo);
        write(repo.resolve("src/NewService.java"), "class NewService {}", 1_000L);
        RecordingProcessor processor = new RecordingProcessor();

        IncrementalReport report =
                new IncrementalIndexer()
                        .compareAndProcess(
                                repo, previous, RepositoryFingerprint.scan(repo), processor);

        assertEquals(List.of("src/NewService.java"), report.getAdded());
        assertEquals(
                List.of(
                        "parse:src/NewService.java:ADDED",
                        "summary:src/NewService.java:ADDED",
                        "embedding:src/NewService.java:ADDED"),
                processor.operations);
    }

    @Test
    void shouldReportModifiedFilesAndRegenerateArtifacts() throws IOException {
        Path repo = tempDir.resolve("repo-modified");
        write(repo.resolve("src/Service.java"), "class Service {}", 1_000L);
        RepositoryFingerprint previous = RepositoryFingerprint.scan(repo);
        write(repo.resolve("src/Service.java"), "class Service { void run() {} }", 2_000L);
        RecordingProcessor processor = new RecordingProcessor();

        IncrementalReport report =
                new IncrementalIndexer()
                        .compareAndProcess(
                                repo, previous, RepositoryFingerprint.scan(repo), processor);

        assertEquals(List.of("src/Service.java"), report.getModified());
        assertEquals(
                List.of(
                        "parse:src/Service.java:MODIFIED",
                        "summary:src/Service.java:MODIFIED",
                        "embedding:src/Service.java:MODIFIED"),
                processor.operations);
    }

    @Test
    void shouldReportDeletedFilesAndCleanAllIndexes() throws IOException {
        Path repo = tempDir.resolve("repo-deleted");
        write(repo.resolve("src/OldService.java"), "class OldService {}", 1_000L);
        RepositoryFingerprint previous = RepositoryFingerprint.scan(repo);
        Files.delete(repo.resolve("src/OldService.java"));
        RecordingProcessor processor = new RecordingProcessor();

        IncrementalReport report =
                new IncrementalIndexer()
                        .compareAndProcess(
                                repo, previous, RepositoryFingerprint.scan(repo), processor);

        assertEquals(List.of("src/OldService.java"), report.getDeleted());
        assertEquals(
                List.of(
                        "symbol-delete:src/OldService.java",
                        "summary-delete:src/OldService.java",
                        "embedding-delete:src/OldService.java",
                        "graph-node-delete:src/OldService.java"),
                processor.operations);
    }

    @Test
    void shouldReportRenamedFilesAndUpdatePathSensitiveArtifacts() throws IOException {
        Path repo = tempDir.resolve("repo-renamed");
        write(repo.resolve("src/OldName.java"), "class Renamed {}", 1_000L);
        RepositoryFingerprint previous = RepositoryFingerprint.scan(repo);
        Files.move(repo.resolve("src/OldName.java"), repo.resolve("src/NewName.java"));
        touch(repo.resolve("src/NewName.java"), 2_000L);
        RecordingProcessor processor = new RecordingProcessor();

        IncrementalReport report =
                new IncrementalIndexer()
                        .compareAndProcess(
                                repo, previous, RepositoryFingerprint.scan(repo), processor);

        assertEquals(1, report.getRenamed().size());
        assertEquals("src/OldName.java", report.getRenamed().getFirst().getFrom());
        assertEquals("src/NewName.java", report.getRenamed().getFirst().getTo());
        assertEquals(
                List.of(
                        "rename:src/OldName.java->src/NewName.java",
                        "symbol-delete:src/OldName.java",
                        "summary-delete:src/OldName.java",
                        "embedding-delete:src/OldName.java",
                        "graph-node-delete:src/OldName.java",
                        "parse:src/NewName.java:RENAMED",
                        "summary:src/NewName.java:RENAMED",
                        "embedding:src/NewName.java:RENAMED"),
                processor.operations);
    }

    @Test
    void shouldSkipUnchangedFilesAndWriteIncrementalReportJson() throws IOException {
        Path repo = tempDir.resolve("repo-unchanged");
        Path output = tempDir.resolve("out-unchanged");
        write(repo.resolve("src/Stable.java"), "class Stable {}", 1_000L);
        RepositoryFingerprint previous = RepositoryFingerprint.scan(repo);
        RecordingProcessor processor = new RecordingProcessor();

        IncrementalIndexer.IncrementalResult result =
                new IncrementalIndexer().index(repo, previous, output, processor);
        String reportJson = Files.readString(output.resolve("incremental-report.json"));
        String fingerprintJson = Files.readString(output.resolve("repository-fingerprint.json"));

        assertEquals(List.of("src/Stable.java"), result.report().getSkipped());
        assertEquals(List.of(), processor.operations);
        assertTrue(reportJson.contains("\"added\" : [ ]"));
        assertTrue(reportJson.contains("\"modified\" : [ ]"));
        assertTrue(reportJson.contains("\"deleted\" : [ ]"));
        assertTrue(reportJson.contains("\"renamed\" : [ ]"));
        assertTrue(reportJson.contains("\"skipped\""));
        assertTrue(fingerprintJson.contains("\"path\" : \"src/Stable.java\""));
        assertTrue(fingerprintJson.contains("\"hash\""));
        assertTrue(fingerprintJson.contains("\"size\""));
        assertTrue(fingerprintJson.contains("\"modifiedTime\""));
    }

    private void write(Path path, String content, long modifiedTime) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        touch(path, modifiedTime);
    }

    private void touch(Path path, long modifiedTime) throws IOException {
        Files.setLastModifiedTime(path, FileTime.fromMillis(modifiedTime));
    }

    private static class RecordingProcessor implements IncrementalIndexer.ChangeProcessor {
        private final List<String> operations = new ArrayList<>();

        @Override
        public void indexChangedFile(
                Path repositoryRoot,
                FileFingerprint file,
                IncrementalIndexer.ChangeType changeType) {
            operations.add("parse:" + file.getPath() + ":" + changeType);
            operations.add("summary:" + file.getPath() + ":" + changeType);
            operations.add("embedding:" + file.getPath() + ":" + changeType);
        }

        @Override
        public void removeDeletedFile(String path) {
            operations.add("symbol-delete:" + path);
            operations.add("summary-delete:" + path);
            operations.add("embedding-delete:" + path);
            operations.add("graph-node-delete:" + path);
        }

        @Override
        public void renameFile(
                Path repositoryRoot,
                String oldPath,
                FileFingerprint newFile,
                IncrementalIndexer.ChangeType changeType)
                throws IOException {
            operations.add("rename:" + oldPath + "->" + newFile.getPath());
            IncrementalIndexer.ChangeProcessor.super.renameFile(
                    repositoryRoot, oldPath, newFile, changeType);
        }
    }
}
