package com.agentscope.codewiki.agent;

import com.agentscope.codewiki.core.RepositorySummary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Generates DeepWiki-style Markdown pages for a repository summary. */
public class RepositoryWikiGenerator {

    public WikiGenerationResult generate(
            RepositorySummary repository, Path outputDirectory, WikiGenerationOptions options) {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        if (outputDirectory == null) {
            throw new IllegalArgumentException("outputDirectory must not be null");
        }
        WikiGenerationOptions effectiveOptions =
                options == null ? WikiGenerationOptions.fullRegeneration() : options;
        try {
            Files.createDirectories(outputDirectory);
            List<WikiPageResult> results = new ArrayList<>();
            for (WikiPage page : effectiveOptions.getPages()) {
                results.add(writePage(repository, outputDirectory, effectiveOptions, page));
            }
            return new WikiGenerationResult(results);
        } catch (IOException exception) {
            throw new WikiGenerationException("Failed to generate repository wiki", exception);
        }
    }

    public WikiPageResult regeneratePage(
            RepositorySummary repository, Path outputDirectory, WikiPage page) {
        return generate(repository, outputDirectory, WikiGenerationOptions.singlePage(page))
                .pages()
                .getFirst();
    }

    private WikiPageResult writePage(
            RepositorySummary repository,
            Path outputDirectory,
            WikiGenerationOptions options,
            WikiPage page)
            throws IOException {
        WikiPageContent content = WikiMarkdown.page(repository, page, options.isMermaidEnabled());
        Path pagePath = outputDirectory.resolve(page.getFileName());
        if (options.isIncremental() && Files.exists(pagePath)) {
            String existing = Files.readString(pagePath, StandardCharsets.UTF_8);
            if (content.fingerprint().equals(WikiMarkdown.extractFingerprint(existing))) {
                return new WikiPageResult(page, pagePath, false, content.fingerprint());
            }
        }
        Files.writeString(pagePath, content.markdown(), StandardCharsets.UTF_8);
        return new WikiPageResult(page, pagePath, true, content.fingerprint());
    }
}
