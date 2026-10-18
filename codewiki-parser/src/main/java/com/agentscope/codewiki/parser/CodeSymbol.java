package com.agentscope.codewiki.parser;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A unified symbol extracted from source code via AST parsing. Supports class, interface, enum,
 * method, constructor, function, arrow_function.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodeSymbol {

    private String name;
    private String type;
    private String signature;
    private int startLine;
    private int endLine;
    private String sourceCode;
    private String filePath;
    private String ownerType;
    // Null means parser predates AST call-site support; empty means parsed with no calls.
    private java.util.List<CodeReference> references;
    private java.util.List<String> imports;

    public CodeSymbol() {}

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

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
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

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(String ownerType) {
        this.ownerType = ownerType;
    }

    public java.util.List<CodeReference> getReferences() {
        return references;
    }

    public void setReferences(java.util.List<CodeReference> references) {
        this.references = references;
    }

    public java.util.List<String> getImports() {
        return imports;
    }

    public void setImports(java.util.List<String> imports) {
        this.imports = imports;
    }

    @Override
    public String toString() {
        return type + " " + name + " (L" + startLine + "-" + endLine + ")";
    }
}
