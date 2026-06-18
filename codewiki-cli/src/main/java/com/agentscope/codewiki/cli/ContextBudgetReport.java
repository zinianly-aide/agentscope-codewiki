package com.agentscope.codewiki.cli;

import com.agentscope.codewiki.core.ModuleSummary;
import com.agentscope.codewiki.core.RepositorySummary;
import com.agentscope.codewiki.retriever.CodeContext;
import com.agentscope.codewiki.retriever.ContextPack;
import com.agentscope.codewiki.retriever.DependencyChain;
import com.agentscope.codewiki.retriever.SummaryContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Budgeted prompt inputs and token accounting for Dify/AgentScope workflow calls. */
final class ContextBudgetReport {

    private static final int MIN_CODE_TOKENS = 64;
    private static final int TOKEN_CHARS = 4;

    private final String question;
    private final String repoSummary;
    private final List<String> moduleSummaries;
    private final List<String> retrievedCodeContext;
    private final List<String> dependencyGraph;
    private final String memorySummary;
    private final Map<String, Integer> tokenBreakdown;
    private final int maxTokens;
    private final boolean trimmed;

    private ContextBudgetReport(
            String question,
            String repoSummary,
            List<String> moduleSummaries,
            List<String> retrievedCodeContext,
            List<String> dependencyGraph,
            String memorySummary,
            int maxTokens,
            boolean trimmed) {
        this.question = question;
        this.repoSummary = repoSummary;
        this.moduleSummaries = List.copyOf(moduleSummaries);
        this.retrievedCodeContext = List.copyOf(retrievedCodeContext);
        this.dependencyGraph = List.copyOf(dependencyGraph);
        this.memorySummary = memorySummary;
        this.maxTokens = maxTokens;
        this.trimmed = trimmed;
        this.tokenBreakdown = calculateBreakdown();
    }

    static ContextBudgetReport from(
            RepositorySummary repository,
            ContextPack contextPack,
            int maxTokens,
            String sessionId) {
        String question = contextPack.question();
        String repoSummary = repositorySummary(repository);
        List<String> moduleSummaries = moduleSummaries(repository, contextPack);
        List<String> code = codeContexts(contextPack);
        List<String> dependencies = dependencies(contextPack);
        String memorySummary = memorySummary(sessionId);
        ContextBudgetReport initial =
                new ContextBudgetReport(
                        question,
                        repoSummary,
                        moduleSummaries,
                        code,
                        dependencies,
                        memorySummary,
                        maxTokens,
                        false);
        return initial.totalTokens() <= maxTokens ? initial : initial.trimToBudget();
    }

    Map<String, Object> asDifyInputs() {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("question", question);
        inputs.put("repo_summary", repoSummary);
        inputs.put("module_summaries", moduleSummaries);
        inputs.put("retrieved_code_context", retrievedCodeContext);
        inputs.put("dependency_graph", dependencyGraph);
        inputs.put("memory_summary", memorySummary);
        inputs.put("context_budget", tokenBreakdown);
        inputs.put("max_context_tokens", maxTokens);
        inputs.put("trimmed", trimmed);
        return inputs;
    }

    String prompt() {
        return "Generate repository wiki pages using the provided CodeWiki context. "
                + "Respect the budgeted context and prefer high relevance code plus module summaries.";
    }

    String logLines() {
        StringBuilder builder = new StringBuilder("context budget tokens:");
        for (Map.Entry<String, Integer> entry : tokenBreakdown.entrySet()) {
            builder.append(System.lineSeparator())
                    .append("  ")
                    .append(entry.getKey())
                    .append(" tokens=")
                    .append(entry.getValue());
        }
        builder.append(System.lineSeparator()).append("  max_context_tokens=").append(maxTokens);
        builder.append(System.lineSeparator()).append("  trimmed=").append(trimmed);
        return builder.toString();
    }

    int totalTokens() {
        return tokenBreakdown.get("total");
    }

    boolean trimmed() {
        return trimmed;
    }

    private ContextBudgetReport trimToBudget() {
        int questionTokens = estimate(question);
        int repoTokens = estimate(repoSummary);
        int moduleBudget = Math.max(128, (int) (maxTokens * 0.20));
        int codeBudget = Math.max(MIN_CODE_TOKENS, (int) (maxTokens * 0.55));
        int dependencyBudget = Math.max(32, (int) (maxTokens * 0.10));
        int memoryBudget =
                Math.max(
                        0,
                        maxTokens
                                - questionTokens
                                - repoTokens
                                - moduleBudget
                                - codeBudget
                                - dependencyBudget);

        List<String> modules = fitList(moduleSummaries, moduleBudget, false);
        List<String> code = fitList(retrievedCodeContext, codeBudget, true);
        List<String> dependencies = fitList(dependencyGraph, dependencyBudget, false);
        String memory = truncate(memorySummary, memoryBudget);
        ContextBudgetReport trimmedReport =
                new ContextBudgetReport(
                        question,
                        repoSummary,
                        modules,
                        code,
                        dependencies,
                        memory,
                        maxTokens,
                        true);
        if (trimmedReport.totalTokens() <= maxTokens) {
            return trimmedReport;
        }
        int repoBudget = Math.max(64, (int) (maxTokens * 0.20));
        String compactRepoSummary = truncate(repoSummary, repoBudget);
        int compactQuestionTokens = estimate(question);
        int compactRepoTokens = estimate(compactRepoSummary);
        int compactModuleBudget = Math.max(64, (int) (maxTokens * 0.25));
        List<String> compactModules = fitList(moduleSummaries, compactModuleBudget, false);
        int compactDependencyBudget = Math.max(0, (int) (maxTokens * 0.05));
        List<String> compactDependencies = fitList(dependencyGraph, compactDependencyBudget, false);
        int remaining =
                Math.max(
                        MIN_CODE_TOKENS,
                        maxTokens
                                - compactQuestionTokens
                                - compactRepoTokens
                                - estimate(join(compactModules))
                                - estimate(join(compactDependencies)));
        return new ContextBudgetReport(
                question,
                compactRepoSummary,
                compactModules,
                fitList(retrievedCodeContext, remaining, true),
                compactDependencies,
                "",
                maxTokens,
                true);
    }

    private Map<String, Integer> calculateBreakdown() {
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        breakdown.put("repo_summary", estimate(repoSummary));
        breakdown.put("module_summaries", estimate(join(moduleSummaries)));
        breakdown.put("retrieved_code_context", estimate(join(retrievedCodeContext)));
        breakdown.put("dependency_graph", estimate(join(dependencyGraph)));
        breakdown.put("memory_summary", estimate(memorySummary));
        breakdown.put("question", estimate(question));
        int total = breakdown.values().stream().mapToInt(Integer::intValue).sum();
        breakdown.put("total", total);
        return breakdown;
    }

    private static List<String> fitList(
            List<String> values, int tokenBudget, boolean trimComments) {
        List<String> accepted = new ArrayList<>();
        int used = 0;
        for (String value : values) {
            String candidate = trimComments ? stripLongComments(value) : value;
            int tokens = estimate(candidate);
            if (used + tokens <= tokenBudget) {
                accepted.add(candidate);
                used += tokens;
            } else {
                int remaining = tokenBudget - used;
                if (remaining >= MIN_CODE_TOKENS) {
                    accepted.add(truncate(candidate, remaining));
                }
                break;
            }
        }
        return accepted;
    }

    private static String repositorySummary(RepositorySummary repository) {
        return "repository="
                + text(repository.getName())
                + " branch="
                + text(repository.getBranch())
                + " commit="
                + text(repository.getCommitId())
                + " summary="
                + text(repository.getSummary());
    }

    private static List<String> moduleSummaries(
            RepositorySummary repository, ContextPack contextPack) {
        List<String> summaries = new ArrayList<>();
        for (ModuleSummary module :
                repository.getModules() == null
                        ? List.<ModuleSummary>of()
                        : repository.getModules()) {
            summaries.add(
                    "module="
                            + text(module.getName())
                            + " path="
                            + text(module.getPath())
                            + " summary="
                            + text(module.getSummary()));
        }
        for (SummaryContext summary : contextPack.relevantSummaries()) {
            summaries.add(
                    "summary path="
                            + summary.path()
                            + " module="
                            + summary.module()
                            + " score="
                            + summary.score()
                            + " text="
                            + summary.summary());
        }
        return summaries;
    }

    private static List<String> codeContexts(ContextPack contextPack) {
        return contextPack.relevantCode().stream()
                .sorted(Comparator.comparingDouble(CodeContext::score).reversed())
                .map(
                        code ->
                                "path="
                                        + code.path()
                                        + " module="
                                        + code.module()
                                        + " language="
                                        + code.language()
                                        + " score="
                                        + code.score()
                                        + System.lineSeparator()
                                        + code.content())
                .toList();
    }

    private static List<String> dependencies(ContextPack contextPack) {
        return contextPack.relevantDependencyChains().stream()
                .map(DependencyChain::toString)
                .toList();
    }

    private static String memorySummary(String sessionId) {
        return sessionId == null || sessionId.isBlank()
                ? ""
                : "session_id="
                        + sessionId
                        + " memory_summary=No persisted memory loaded for CLI run.";
    }

    private static String stripLongComments(String value) {
        return value.replaceAll("(?s)/\\*.*?\\*/", "/* comment trimmed */")
                .replaceAll("(?m)^\\s*//.{120,}$", "// comment trimmed")
                .replaceAll("(?m)^\\s*#.{120,}$", "# comment trimmed");
    }

    private static String truncate(String value, int tokenBudget) {
        if (value == null || tokenBudget <= 0) {
            return "";
        }
        int maxChars = tokenBudget * TOKEN_CHARS;
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 28)) + "\n... [trimmed for budget]";
    }

    private static int estimate(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(value.length() / (double) TOKEN_CHARS));
    }

    private static String join(List<String> values) {
        return String.join(System.lineSeparator(), values);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
