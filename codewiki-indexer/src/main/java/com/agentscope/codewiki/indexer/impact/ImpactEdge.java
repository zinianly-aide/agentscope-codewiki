package com.agentscope.codewiki.indexer.impact;

/** A directed impact relationship discovered from the code graph. */
public class ImpactEdge {

    private ImpactNode source;
    private ImpactNode target;
    private String relation;
    private int depth;

    public ImpactEdge() {}

    public ImpactEdge(ImpactNode source, ImpactNode target, String relation, int depth) {
        this.source = source;
        this.target = target;
        this.relation = relation;
        this.depth = depth;
    }

    public ImpactNode getSource() {
        return source;
    }

    public void setSource(ImpactNode source) {
        this.source = source;
    }

    public ImpactNode getTarget() {
        return target;
    }

    public void setTarget(ImpactNode target) {
        this.target = target;
    }

    public String getRelation() {
        return relation;
    }

    public void setRelation(String relation) {
        this.relation = relation;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}
