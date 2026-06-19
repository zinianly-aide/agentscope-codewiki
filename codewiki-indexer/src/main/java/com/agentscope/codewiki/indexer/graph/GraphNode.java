package com.agentscope.codewiki.indexer.graph;

import com.agentscope.codewiki.parser.CodeSymbol;
import java.util.Objects;

/** A symbol-level node in the code knowledge graph. */
public class GraphNode {

    private String id;
    private String name;
    private String type;
    private String signature;
    private String filePath;
    private String module;
    private int startLine;
    private int endLine;

    public GraphNode() {}

    public GraphNode(
            String id,
            String name,
            String type,
            String signature,
            String filePath,
            String module,
            int startLine,
            int endLine) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.signature = signature;
        this.filePath = filePath;
        this.module = module;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    public static GraphNode fromSymbol(CodeSymbol symbol) {
        String id = stableId(symbol);
        String filePath = normalize(symbol.getFilePath());
        return new GraphNode(
                id,
                symbol.getName(),
                symbol.getType(),
                symbol.getSignature(),
                filePath,
                moduleOf(filePath),
                symbol.getStartLine(),
                symbol.getEndLine());
    }

    public static String stableId(CodeSymbol symbol) {
        String filePath = normalize(symbol.getFilePath());
        String name = nullToEmpty(symbol.getName());
        String type = nullToEmpty(symbol.getType());
        return filePath + "#" + name + ":" + type + ":" + symbol.getStartLine();
    }

    private static String moduleOf(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "root";
        }
        String normalized = filePath.replace('\\', '/');
        int slash = normalized.indexOf('/');
        if (slash <= 0) {
            return "root";
        }
        return normalized.substring(0, slash);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\\', '/');
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GraphNode graphNode)) {
            return false;
        }
        return Objects.equals(id, graphNode.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
