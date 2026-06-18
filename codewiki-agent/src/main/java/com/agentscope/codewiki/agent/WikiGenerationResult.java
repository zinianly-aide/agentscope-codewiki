package com.agentscope.codewiki.agent;

import java.util.List;

/** Aggregated result for a wiki generation run. */
public record WikiGenerationResult(List<WikiPageResult> pages) {

    public WikiGenerationResult {
        pages = pages == null ? List.of() : List.copyOf(pages);
    }

    public long writtenCount() {
        return pages.stream().filter(WikiPageResult::written).count();
    }

    public long skippedCount() {
        return pages.size() - writtenCount();
    }
}
