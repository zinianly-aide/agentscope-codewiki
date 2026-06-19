package com.agentscope.codewiki.indexer.incremental;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** JSON report for an incremental indexing run. */
public class IncrementalReport {

    public static final String DEFAULT_FILE_NAME = "incremental-report.json";

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private List<String> added = new ArrayList<>();
    private List<String> modified = new ArrayList<>();
    private List<String> deleted = new ArrayList<>();
    private List<Rename> renamed = new ArrayList<>();
    private List<String> skipped = new ArrayList<>();

    public List<String> getAdded() {
        return List.copyOf(added);
    }

    public void setAdded(List<String> added) {
        this.added = added == null ? new ArrayList<>() : new ArrayList<>(added);
    }

    public List<String> getModified() {
        return List.copyOf(modified);
    }

    public void setModified(List<String> modified) {
        this.modified = modified == null ? new ArrayList<>() : new ArrayList<>(modified);
    }

    public List<String> getDeleted() {
        return List.copyOf(deleted);
    }

    public void setDeleted(List<String> deleted) {
        this.deleted = deleted == null ? new ArrayList<>() : new ArrayList<>(deleted);
    }

    public List<Rename> getRenamed() {
        return List.copyOf(renamed);
    }

    public void setRenamed(List<Rename> renamed) {
        this.renamed = renamed == null ? new ArrayList<>() : new ArrayList<>(renamed);
    }

    public List<String> getSkipped() {
        return List.copyOf(skipped);
    }

    public void setSkipped(List<String> skipped) {
        this.skipped = skipped == null ? new ArrayList<>() : new ArrayList<>(skipped);
    }

    void addAdded(String path) {
        added.add(path);
    }

    void addModified(String path) {
        modified.add(path);
    }

    void addDeleted(String path) {
        deleted.add(path);
    }

    void addRenamed(String oldPath, String newPath) {
        renamed.add(new Rename(oldPath, newPath));
    }

    void addSkipped(String path) {
        skipped.add(path);
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize incremental report", e);
        }
    }

    public void writeJson(Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve(DEFAULT_FILE_NAME), toJson());
    }

    /** Describes a file path change with identical content. */
    public static class Rename {
        private String from;
        private String to;

        public Rename() {}

        public Rename(String from, String to) {
            this.from = from;
            this.to = to;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }
    }
}
