package com.agentscope.codewiki.indexer.impact;

/** A directed impact relationship discovered from the code graph. */
public class ImpactEdge {

    private ImpactNode source;
    private ImpactNode target;
    private String relation;
    private int depth;
    private String origin;
    private String confidence;
    private int evidenceLine;

    public ImpactEdge() {}

    public ImpactEdge(ImpactNode source, ImpactNode target, String relation, int depth) {
        this.source = source;
        this.target = target;
        this.relation = relation;
        this.depth = depth;
    }

    public ImpactEdge(
            ImpactNode source,
            ImpactNode target,
            String relation,
            int depth,
            String origin,
            String confidence,
            int evidenceLine) {
        this(source, target, relation, depth);
        this.origin = origin;
        this.confidence = confidence;
        this.evidenceLine = evidenceLine;
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

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public int getEvidenceLine() {
        return evidenceLine;
    }

    public void setEvidenceLine(int evidenceLine) {
        this.evidenceLine = evidenceLine;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}
