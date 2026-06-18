package com.agentscope.codewiki.retriever;

import java.util.List;

/** Full context returned by the hybrid retrieval engine for a user question. */
public record ContextPack(
        String question,
        List<CodeContext> relevantCode,
        List<SummaryContext> relevantSummaries,
        List<DependencyChain> relevantDependencyChains,
        List<ModuleContext> relevantModules) {

    public ContextPack {
        question = question == null ? "" : question;
        relevantCode = relevantCode == null ? List.of() : List.copyOf(relevantCode);
        relevantSummaries = relevantSummaries == null ? List.of() : List.copyOf(relevantSummaries);
        relevantDependencyChains =
                relevantDependencyChains == null
                        ? List.of()
                        : List.copyOf(relevantDependencyChains);
        relevantModules = relevantModules == null ? List.of() : List.copyOf(relevantModules);
    }
}
