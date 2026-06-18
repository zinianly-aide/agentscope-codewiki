package com.agentscope.codewiki.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentscope.codewiki.core.ClassSummary;
import com.agentscope.codewiki.core.FileSummary;
import com.agentscope.codewiki.core.ModuleSummary;
import com.agentscope.codewiki.core.RepositorySummary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for repository wiki generation. */
class RepositoryWikiGeneratorTest {

    @TempDir private Path tempDir;

    @Test
    void shouldGenerateCanonicalWikiPagesWithMermaid() throws Exception {
        RepositoryWikiGenerator generator = new RepositoryWikiGenerator();

        WikiGenerationResult result =
                generator.generate(
                        sampleRepository(), tempDir, WikiGenerationOptions.fullRegeneration());

        assertEquals(5, result.pages().size());
        assertEquals(5, result.writtenCount());
        assertTrue(Files.exists(tempDir.resolve("overview.md")));
        assertTrue(Files.exists(tempDir.resolve("architecture.md")));
        assertTrue(Files.exists(tempDir.resolve("modules.md")));
        assertTrue(Files.exists(tempDir.resolve("dataflow.md")));
        assertTrue(Files.exists(tempDir.resolve("review.md")));
        assertTrue(Files.readString(tempDir.resolve("architecture.md")).contains("```mermaid"));
        assertTrue(Files.readString(tempDir.resolve("dataflow.md")).contains("sequenceDiagram"));
        assertTrue(Files.readString(tempDir.resolve("modules.md")).contains("OrderService"));
    }

    @Test
    void shouldSkipUnchangedPagesDuringIncrementalGeneration() {
        RepositoryWikiGenerator generator = new RepositoryWikiGenerator();
        generator.generate(sampleRepository(), tempDir, WikiGenerationOptions.fullRegeneration());

        WikiGenerationResult result =
                generator.generate(
                        sampleRepository(), tempDir, WikiGenerationOptions.incremental());

        assertEquals(0, result.writtenCount());
        assertEquals(5, result.skippedCount());
    }

    @Test
    void shouldRegenerateSinglePageOnly() throws Exception {
        RepositoryWikiGenerator generator = new RepositoryWikiGenerator();

        WikiGenerationResult result =
                generator.generate(
                        sampleRepository(),
                        tempDir,
                        WikiGenerationOptions.builder()
                                .page(WikiPage.MODULES)
                                .mermaidEnabled(false)
                                .build());

        assertEquals(1, result.pages().size());
        assertTrue(Files.exists(tempDir.resolve("modules.md")));
        assertFalse(Files.exists(tempDir.resolve("overview.md")));
        assertTrue(
                Files.readString(tempDir.resolve("modules.md"))
                        .contains("| File | Language | LOC | Summary |"));
    }

    @Test
    void shouldUpdateChangedPageInIncrementalMode() throws Exception {
        RepositoryWikiGenerator generator = new RepositoryWikiGenerator();
        RepositorySummary repository = sampleRepository();
        generator.generate(repository, tempDir, WikiGenerationOptions.fullRegeneration());
        repository.setSummary("Updated summary for incremental generation.");

        WikiGenerationResult result =
                generator.generate(
                        repository,
                        tempDir,
                        WikiGenerationOptions.builder()
                                .incremental(true)
                                .pages(EnumSet.of(WikiPage.OVERVIEW))
                                .build());

        assertEquals(1, result.writtenCount());
        assertTrue(Files.readString(tempDir.resolve("overview.md")).contains("Updated summary"));
    }

    private RepositorySummary sampleRepository() {
        ClassSummary orderClass = new ClassSummary();
        orderClass.setName("OrderService");
        orderClass.setKind("class");
        orderClass.setSummary("Coordinates order checkout.");

        FileSummary orderFile = new FileSummary();
        orderFile.setName("OrderService.java");
        orderFile.setPath("codewiki-agent/src/main/java/OrderService.java");
        orderFile.setLanguage("Java");
        orderFile.setLinesOfCode(120);
        orderFile.setSummary("Order checkout workflow.");
        orderFile.setClasses(List.of(orderClass));

        ModuleSummary agentModule = new ModuleSummary();
        agentModule.setName("codewiki-agent");
        agentModule.setPath("codewiki-agent");
        agentModule.setBuildSystem("Maven");
        agentModule.setSummary("Runs wiki and agent orchestration workflows.");
        agentModule.setFiles(List.of(orderFile));

        ModuleSummary retrieverModule = new ModuleSummary();
        retrieverModule.setName("codewiki-retriever");
        retrieverModule.setPath("codewiki-retriever");
        retrieverModule.setBuildSystem("Maven");
        retrieverModule.setSummary("Builds hybrid retrieval context for questions.");

        RepositorySummary repository = new RepositorySummary();
        repository.setName("agentscope-codewiki");
        repository.setBranch("main");
        repository.setCommitId("abc123");
        repository.setLanguages(List.of("Java"));
        repository.setFileCount(42);
        repository.setBuildFiles(List.of("pom.xml"));
        repository.setReadmePath("README.md");
        repository.setSummary("DeepWiki-style code analysis for AgentScope Java projects.");
        repository.setModules(List.of(agentModule, retrieverModule));
        return repository;
    }
}
