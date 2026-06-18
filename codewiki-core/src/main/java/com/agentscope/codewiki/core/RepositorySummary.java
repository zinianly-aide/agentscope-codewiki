package com.agentscope.codewiki.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-level summary of an entire repository.
 *
 * <p>Root of the hierarchical summary model: RepositorySummary → ModuleSummary → FileSummary →
 * ClassSummary → FunctionSummary
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RepositorySummary {

    /** Repository name (e.g., "agentscope-codewiki"). */
    private String name;

    /** VCS branch. */
    private String branch;

    /** HEAD commit id. */
    private String commitId;

    /** Detected programming languages. */
    private List<String> languages = new ArrayList<>();

    /** Total source file count. */
    private int fileCount;

    /** Top-level modules. */
    private List<ModuleSummary> modules = new ArrayList<>();

    /** Build files found (relative paths). */
    private List<String> buildFiles = new ArrayList<>();

    /** README file path relative to root. */
    private String readmePath;

    /** Natural-language summary of the entire repository. */
    private String summary;

    // ── accessors ──

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public List<ModuleSummary> getModules() {
        return modules;
    }

    public void setModules(List<ModuleSummary> modules) {
        this.modules = modules;
    }

    public List<String> getBuildFiles() {
        return buildFiles;
    }

    public void setBuildFiles(List<String> buildFiles) {
        this.buildFiles = buildFiles;
    }

    public String getReadmePath() {
        return readmePath;
    }

    public void setReadmePath(String readmePath) {
        this.readmePath = readmePath;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
