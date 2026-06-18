package com.agentscope.codewiki.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentscope.codewiki.core.ModuleSummary;
import com.agentscope.codewiki.core.RepositorySummary;
import com.agentscope.codewiki.retriever.CodeContext;
import com.agentscope.codewiki.retriever.ContextPack;
import com.agentscope.codewiki.retriever.ModuleContext;
import com.agentscope.codewiki.retriever.SummaryContext;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests context budget accounting and trimming. */
class ContextBudgetReportTest {

    @Test
    void trimsContextToMaxBudgetAndLogsBreakdown() {
        RepositorySummary repository = new RepositorySummary();
        repository.setName("repo");
        repository.setBranch("main");
        repository.setCommitId("abc");
        repository.setSummary("Repository summary".repeat(60));
        ModuleSummary module = new ModuleSummary();
        module.setName("app");
        module.setPath(".");
        module.setSummary("Important module summary".repeat(30));
        repository.setModules(List.of(module));
        String largeLowRelevantCode =
                "file=src/App.java\nsymbol=App lines=1-10\n"
                        + "// very long comment ".repeat(200)
                        + "class App {}";
        ContextPack pack =
                new ContextPack(
                        "Generate wiki",
                        List.of(
                                new CodeContext(
                                        "high",
                                        "app",
                                        "src/App.java",
                                        "Java",
                                        "file=src/App.java\nsymbol=App lines=1-10\nclass App {}",
                                        0.99),
                                new CodeContext(
                                        "low",
                                        "app",
                                        "src/Low.java",
                                        "Java",
                                        largeLowRelevantCode,
                                        0.01)),
                        List.of(
                                new SummaryContext(
                                        "high", "app", "src/App.java", "App summary", 0.9)),
                        List.of(),
                        List.of(new ModuleContext("app", 2, 1.0)));

        ContextBudgetReport report = ContextBudgetReport.from(repository, pack, 350, "session-1");

        assertTrue(report.trimmed());
        assertTrue(report.totalTokens() <= 350);
        assertTrue(report.logLines().contains("repo_summary tokens="));
        assertTrue(report.logLines().contains("retrieved_code_context tokens="));
        assertTrue(report.asDifyInputs().toString().contains("src/App.java"));
        assertTrue(report.asDifyInputs().toString().contains("lines=1-10"));
    }
}
