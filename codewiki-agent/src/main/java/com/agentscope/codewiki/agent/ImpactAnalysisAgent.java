package com.agentscope.codewiki.agent;

import com.agentscope.codewiki.indexer.graph.CodeGraph;
import com.agentscope.codewiki.indexer.impact.ImpactAnalyzer;
import com.agentscope.codewiki.indexer.impact.ImpactReport;
import java.util.List;

/** Deterministic agent-facing facade for code impact analysis. No LLM review is performed. */
public class ImpactAnalysisAgent {

    public ImpactReport analyze(
            CodeGraph graph, List<String> changedFiles, List<String> changedSymbols) {
        return new ImpactAnalyzer(graph).analyze(changedFiles, changedSymbols);
    }
}
