package com.agentscope.codewiki.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Summary of a class, interface, or enum.
 *
 * <p>Part of the hierarchical summary model: RepositorySummary → ModuleSummary → FileSummary →
 * ClassSummary → FunctionSummary
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClassSummary {

    /** Simple name (e.g., "UserService"). */
    private String name;

    /** Kind: class, interface, enum, record, abstract_class, annotation. */
    private String kind;

    /** Access modifiers and other modifiers (e.g., ["public", "abstract"]). */
    private List<String> modifiers = new ArrayList<>();

    /** Superclass name (null for interfaces, enums, or java.lang.Object). */
    private String superClass;

    /** Implemented interfaces. */
    private List<String> interfaces = new ArrayList<>();

    /** Methods declared in this class. */
    private List<FunctionSummary> methods = new ArrayList<>();

    /** Inner classes / interfaces / enums. */
    private List<ClassSummary> innerClasses = new ArrayList<>();

    /** Natural-language summary. */
    private String summary;

    /** Source location. */
    private int startLine;

    private int endLine;
    private String filePath;

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

    public List<String> getModifiers() {
        return modifiers;
    }

    public void setModifiers(List<String> modifiers) {
        this.modifiers = modifiers;
    }

    public String getSuperClass() {
        return superClass;
    }

    public void setSuperClass(String superClass) {
        this.superClass = superClass;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public void setInterfaces(List<String> interfaces) {
        this.interfaces = interfaces;
    }

    public List<FunctionSummary> getMethods() {
        return methods;
    }

    public void setMethods(List<FunctionSummary> methods) {
        this.methods = methods;
    }

    public List<ClassSummary> getInnerClasses() {
        return innerClasses;
    }

    public void setInnerClasses(List<ClassSummary> innerClasses) {
        this.innerClasses = innerClasses;
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
