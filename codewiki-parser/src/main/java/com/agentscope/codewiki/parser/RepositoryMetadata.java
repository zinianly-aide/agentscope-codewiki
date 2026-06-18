package com.agentscope.codewiki.parser;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.List;

/** Metadata extracted from a local Git repository. */
@JsonPropertyOrder({
    "repositoryName",
    "branch",
    "commitId",
    "languages",
    "fileCount",
    "readmeLocation",
    "buildFiles",
    "directoryTree"
})
public class RepositoryMetadata {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private String repositoryName;
    private String branch;
    private String commitId;
    private List<String> languages;
    private int fileCount;
    private String readmeLocation;
    private List<String> buildFiles;
    private DirectoryTree directoryTree;

    public RepositoryMetadata() {}

    private RepositoryMetadata(Builder builder) {
        this.repositoryName = builder.repositoryName;
        this.branch = builder.branch;
        this.commitId = builder.commitId;
        this.languages = builder.languages;
        this.fileCount = builder.fileCount;
        this.readmeLocation = builder.readmeLocation;
        this.buildFiles = builder.buildFiles;
        this.directoryTree = builder.directoryTree;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getCommitId() {
        return commitId;
    }

    public void setCommitId(String commitId) {
        this.commitId = commitId;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    public String getReadmeLocation() {
        return readmeLocation;
    }

    public void setReadmeLocation(String readmeLocation) {
        this.readmeLocation = readmeLocation;
    }

    public List<String> getBuildFiles() {
        return buildFiles;
    }

    public void setBuildFiles(List<String> buildFiles) {
        this.buildFiles = buildFiles;
    }

    public DirectoryTree getDirectoryTree() {
        return directoryTree;
    }

    public void setDirectoryTree(DirectoryTree directoryTree) {
        this.directoryTree = directoryTree;
    }

    /** Serialize to pretty-printed JSON. */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize RepositoryMetadata to JSON", e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String repositoryName;
        private String branch;
        private String commitId;
        private List<String> languages;
        private int fileCount;
        private String readmeLocation;
        private List<String> buildFiles;
        private DirectoryTree directoryTree;

        public Builder repositoryName(String name) {
            this.repositoryName = name;
            return this;
        }

        public Builder branch(String branch) {
            this.branch = branch;
            return this;
        }

        public Builder commitId(String commitId) {
            this.commitId = commitId;
            return this;
        }

        public Builder languages(List<String> languages) {
            this.languages = languages;
            return this;
        }

        public Builder fileCount(int fileCount) {
            this.fileCount = fileCount;
            return this;
        }

        public Builder readmeLocation(String readmeLocation) {
            this.readmeLocation = readmeLocation;
            return this;
        }

        public Builder buildFiles(List<String> buildFiles) {
            this.buildFiles = buildFiles;
            return this;
        }

        public Builder directoryTree(DirectoryTree directoryTree) {
            this.directoryTree = directoryTree;
            return this;
        }

        public RepositoryMetadata build() {
            return new RepositoryMetadata(this);
        }
    }
}
