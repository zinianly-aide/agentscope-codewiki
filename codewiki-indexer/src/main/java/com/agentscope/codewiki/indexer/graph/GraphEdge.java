package com.agentscope.codewiki.indexer.graph;

import java.util.Objects;

/** A directed relationship between two code graph nodes. */
public class GraphEdge {

    private String id;
    private String sourceId;
    private String targetId;
    private GraphRelation type;
    private String evidence;

    public GraphEdge() {}

    public GraphEdge(String sourceId, String targetId, GraphRelation type, String evidence) {
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.type = type;
        this.evidence = evidence;
        this.id = sourceId + "->" + targetId + ":" + type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public GraphRelation getType() {
        return type;
    }

    public void setType(GraphRelation type) {
        this.type = type;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GraphEdge graphEdge)) {
            return false;
        }
        return Objects.equals(id, graphEdge.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
