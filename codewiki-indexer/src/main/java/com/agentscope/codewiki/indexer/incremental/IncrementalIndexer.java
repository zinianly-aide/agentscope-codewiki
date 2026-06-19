package com.agentscope.codewiki.indexer.incremental;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Executes repository indexing only for files whose fingerprints changed. */
public class IncrementalIndexer {

    /** Type of parse/summary/embedding work required for a changed file. */
    public enum ChangeType {
        ADDED,
        MODIFIED,
        RENAMED
    }

    /** Hook for concrete index stores that own symbols, summaries, embeddings, and graph nodes. */
    public interface ChangeProcessor {
        void indexChangedFile(Path repositoryRoot, FileFingerprint file, ChangeType changeType)
                throws IOException;

        void removeDeletedFile(String path) throws IOException;

        default void renameFile(
                Path repositoryRoot, String oldPath, FileFingerprint newFile, ChangeType changeType)
                throws IOException {
            removeDeletedFile(oldPath);
            indexChangedFile(repositoryRoot, newFile, changeType);
        }
    }

    private static final ChangeProcessor NOOP_PROCESSOR =
            new ChangeProcessor() {
                @Override
                public void indexChangedFile(
                        Path repositoryRoot, FileFingerprint file, ChangeType changeType) {}

                @Override
                public void removeDeletedFile(String path) {}
            };

    public IncrementalResult index(
            Path repositoryRoot, RepositoryFingerprint previous, Path outputDirectory)
            throws IOException {
        return index(repositoryRoot, previous, outputDirectory, NOOP_PROCESSOR);
    }

    public IncrementalResult index(
            Path repositoryRoot,
            RepositoryFingerprint previous,
            Path outputDirectory,
            ChangeProcessor processor)
            throws IOException {
        RepositoryFingerprint current = RepositoryFingerprint.scan(repositoryRoot);
        IncrementalReport report = compareAndProcess(repositoryRoot, previous, current, processor);
        report.writeJson(outputDirectory);
        current.writeJson(outputDirectory);
        return new IncrementalResult(current, report);
    }

    public IncrementalReport compareAndProcess(
            Path repositoryRoot,
            RepositoryFingerprint previous,
            RepositoryFingerprint current,
            ChangeProcessor processor)
            throws IOException {
        ChangeProcessor effectiveProcessor = processor == null ? NOOP_PROCESSOR : processor;
        Map<String, FileFingerprint> previousByPath =
                previous == null ? Map.of() : previous.byPath();
        Map<String, FileFingerprint> currentByPath = current.byPath();
        List<String> addedCandidates = new ArrayList<>();
        List<String> deletedCandidates = new ArrayList<>();
        IncrementalReport report = new IncrementalReport();

        for (FileFingerprint currentFile : current.getFiles()) {
            FileFingerprint previousFile = previousByPath.get(currentFile.getPath());
            if (previousFile == null) {
                addedCandidates.add(currentFile.getPath());
            } else if (currentFile.sameFingerprint(previousFile)) {
                report.addSkipped(currentFile.getPath());
            } else {
                report.addModified(currentFile.getPath());
                effectiveProcessor.indexChangedFile(
                        repositoryRoot, currentFile, ChangeType.MODIFIED);
            }
        }

        for (FileFingerprint previousFile : previousByPath.values()) {
            if (!currentByPath.containsKey(previousFile.getPath())) {
                deletedCandidates.add(previousFile.getPath());
            }
        }

        Map<String, FileFingerprint> remainingAdded = orderedFiles(currentByPath, addedCandidates);
        Map<String, FileFingerprint> remainingDeleted =
                orderedFiles(previousByPath, deletedCandidates);
        detectRenames(repositoryRoot, remainingDeleted, remainingAdded, report, effectiveProcessor);
        processAdded(repositoryRoot, remainingAdded, report, effectiveProcessor);
        processDeleted(remainingDeleted, report, effectiveProcessor);
        return report;
    }

    private void detectRenames(
            Path repositoryRoot,
            Map<String, FileFingerprint> deleted,
            Map<String, FileFingerprint> added,
            IncrementalReport report,
            ChangeProcessor processor)
            throws IOException {
        for (FileFingerprint oldFile : new ArrayList<>(deleted.values())) {
            Optional<FileFingerprint> newFile =
                    added.values().stream()
                            .filter(candidate -> candidate.sameContent(oldFile))
                            .min(Comparator.comparing(FileFingerprint::getPath));
            if (newFile.isPresent()) {
                FileFingerprint replacement = newFile.get();
                deleted.remove(oldFile.getPath());
                added.remove(replacement.getPath());
                report.addRenamed(oldFile.getPath(), replacement.getPath());
                processor.renameFile(
                        repositoryRoot, oldFile.getPath(), replacement, ChangeType.RENAMED);
            }
        }
    }

    private void processAdded(
            Path repositoryRoot,
            Map<String, FileFingerprint> added,
            IncrementalReport report,
            ChangeProcessor processor)
            throws IOException {
        for (FileFingerprint file : added.values()) {
            report.addAdded(file.getPath());
            processor.indexChangedFile(repositoryRoot, file, ChangeType.ADDED);
        }
    }

    private void processDeleted(
            Map<String, FileFingerprint> deleted,
            IncrementalReport report,
            ChangeProcessor processor)
            throws IOException {
        for (FileFingerprint file : deleted.values()) {
            report.addDeleted(file.getPath());
            processor.removeDeletedFile(file.getPath());
        }
    }

    private Map<String, FileFingerprint> orderedFiles(
            Map<String, FileFingerprint> source, List<String> paths) {
        Map<String, FileFingerprint> ordered = new LinkedHashMap<>();
        paths.stream().sorted().forEach(path -> ordered.put(path, source.get(path)));
        return ordered;
    }

    /** Result returned after writing report and current fingerprint artifacts. */
    public record IncrementalResult(RepositoryFingerprint fingerprint, IncrementalReport report) {}
}
