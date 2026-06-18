package com.agentscope.codewiki.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Summary of a single function or method.
 *
 * <p>Part of the hierarchical summary model: RepositorySummary → ModuleSummary → FileSummary →
 * ClassSummary → FunctionSummary
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FunctionSummary {

    /** Simple name (e.g., "calculateTotal"). */
    private String name;

    /** Kind: method, constructor, function, arrow_function, lambda. */
    private String kind;

    /** Human-readable signature (e.g., "public int calculateTotal(int a, int b)"). */
    private String signature;

    /** Return type as string (e.g., "int", "void", "Promise<User>"). */
    private String returnType;

    /** Ordered parameter descriptions. */
    private List<Parameter> parameters = new ArrayList<>();

    /** Natural-language summary of what this function does. */
    private String summary;

    /** Source location. */
    private int startLine;

    private int endLine;
    private String filePath;

    // ── nested ──

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Parameter {
        private String name;
        private String type;
        private String description;

        public Parameter() {}

        public Parameter(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    // ── accessors ──

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public List<Parameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<Parameter> parameters) {
        this.parameters = parameters;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
