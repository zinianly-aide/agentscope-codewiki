package com.agentscope.codewiki.indexer.impact;

import com.agentscope.codewiki.indexer.graph.GraphNode;

/** A node included in an impact report. */
public class ImpactNode {

    private String id;
    private String name;
    private String type;
    private String filePath;
    private String module;

    public ImpactNode() {}

    public ImpactNode(String id, String name, String type, String filePath, String module) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.filePath = filePath;
        this.module = module;
    }

    public static ImpactNode fromGraphNode(GraphNode node) {
        return new ImpactNode(
                node.getId(), node.getName(), node.getType(), node.getFilePath(), node.getModule());
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
}
