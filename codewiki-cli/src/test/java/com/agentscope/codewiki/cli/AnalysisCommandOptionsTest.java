package com.agentscope.codewiki.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentscope.codewiki.agent.WikiPage;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests for CodeWiki analysis command option parsing. */
class AnalysisCommandOptionsTest {

    @Test
    void shouldDefaultToIncrementalWikiWithAllMermaidPages() {
        AnalysisCommandOptions options =
                AnalysisCommandOptions.parse(
                        new String[] {"analyze", "--repo", ".", "--output", "wiki"}, Map.of());

        assertTrue(options.wikiGenerationOptions().isIncremental());
        assertTrue(options.wikiGenerationOptions().isMermaidEnabled());
        assertEquals(EnumSet.allOf(WikiPage.class), options.wikiGenerationOptions().getPages());
    }

    @Test
    void shouldParseSinglePageFullRegenerationWithoutMermaid() {
        AnalysisCommandOptions options =
                AnalysisCommandOptions.parse(
                        new String[] {
                            "analyze",
                            "--repo",
                            ".",
                            "--output",
                            "wiki",
                            "--wiki-full",
                            "--wiki-page",
                            "dataflow.md",
                            "--wiki-no-mermaid"
                        },
                        Map.of());

        assertFalse(options.wikiGenerationOptions().isIncremental());
        assertFalse(options.wikiGenerationOptions().isMermaidEnabled());
        assertEquals(Set.of(WikiPage.DATAFLOW), options.wikiGenerationOptions().getPages());
    }

    @Test
    void shouldRejectUnknownWikiPage() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                AnalysisCommandOptions.parse(
                                        new String[] {
                                            "analyze",
                                            "--repo",
                                            ".",
                                            "--output",
                                            "wiki",
                                            "--wiki-page",
                                            "unknown"
                                        },
                                        Map.of()));

        assertTrue(exception.getMessage().contains("Unknown wiki page"));
    }
}
