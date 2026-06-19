package com.agentscope.codewiki.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentscope.codewiki.dify.DifyChatModel;
import com.agentscope.codewiki.indexer.impact.ImpactReport;
import com.agentscope.codewiki.retriever.CodeDocument;
import com.agentscope.codewiki.retriever.RetrievalCorpus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiffReviewAgentTest {

    @TempDir Path tempDir;

    @Test
    void mockReviewIncludesChangedFilesSymbolsEvidenceAndSuggestedTests() {
        DiffReviewAgent agent = DiffReviewAgent.mockMode(retrievalCorpus(), 2_000);
        ImpactReport impactReport = new ImpactReport();
        impactReport.setRiskLevel("MEDIUM");
        impactReport.setChangedSymbols(List.of("UserService.login"));
        impactReport.setAffectedModules(List.of("auth"));

        DiffReviewReport report =
                agent.review(
                        "diff --git a/src/UserService.java b/src/UserService.java\n"
                                + "+ public class UserService {\n"
                                + "+   public boolean login(String token) { return token != null; }\n"
                                + "+ }\n",
                        List.of("src/UserService.java"),
                        impactReport);

        String markdown = report.toMarkdown();

        assertTrue(markdown.contains("# Summary"));
        assertTrue(markdown.contains("# Changed Files"));
        assertTrue(markdown.contains("src/UserService.java"));
        assertTrue(markdown.contains("UserService"));
        assertTrue(markdown.contains("login"));
        assertTrue(markdown.contains("Evidence:"));
        assertTrue(markdown.contains("Add regression tests for changed symbols"));
    }

    @Test
    void difyReviewUsesDifyChatModelAndPreservesModelEvidence() {
        CapturingDifyChatModel dify =
                new CapturingDifyChatModel(
                        "Potential Risks:\n"
                                + "- HIGH src/UserService.java UserService.login Evidence: token validation changed");
        DiffReviewAgent agent = DiffReviewAgent.difyMode(retrievalCorpus(), dify, 2_000);

        DiffReviewReport report =
                agent.review(
                        "diff --git a/src/UserService.java b/src/UserService.java\n"
                                + "+ public boolean login(String token) { return token != null; }\n",
                        List.of("src/UserService.java"),
                        new ImpactReport());

        assertTrue(dify.lastPrompt.contains("ContextPack"));
        assertTrue(dify.lastPrompt.contains("src/UserService.java"));
        assertTrue(report.toMarkdown().contains("token validation changed"));
        assertTrue(
                report.getFindings().stream()
                        .anyMatch(f -> f.getSeverity() == ReviewSeverity.HIGH));
    }

    @Test
    void budgetTrimmingKeepsChangedFileAndDropsOversizedContext() {
        DiffReviewAgent agent = DiffReviewAgent.mockMode(oversizedRetrievalCorpus(), 80);

        DiffReviewReport report =
                agent.review(
                        "diff --git a/src/UserService.java b/src/UserService.java\n"
                                + "+ public boolean login(String token) { return token != null; }\n",
                        List.of("src/UserService.java"),
                        new ImpactReport());

        String evidence = report.getEvidence();
        assertTrue(evidence.contains("src/UserService.java"));
        assertTrue(evidence.contains("Context budget exceeded"));
        assertFalse(evidence.contains("very-long-context-marker-at-the-end"));
    }

    @Test
    void reportGenerationWritesDiffReviewMarkdown() throws IOException {
        DiffReviewReport report = new DiffReviewReport();
        report.setSummary("Reviewed 1 changed file.");
        report.setChangedFiles(List.of("src/UserService.java"));
        report.addFinding(
                new ReviewFinding(
                        ReviewSeverity.LOW,
                        "Code Quality Issues",
                        "Prefer explicit token validation.",
                        "src/UserService.java",
                        "UserService",
                        "login",
                        "Evidence: return token != null"));

        Path output = report.writeMarkdown(tempDir);

        assertEquals("diff-review.md", output.getFileName().toString());
        String markdown = Files.readString(output);
        assertTrue(markdown.contains("# Code Quality Issues"));
        assertTrue(markdown.contains("LOW"));
        assertTrue(markdown.contains("UserService.login"));
        assertTrue(markdown.contains("Evidence: return token != null"));
    }

    private RetrievalCorpus retrievalCorpus() {
        return RetrievalCorpus.of(
                List.of(
                        CodeDocument.builder()
                                .id("user-service")
                                .module("auth")
                                .path("src/UserService.java")
                                .language("java")
                                .content(
                                        "class UserService { boolean login(String token) { return token != null; } }")
                                .summary("Handles UserService login token checks")
                                .dependencies(List.of("src/AuthController.java"))
                                .build()));
    }

    private RetrievalCorpus oversizedRetrievalCorpus() {
        return RetrievalCorpus.of(
                List.of(
                        CodeDocument.builder()
                                .id("user-service")
                                .module("auth")
                                .path("src/UserService.java")
                                .language("java")
                                .content("x".repeat(2_000) + "very-long-context-marker-at-the-end")
                                .summary("large context")
                                .dependencies(List.of())
                                .build()));
    }

    private static final class CapturingDifyChatModel extends DifyChatModel {
        private final String answer;
        private String lastPrompt;

        private CapturingDifyChatModel(String answer) {
            super("http://localhost", "test-key", "test-user");
            this.answer = answer;
        }

        @Override
        public String chat(String query) {
            this.lastPrompt = query;
            return answer;
        }
    }
}
