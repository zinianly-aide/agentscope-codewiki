package com.agentscope.codewiki.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Markdown report emitted by the AI diff review engine. */
public class DiffReviewReport {

    public static final String DEFAULT_FILE_NAME = "diff-review.md";

    private static final List<String> FINDING_SECTIONS =
            List.of(
                    "Potential Risks",
                    "Code Quality Issues",
                    "Security Concerns",
                    "Performance Concerns",
                    "Suggested Tests",
                    "Suggested Refactors");

    private String summary = "No summary generated.";
    private List<String> changedFiles = new ArrayList<>();
    private String impactAnalysis = "No impact analysis provided.";
    private String evidence = "No review evidence captured.";
    private List<ReviewFinding> findings = new ArrayList<>();

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary == null || summary.isBlank() ? "No summary generated." : summary;
    }

    public List<String> getChangedFiles() {
        return List.copyOf(changedFiles);
    }

    public void setChangedFiles(List<String> changedFiles) {
        this.changedFiles =
                changedFiles == null ? new ArrayList<>() : new ArrayList<>(changedFiles);
    }

    public String getImpactAnalysis() {
        return impactAnalysis;
    }

    public void setImpactAnalysis(String impactAnalysis) {
        this.impactAnalysis =
                impactAnalysis == null || impactAnalysis.isBlank()
                        ? "No impact analysis provided."
                        : impactAnalysis;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence =
                evidence == null || evidence.isBlank() ? "No review evidence captured." : evidence;
    }

    public List<ReviewFinding> getFindings() {
        return List.copyOf(findings);
    }

    public void setFindings(List<ReviewFinding> findings) {
        this.findings = findings == null ? new ArrayList<>() : new ArrayList<>(findings);
    }

    public void addFinding(ReviewFinding finding) {
        if (finding != null) {
            findings.add(finding);
        }
    }

    public String toMarkdown() {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Summary\n\n").append(summary).append("\n\n");
        markdown.append("# Changed Files\n\n");
        appendList(markdown, changedFiles);
        markdown.append("\n# Impact Analysis\n\n").append(impactAnalysis).append("\n\n");

        Map<String, List<ReviewFinding>> findingsBySection = findingsBySection();
        for (String section : FINDING_SECTIONS) {
            markdown.append("# ").append(section).append("\n\n");
            appendFindings(markdown, findingsBySection.getOrDefault(section, List.of()));
            markdown.append('\n');
        }
        markdown.append("# Review Evidence\n\n").append(evidence).append('\n');
        return markdown.toString();
    }

    public Path writeMarkdown(Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        Path output = outputDirectory.resolve(DEFAULT_FILE_NAME);
        Files.writeString(output, toMarkdown());
        return output;
    }

    private Map<String, List<ReviewFinding>> findingsBySection() {
        Map<String, List<ReviewFinding>> grouped = new LinkedHashMap<>();
        for (ReviewFinding finding : findings) {
            grouped.computeIfAbsent(finding.getSection(), ignored -> new ArrayList<>())
                    .add(finding);
        }
        return grouped;
    }

    private void appendFindings(StringBuilder markdown, List<ReviewFinding> sectionFindings) {
        if (sectionFindings.isEmpty()) {
            markdown.append("- None identified.\n");
            return;
        }
        for (ReviewFinding finding : sectionFindings) {
            markdown.append("- ")
                    .append(finding.getSeverity())
                    .append(" ")
                    .append(finding.getMessage());
            List<String> references = new ArrayList<>();
            if (!finding.getFilePath().isBlank()) {
                references.add(finding.getFilePath());
            }
            if (!finding.symbolReference().isBlank()) {
                references.add(finding.symbolReference());
            }
            if (!references.isEmpty()) {
                markdown.append(" (").append(String.join(" / ", references)).append(")");
            }
            if (!finding.getEvidence().isBlank()) {
                markdown.append("\n  Evidence: ").append(finding.getEvidence());
            }
            markdown.append('\n');
        }
    }

    private void appendList(StringBuilder markdown, List<String> values) {
        if (values == null || values.isEmpty()) {
            markdown.append("- None.\n");
            return;
        }
        values.forEach(value -> markdown.append("- ").append(value).append('\n'));
    }
}
