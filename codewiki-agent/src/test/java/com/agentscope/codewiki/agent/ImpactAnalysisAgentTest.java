package com.agentscope.codewiki.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agentscope.codewiki.indexer.graph.CodeGraph;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImpactAnalysisAgentTest {

    @Test
    void shouldExposeDeterministicImpactAnalysisFacade() {
        ImpactAnalysisAgent agent = new ImpactAnalysisAgent();

        assertEquals(
                List.of(), agent.analyze(new CodeGraph(), List.of(), List.of()).getDirectImpacts());
    }
}
