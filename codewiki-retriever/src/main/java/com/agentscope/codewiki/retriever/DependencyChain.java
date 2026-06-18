package com.agentscope.codewiki.retriever;

import java.util.List;

/** Dependency path related to the user question. */
public record DependencyChain(List<String> nodes, double score) {

    public DependencyChain {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }
}
