package com.agentscope.codewiki.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Summary of a single source file.
 *
 * <p>Part of the hierarchical summary model: RepositorySummary → ModuleSummary → FileSummary →
 * ClassSummary → FunctionSummary
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileSummary {

    /** File name (e.g., "UserService.java"). */
    private String name;

    /** Path relative to module root. */
    private String path;

    /** Programming language (e.g., "Java", "Python", "TypeScript"). */
    private String language;

    /** Top-level classes and interfaces. */
    private List<ClassSummary> classes = new ArrayList<>();

    /** Top-level functions (for languages without enclosing classes). */
    private List<FunctionSummary> functions = new ArrayList<>();

    /** Natural-language summary of the entire file. */
    private String summary;

    /** Lines of code. */
    private int linesOfCode;

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

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public List<ClassSummary> getClasses() {
        return classes;
    }

    public void setClasses(List<ClassSummary> classes) {
        this.classes = classes;
    }

    public List<FunctionSummary> getFunctions() {
        return functions;
    }

    public void setFunctions(List<FunctionSummary> functions) {
        this.functions = functions;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public int getLinesOfCode() {
        return linesOfCode;
    }

    public void setLinesOfCode(int linesOfCode) {
        this.linesOfCode = linesOfCode;
    }
}
