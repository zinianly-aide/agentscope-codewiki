package com.agentscope.codewiki.agent;

import com.agentscope.codewiki.dify.DifyChatModel;
import com.agentscope.codewiki.indexer.graph.CodeGraph;
import com.agentscope.codewiki.indexer.impact.ImpactAnalyzer;
import com.agentscope.codewiki.indexer.impact.ImpactEdge;
import com.agentscope.codewiki.indexer.impact.ImpactReport;
import com.agentscope.codewiki.retriever.CodeContext;
import com.agentscope.codewiki.retriever.ContextPack;
import com.agentscope.codewiki.retriever.HybridRetriever;
import com.agentscope.codewiki.retriever.RetrievalCorpus;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** AI diff review engine that combines diff parsing, impact analysis, retrieval, and Dify. */
public class DiffReviewAgent {

    private static final int DEFAULT_CONTEXT_BUDGET_TOKENS = 8_000;
    private static final int TOKEN_CHARS = 4;
    private static final int REPORT_IMPACT_LIMIT = 25;
    private static final int PROMPT_IMPACT_LIMIT = 20;
    private static final Pattern CLASS_PATTERN =
            Pattern.compile("\\b(class|interface|enum|record)\\s+(\\w+)");
    private static final Pattern METHOD_PATTERN =
            Pattern.compile(
                    "(?:public|protected|private|static|final|synchronized|native|abstract|\\s)+"
                            + "[\\w<>\\[\\], ?]+\\s+(\\w+)\\s*\\(");

    private final HybridRetriever retriever;
    private final DifyChatModel difyChatModel;
    private final boolean mockMode;
    private final int contextBudgetTokens;

    private DiffReviewAgent(
            HybridRetriever retriever,
            DifyChatModel difyChatModel,
            boolean mockMode,
            int contextBudgetTokens) {
        this.retriever = retriever;
        this.difyChatModel = difyChatModel;
        this.mockMode = mockMode;
        this.contextBudgetTokens = Math.max(64, contextBudgetTokens);
    }

    public static DiffReviewAgent mockMode(RetrievalCorpus corpus, int contextBudgetTokens) {
        return new DiffReviewAgent(
                new HybridRetriever(corpus), null, true, normalizeBudget(contextBudgetTokens));
    }

    public static DiffReviewAgent difyMode(
            RetrievalCorpus corpus, DifyChatModel difyChatModel, int contextBudgetTokens) {
        return new DiffReviewAgent(
                new HybridRetriever(corpus),
                difyChatModel,
                false,
                normalizeBudget(contextBudgetTokens));
    }

    public DiffReviewReport review(
            String gitDiff, List<String> changedFiles, ImpactReport impactReport) {
        List<String> safeChangedFiles = safeList(changedFiles);
        if (safeChangedFiles.isEmpty()) {
            return noCodeChangesReport();
        }
        List<ChangedSymbol> symbols = extractChangedSymbols(gitDiff);
        ImpactReport safeImpactReport = impactReport == null ? new ImpactReport() : impactReport;
        String query = buildRetrievalQuery(gitDiff, safeChangedFiles, safeImpactReport, symbols);
        ContextPack contextPack = retriever.retrieve(query);
        BudgetedContext budgetedContext = applyContextBudget(contextPack);
        if (mockMode) {
            return mockReview(
                    gitDiff, safeChangedFiles, safeImpactReport, symbols, budgetedContext);
        }
        return difyReview(gitDiff, safeChangedFiles, safeImpactReport, symbols, budgetedContext);
    }

    public DiffReviewReport review(
            String gitDiff,
            List<String> changedFiles,
            CodeGraph codeGraph,
            List<String> changedSymbols) {
        ImpactReport impactReport =
                new ImpactAnalyzer(codeGraph).analyze(changedFiles, changedSymbols);
        return review(gitDiff, changedFiles, impactReport);
    }

    public static DiffReviewReport noCodeChangesReport() {
        DiffReviewReport report = new DiffReviewReport();
        report.setSummary("No code changes detected.");
        report.setChangedFiles(List.of());
        report.setImpactAnalysis(
                "Risk level: LOW\n"
                        + "Changed symbols: []\n"
                        + "Affected modules: []\n"
                        + "Direct impacts:\n- none\n"
                        + "Transitive impacts:\n- none\n");
        report.setEvidence("No code changes detected.");
        return report;
    }

    private DiffReviewReport mockReview(
            String gitDiff,
            List<String> changedFiles,
            ImpactReport impactReport,
            List<ChangedSymbol> symbols,
            BudgetedContext budgetedContext) {
        DiffReviewReport report = baseReport(changedFiles, impactReport, symbols, budgetedContext);
        report.setSummary(
                "Reviewed "
                        + safeList(changedFiles).size()
                        + " changed file(s), "
                        + symbols.size()
                        + " changed symbol(s), and impact risk "
                        + impactReport.getRiskLevel()
                        + ".");
        if (safeList(changedFiles).isEmpty()) {
            report.addFinding(
                    new ReviewFinding(
                            ReviewSeverity.INFO,
                            "Potential Risks",
                            "No changed files were supplied.",
                            "",
                            "",
                            "",
                            "Evidence: changed files input was empty."));
        }
        for (ChangedSymbol symbol : symbols) {
            report.addFinding(
                    new ReviewFinding(
                            ReviewSeverity.INFO,
                            "Suggested Tests",
                            "Add regression tests for changed symbols.",
                            firstChangedFile(changedFiles),
                            symbol.className(),
                            symbol.methodName(),
                            "Changed symbol detected in git diff: " + symbol.reference()));
        }
        if (gitDiff != null && gitDiff.contains("return token != null")) {
            report.addFinding(
                    new ReviewFinding(
                            ReviewSeverity.MEDIUM,
                            "Security Concerns",
                            "Token validation appears shallow; verify expiry, issuer, and signature checks.",
                            firstChangedFile(changedFiles),
                            firstClass(symbols),
                            firstMethod(symbols),
                            "Diff evidence contains `return token != null`."));
        }
        if (!budgetedContext.trimmed()) {
            report.addFinding(
                    new ReviewFinding(
                            ReviewSeverity.LOW,
                            "Code Quality Issues",
                            "Review related retrieved context for consistency with changed code.",
                            firstChangedFile(changedFiles),
                            firstClass(symbols),
                            firstMethod(symbols),
                            "Retrieved ContextPack includes related code and summaries."));
        }
        return report;
    }

    private DiffReviewReport difyReview(
            String gitDiff,
            List<String> changedFiles,
            ImpactReport impactReport,
            List<ChangedSymbol> symbols,
            BudgetedContext budgetedContext) {
        DiffReviewReport report = baseReport(changedFiles, impactReport, symbols, budgetedContext);
        String prompt =
                buildDifyPrompt(gitDiff, changedFiles, impactReport, symbols, budgetedContext);
        try {
            String answer = difyChatModel.chat(prompt);
            report.setSummary(
                    "Dify review completed for " + safeList(changedFiles).size() + " file(s).");
            report.setEvidence(budgetedContext.text() + "\n\nDify answer:\n" + nullToEmpty(answer));
            parseDifyFindings(answer, changedFiles, symbols).forEach(report::addFinding);
        } catch (IOException e) {
            report.setSummary("Dify review failed; fallback evidence captured.");
            report.addFinding(
                    new ReviewFinding(
                            ReviewSeverity.HIGH,
                            "Potential Risks",
                            "Dify review failed and needs retry before release sign-off.",
                            firstChangedFile(changedFiles),
                            firstClass(symbols),
                            firstMethod(symbols),
                            e.getMessage()));
        }
        return report;
    }

    private DiffReviewReport baseReport(
            List<String> changedFiles,
            ImpactReport impactReport,
            List<ChangedSymbol> symbols,
            BudgetedContext budgetedContext) {
        DiffReviewReport report = new DiffReviewReport();
        report.setChangedFiles(safeList(changedFiles));
        report.setImpactAnalysis(
                renderImpactAnalysis(
                        impactReport, symbols, REPORT_IMPACT_LIMIT, REPORT_IMPACT_LIMIT));
        report.setEvidence(budgetedContext.text());
        return report;
    }

    private String buildDifyPrompt(
            String gitDiff,
            List<String> changedFiles,
            ImpactReport impactReport,
            List<ChangedSymbol> symbols,
            BudgetedContext budgetedContext) {
        String instructions =
                "You are the AI Diff Review Engine. Produce findings with severities INFO, LOW, "
                        + "MEDIUM, HIGH, or CRITICAL and cite file path, class, method, and evidence.";
        String changed =
                "Changed files:\n"
                        + String.join("\n", safeList(changedFiles))
                        + "\n\nChanged symbols:\n"
                        + symbols.stream().map(ChangedSymbol::reference).toList();
        String impact =
                "Impact analysis:\n"
                        + renderImpactAnalysis(
                                impactReport, symbols, PROMPT_IMPACT_LIMIT, PROMPT_IMPACT_LIMIT);
        String context = "ContextPack:\n" + budgetedContext.text();
        String diff = "Git diff:\n" + nullToEmpty(gitDiff);
        return fitPromptToBudget(instructions, changed, impact, context, diff);
    }

    private List<ReviewFinding> parseDifyFindings(
            String answer, List<String> changedFiles, List<ChangedSymbol> symbols) {
        List<ReviewFinding> findings = new ArrayList<>();
        String text = nullToEmpty(answer);
        for (ReviewSeverity severity : ReviewSeverity.values()) {
            if (text.toUpperCase(Locale.ROOT).contains(severity.name())) {
                findings.add(
                        new ReviewFinding(
                                severity,
                                inferSection(text),
                                firstLine(text),
                                firstChangedFile(changedFiles),
                                firstClass(symbols),
                                firstMethod(symbols),
                                extractEvidence(text)));
                break;
            }
        }
        if (findings.isEmpty() && !text.isBlank()) {
            findings.add(
                    new ReviewFinding(
                            ReviewSeverity.INFO,
                            inferSection(text),
                            firstLine(text),
                            firstChangedFile(changedFiles),
                            firstClass(symbols),
                            firstMethod(symbols),
                            extractEvidence(text)));
        }
        return findings;
    }

    private BudgetedContext applyContextBudget(ContextPack contextPack) {
        String rendered = renderContextPack(contextPack);
        int maxChars = contextBudgetTokens * TOKEN_CHARS;
        if (rendered.length() <= maxChars) {
            return new BudgetedContext(rendered, false);
        }
        StringBuilder trimmed = new StringBuilder();
        trimmed.append("Context budget exceeded; low-relevance context was trimmed.\n");
        trimmed.append("Question: ").append(contextPack.question()).append('\n');
        contextPack.relevantCode().stream()
                .limit(3)
                .forEach(
                        code ->
                                trimmed.append("Code: ")
                                        .append(code.path())
                                        .append(" module=")
                                        .append(code.module())
                                        .append(" score=")
                                        .append(code.score())
                                        .append('\n'));
        String text = trimmed.toString();
        if (text.length() > maxChars) {
            text = text.substring(0, maxChars);
        }
        return new BudgetedContext(text, true);
    }

    private String renderContextPack(ContextPack contextPack) {
        StringBuilder builder = new StringBuilder();
        builder.append("Question: ").append(contextPack.question()).append('\n');
        for (CodeContext code : contextPack.relevantCode()) {
            builder.append("Code: ")
                    .append(code.path())
                    .append(" module=")
                    .append(code.module())
                    .append(" language=")
                    .append(code.language())
                    .append(" score=")
                    .append(code.score())
                    .append('\n')
                    .append(code.content())
                    .append('\n');
        }
        contextPack
                .relevantSummaries()
                .forEach(
                        summary ->
                                builder.append("Summary: ")
                                        .append(summary.path())
                                        .append(" ")
                                        .append(summary.summary())
                                        .append('\n'));
        contextPack
                .relevantDependencyChains()
                .forEach(
                        chain ->
                                builder.append("DependencyChain: ")
                                        .append(chain.nodes())
                                        .append('\n'));
        contextPack
                .relevantModules()
                .forEach(module -> builder.append("Module: ").append(module.module()).append('\n'));
        return builder.toString();
    }

    private String renderImpactAnalysis(
            ImpactReport impactReport,
            List<ChangedSymbol> symbols,
            int directLimit,
            int transitiveLimit) {
        StringBuilder builder = new StringBuilder();
        builder.append("Risk level: ").append(impactReport.getRiskLevel()).append('\n');
        builder.append("Changed symbols: ").append(impactReport.getChangedSymbols()).append('\n');
        if (!symbols.isEmpty()) {
            builder.append("Diff symbols: ")
                    .append(symbols.stream().map(ChangedSymbol::reference).toList())
                    .append('\n');
        }
        builder.append("Affected modules: ").append(impactReport.getAffectedModules()).append('\n');
        appendImpacts(builder, "Direct impacts", impactReport.getDirectImpacts(), directLimit);
        appendImpacts(
                builder,
                "Transitive impacts",
                impactReport.getTransitiveImpacts(),
                transitiveLimit);
        return builder.toString();
    }

    private void appendImpacts(
            StringBuilder builder, String title, List<ImpactEdge> impacts, int limit) {
        builder.append(title).append(':').append('\n');
        if (impacts.isEmpty()) {
            builder.append("- none\n");
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        int written = 0;
        for (ImpactEdge impact : impacts) {
            String source = impact.getSource() == null ? "" : impact.getSource().getFilePath();
            String target = impact.getTarget() == null ? "" : impact.getTarget().getFilePath();
            String key =
                    impact.getRelation() + "|" + impact.getDepth() + "|" + source + "|" + target;
            if (!seen.add(key)) {
                continue;
            }
            if (written >= Math.max(0, limit)) {
                break;
            }
            builder.append("- ")
                    .append(impact.getRelation())
                    .append(" depth=")
                    .append(impact.getDepth())
                    .append(" ")
                    .append(source)
                    .append(" -> ")
                    .append(target)
                    .append('\n');
            written++;
        }
        int remaining = Math.max(0, seen.size() - written);
        if (remaining > 0 || impacts.size() > seen.size()) {
            builder.append("- ... trimmed ")
                    .append(Math.max(remaining, impacts.size() - written))
                    .append(" impact(s) for prompt budget\n");
        }
    }

    private List<ChangedSymbol> extractChangedSymbols(String gitDiff) {
        Set<ChangedSymbol> symbols = new LinkedHashSet<>();
        String currentClass = "";
        for (String line : nullToEmpty(gitDiff).split("\\R")) {
            if (!line.startsWith("+") || line.startsWith("+++")) {
                continue;
            }
            Matcher classMatcher = CLASS_PATTERN.matcher(line);
            if (classMatcher.find()) {
                currentClass = classMatcher.group(2);
                symbols.add(new ChangedSymbol(currentClass, ""));
            }
            Matcher methodMatcher = METHOD_PATTERN.matcher(line);
            if (methodMatcher.find()) {
                symbols.add(new ChangedSymbol(currentClass, methodMatcher.group(1)));
            }
        }
        return new ArrayList<>(symbols);
    }

    private String buildRetrievalQuery(
            String gitDiff,
            List<String> changedFiles,
            ImpactReport impactReport,
            List<ChangedSymbol> symbols) {
        List<String> terms = new ArrayList<>();
        terms.addAll(safeList(changedFiles));
        terms.addAll(impactReport.getChangedSymbols());
        terms.addAll(impactReport.getAffectedModules());
        terms.addAll(symbols.stream().map(ChangedSymbol::reference).toList());
        terms.add(truncate(nullToEmpty(gitDiff), 1_000));
        return String.join(" ", terms);
    }

    private String fitPromptToBudget(
            String instructions, String changed, String impact, String context, String diff) {
        int maxChars = Math.max(256, contextBudgetTokens * TOKEN_CHARS);
        String full = joinSections(instructions, changed, impact, context, diff);
        if (full.length() <= maxChars) {
            return full;
        }
        int fixedChars = joinSections(instructions, changed).length() + 12;
        int remaining = Math.max(0, maxChars - fixedChars);
        int impactChars = Math.max(256, (int) (remaining * 0.25));
        int contextChars = Math.max(256, (int) (remaining * 0.40));
        int diffChars = Math.max(256, remaining - impactChars - contextChars);
        String compact =
                joinSections(
                        instructions,
                        changed,
                        truncate(impact, impactChars),
                        truncate(context, contextChars),
                        truncate(diff, diffChars));
        if (compact.length() <= maxChars) {
            return compact;
        }
        return truncate(compact, maxChars);
    }

    private String joinSections(String... sections) {
        return String.join("\n\n", sections);
    }

    private String truncate(String value, int maxChars) {
        String text = nullToEmpty(value);
        if (text.length() <= maxChars) {
            return text;
        }
        String marker = "\n... [trimmed for Dify prompt budget]";
        return text.substring(0, Math.max(0, maxChars - marker.length())) + marker;
    }

    private String inferSection(String text) {
        for (String section :
                List.of(
                        "Security Concerns",
                        "Performance Concerns",
                        "Code Quality Issues",
                        "Suggested Tests",
                        "Suggested Refactors",
                        "Potential Risks")) {
            if (text.toLowerCase(Locale.ROOT).contains(section.toLowerCase(Locale.ROOT))) {
                return section;
            }
        }
        return "Potential Risks";
    }

    private String extractEvidence(String text) {
        int evidence = text.toLowerCase(Locale.ROOT).indexOf("evidence:");
        return evidence >= 0
                ? text.substring(evidence + "evidence:".length()).trim()
                : firstLine(text);
    }

    private String firstLine(String text) {
        return nullToEmpty(text).lines().findFirst().orElse("").replace("- ", "").trim();
    }

    private String firstChangedFile(List<String> changedFiles) {
        return safeList(changedFiles).stream().findFirst().orElse("");
    }

    private String firstClass(List<ChangedSymbol> symbols) {
        return symbols.stream()
                .map(ChangedSymbol::className)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse("");
    }

    private String firstMethod(List<ChangedSymbol> symbols) {
        return symbols.stream()
                .map(ChangedSymbol::methodName)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse("");
    }

    private static int normalizeBudget(int contextBudgetTokens) {
        return contextBudgetTokens <= 0 ? DEFAULT_CONTEXT_BUDGET_TOKENS : contextBudgetTokens;
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record ChangedSymbol(String className, String methodName) {
        private ChangedSymbol {
            className = className == null ? "" : className;
            methodName = methodName == null ? "" : methodName;
        }

        private String reference() {
            if (!className.isBlank() && !methodName.isBlank()) {
                return className + "." + methodName;
            }
            if (!className.isBlank()) {
                return className;
            }
            return methodName;
        }
    }

    private record BudgetedContext(String text, boolean trimmed) {
        private BudgetedContext {
            text = text == null ? "" : text;
        }
    }
}
