package com.agentscope.codewiki.agent;

import com.agentscope.codewiki.core.ClassSummary;
import com.agentscope.codewiki.core.FileSummary;
import com.agentscope.codewiki.core.ModuleSummary;
import com.agentscope.codewiki.core.RepositorySummary;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

final class WikiMarkdown {

    static final String FINGERPRINT_PREFIX = "<!-- codewiki:fingerprint=";
    static final String FINGERPRINT_SUFFIX = " -->";

    private WikiMarkdown() {}

    static WikiPageContent page(
            RepositorySummary repository, WikiPage page, boolean mermaidEnabled) {
        String body =
                switch (page) {
                    case OVERVIEW -> overview(repository);
                    case ARCHITECTURE -> architecture(repository, mermaidEnabled);
                    case MODULES -> modules(repository);
                    case DATAFLOW -> dataflow(repository, mermaidEnabled);
                    case REVIEW -> review(repository);
                };
        String fingerprint = sha256(page.name() + "\n" + body);
        String markdown = FINGERPRINT_PREFIX + fingerprint + FINGERPRINT_SUFFIX + "\n" + body;
        return new WikiPageContent(page, markdown, fingerprint);
    }

    static String extractFingerprint(String markdown) {
        if (markdown == null || !markdown.startsWith(FINGERPRINT_PREFIX)) {
            return "";
        }
        int end = markdown.indexOf(FINGERPRINT_SUFFIX);
        if (end < 0) {
            return "";
        }
        return markdown.substring(FINGERPRINT_PREFIX.length(), end);
    }

    private static String overview(RepositorySummary repository) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ")
                .append(text(repository.getName(), "Repository Overview"))
                .append("\n\n");
        markdown.append("## Summary\n\n")
                .append(text(repository.getSummary(), "No repository summary available."));
        markdown.append("\n\n## Facts\n\n");
        markdown.append("- Branch: ").append(text(repository.getBranch(), "unknown")).append("\n");
        markdown.append("- Commit: ")
                .append(text(repository.getCommitId(), "unknown"))
                .append("\n");
        markdown.append("- Files: ").append(repository.getFileCount()).append("\n");
        markdown.append("- Languages: ")
                .append(join(repository.getLanguages(), "unknown"))
                .append("\n");
        markdown.append("- Build files: ")
                .append(join(repository.getBuildFiles(), "none detected"))
                .append("\n");
        markdown.append("\n## Modules\n\n");
        for (ModuleSummary module : safe(repository.getModules())) {
            markdown.append("- ").append(text(module.getName(), module.getPath())).append(": ");
            markdown.append(text(module.getSummary(), "No module summary available.")).append("\n");
        }
        return markdown.toString();
    }

    private static String architecture(RepositorySummary repository, boolean mermaidEnabled) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Architecture\n\n");
        markdown.append("## System Shape\n\n");
        markdown.append(
                "The repository is organized around the following modules and source areas.\n\n");
        if (mermaidEnabled) {
            markdown.append("```mermaid\nflowchart TD\n");
            markdown.append("    repo[\"")
                    .append(escapeMermaid(text(repository.getName(), "Repository")))
                    .append("\"]\n");
            for (ModuleSummary module : safe(repository.getModules())) {
                String moduleId = mermaidId(module.getName());
                markdown.append("    repo --> ").append(moduleId).append("[\"");
                markdown.append(escapeMermaid(text(module.getName(), module.getPath())))
                        .append("\"]\n");
                for (ModuleSummary child : safe(module.getSubModules())) {
                    markdown.append("    ")
                            .append(moduleId)
                            .append(" --> ")
                            .append(mermaidId(child.getName()));
                    markdown.append("[\"")
                            .append(escapeMermaid(text(child.getName(), child.getPath())))
                            .append("\"]\n");
                }
            }
            markdown.append("```\n\n");
        }
        markdown.append("## Module Responsibilities\n\n");
        for (ModuleSummary module : safe(repository.getModules())) {
            markdown.append("### ").append(text(module.getName(), module.getPath())).append("\n\n");
            markdown.append("- Path: ").append(text(module.getPath(), "unknown")).append("\n");
            markdown.append("- Build system: ")
                    .append(text(module.getBuildSystem(), "unknown"))
                    .append("\n");
            markdown.append("- Responsibility: ")
                    .append(text(module.getSummary(), "No module summary available."))
                    .append("\n\n");
        }
        return markdown.toString();
    }

    private static String modules(RepositorySummary repository) {
        StringBuilder markdown = new StringBuilder("# Modules\n\n");
        for (ModuleSummary module : safe(repository.getModules())) {
            markdown.append("## ").append(text(module.getName(), module.getPath())).append("\n\n");
            markdown.append(text(module.getSummary(), "No module summary available."))
                    .append("\n\n");
            markdown.append("| File | Language | LOC | Summary |\n");
            markdown.append("| --- | --- | ---: | --- |\n");
            for (FileSummary file : safe(module.getFiles())) {
                markdown.append("| `").append(text(file.getPath(), file.getName())).append("` | ");
                markdown.append(text(file.getLanguage(), "unknown")).append(" | ");
                markdown.append(file.getLinesOfCode()).append(" | ");
                markdown.append(table(text(file.getSummary(), "No summary."))).append(" |\n");
            }
            appendClasses(markdown, module.getFiles());
        }
        return markdown.toString();
    }

    private static String dataflow(RepositorySummary repository, boolean mermaidEnabled) {
        StringBuilder markdown = new StringBuilder("# Data Flow\n\n");
        markdown.append(
                "This page summarizes inferred module-level data and control flow from summaries.\n\n");
        if (mermaidEnabled) {
            markdown.append("```mermaid\nsequenceDiagram\n");
            List<ModuleSummary> modules = safe(repository.getModules());
            for (ModuleSummary module : modules) {
                markdown.append("    participant ")
                        .append(mermaidId(module.getName()))
                        .append(" as ");
                markdown.append(escapeMermaid(text(module.getName(), module.getPath())))
                        .append("\n");
            }
            for (int index = 0; index + 1 < modules.size(); index++) {
                markdown.append("    ").append(mermaidId(modules.get(index).getName()));
                markdown.append("->>").append(mermaidId(modules.get(index + 1).getName()));
                markdown.append(": collaborates with\n");
            }
            markdown.append("```\n\n");
        }
        markdown.append("## Flow Notes\n\n");
        for (ModuleSummary module : safe(repository.getModules())) {
            markdown.append("- ").append(text(module.getName(), module.getPath())).append(": ");
            markdown.append(text(module.getSummary(), "No data-flow summary available."))
                    .append("\n");
        }
        return markdown.toString();
    }

    private static String review(RepositorySummary repository) {
        StringBuilder markdown = new StringBuilder("# Review\n\n");
        markdown.append("## Review Scope\n\n");
        markdown.append("- Repository: ")
                .append(text(repository.getName(), "unknown"))
                .append("\n");
        markdown.append("- Commit: ")
                .append(text(repository.getCommitId(), "unknown"))
                .append("\n");
        markdown.append("- Files analyzed: ").append(repository.getFileCount()).append("\n\n");
        markdown.append("## Findings\n\n");
        markdown.append("- Architecture documentation generated from repository summaries.\n");
        markdown.append("- Mermaid diagrams are generated where requested.\n");
        markdown.append(
                "- Manual review is still required before treating this wiki as authoritative.\n\n");
        markdown.append("## Suggested Follow-ups\n\n");
        markdown.append("- Verify module responsibilities against source code owners.\n");
        markdown.append("- Add missing summaries for files or modules with sparse descriptions.\n");
        markdown.append("- Re-run single-page generation after localized source changes.\n");
        return markdown.toString();
    }

    private static void appendClasses(StringBuilder markdown, List<FileSummary> files) {
        List<ClassSummary> classes =
                safe(files).stream()
                        .flatMap(file -> safe(file.getClasses()).stream())
                        .collect(Collectors.toList());
        if (classes.isEmpty()) {
            markdown.append("\n");
            return;
        }
        markdown.append("\n### Key Symbols\n\n");
        for (ClassSummary classSummary : classes) {
            markdown.append("- `").append(text(classSummary.getName(), "anonymous")).append("`: ");
            markdown.append(text(classSummary.getSummary(), text(classSummary.getKind(), "symbol")))
                    .append("\n");
        }
        markdown.append("\n");
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(content.hashCode());
        }
    }

    private static String mermaidId(String value) {
        String cleaned = text(value, "module").replaceAll("[^A-Za-z0-9_]", "_");
        if (cleaned.isBlank()) {
            return "module";
        }
        if (Character.isDigit(cleaned.charAt(0))) {
            return "m_" + cleaned;
        }
        return cleaned;
    }

    private static String escapeMermaid(String value) {
        return value.replace("\"", "'");
    }

    private static String table(String value) {
        return value.replace("|", "\\|").replace("\n", " ");
    }

    private static String join(List<String> values, String fallback) {
        List<String> safeValues = safe(values).stream().filter(value -> !value.isBlank()).toList();
        return safeValues.isEmpty() ? fallback : String.join(", ", safeValues);
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
