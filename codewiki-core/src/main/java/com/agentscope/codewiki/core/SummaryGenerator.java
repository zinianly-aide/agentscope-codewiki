package com.agentscope.codewiki.core;

/**
 * Generates summaries from source data.
 *
 * <p>Implementations may use LLM, template-based, or heuristic approaches.
 *
 * @param <I> input type (e.g., {@code CodeSymbol}, {@code File})
 * @param <O> output summary type
 */
public interface SummaryGenerator<I, O> {

    /**
     * Generate a summary from the given input.
     *
     * @param input the source data to summarize
     * @return a populated summary object
     */
    O generate(I input);

    /**
     * Batch-generate summaries.
     *
     * @param inputs source data items
     * @return corresponding summaries (same order)
     */
    default java.util.List<O> generateAll(java.util.List<I> inputs) {
        return inputs.stream().map(this::generate).toList();
    }
}
