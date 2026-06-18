package com.agentscope.codewiki.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Summary of a logical module (e.g., "codewiki-parser", "user-service").
 *
 * <p>Part of the hierarchical summary model: RepositorySummary → ModuleSummary → FileSummary →
 * ClassSummary → FunctionSummary
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModuleSummary {

    /** Module name (e.g., "codewiki-parser"). */
    private String name;

    /** Path relative to repository root. */
    private String path;

    /** Build system descriptor (e.g., "Maven", "Gradle", "npm"). */
    private String buildSystem;

    /** Files in this module. */
    private List<FileSummary> files = new ArrayList<>();

    /** Sub-modules (for nested module systems like Maven multi-module). */
    private List<ModuleSummary> subModules = new ArrayList<>();

    /** Natural-language summary of the module. */
    private String summary;

    // ── accessors ──

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getBuildSystem() {
        return buildSystem;
    }

    public void setBuildSystem(String buildSystem) {
        this.buildSystem = buildSystem;
    }

    public List<FileSummary> getFiles() {
        return files;
    }

    public void setFiles(List<FileSummary> files) {
        this.files = files;
    }

    public List<ModuleSummary> getSubModules() {
        return subModules;
    }

    public void setSubModules(List<ModuleSummary> subModules) {
        this.subModules = subModules;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
