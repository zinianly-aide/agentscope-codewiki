package com.agentscope.codewiki.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodewikiRepositoryToolsTest {
    @TempDir Path repo;

    @Test
    void disabledByDefaultAndInvalidRootsExposeNoTools() {
        assertTrue(new CodewikiRepositoryTools(repo.toString(), false).descriptors().isEmpty());
        assertTrue(new CodewikiRepositoryTools("", true).descriptors().isEmpty());
        assertTrue(
                new CodewikiRepositoryTools(repo.resolve("missing").toString(), true)
                        .descriptors()
                        .isEmpty());
    }

    @Test
    void canSearchSymbolsCodeAndAnalyzeImpactOnlyInsideConfiguredRepository() throws Exception {
        Files.writeString(
                repo.resolve("Greeter.java"),
                "public class Greeter { public String hello() { return \"world\"; } }\n");
        Files.writeString(
                repo.resolve("Caller.java"),
                "public class Caller { public void run() { new Greeter().hello(); } }\n");
        Files.createDirectories(repo.resolve("node_modules"));
        Files.writeString(
                repo.resolve("node_modules/Secret.java"),
                "class Secret { void shouldNotAppear() {} }\n");
        CodewikiRepositoryTools tools = new CodewikiRepositoryTools(repo.toString(), true);
        assertEquals(4, tools.descriptors().size());

        var search = tools.call("codewiki_search_code", Map.of("query", "hello Greeter"));
        assertTrue(search.success(), String.valueOf(search.errorMessage()));
        assertFalse(String.valueOf(search.data()).contains("shouldNotAppear"));

        var symbols = tools.call("codewiki_search_symbol", Map.of("query", "Greeter"));
        assertTrue(symbols.success(), String.valueOf(symbols.errorMessage()));
        assertTrue(String.valueOf(symbols.data()).contains("Greeter.java"));
        assertFalse(String.valueOf(symbols.data()).contains("Secret.java"));

        var impact = tools.call("codewiki_analyze_impact", Map.of("file", "Greeter.java"));
        assertTrue(impact.success(), String.valueOf(impact.errorMessage()));
        assertTrue(String.valueOf(impact.data()).contains("riskLevel"));

        var unsafe = tools.call("codewiki_analyze_impact", Map.of("file", "../etc/passwd"));
        assertFalse(unsafe.success());
        assertEquals("INVALID_ARGUMENTS", unsafe.errorCode());
    }

    @Test
    void cachePersistsSymbolsAndOnlyReparsesChangedFiles() throws Exception {
        Path cachePath = Files.createTempFile(repo.getParent(), "codewiki-symbol-cache", ".json");
        Files.delete(cachePath);
        Files.writeString(repo.resolve("First.java"), "class First { void alpha() {} }\n");
        Files.writeString(repo.resolve("Second.java"), "class Second { void beta() {} }\n");
        try {
            var tools =
                    new CodewikiRepositoryTools(repo.toString(), true, "", cachePath.toString());
            var first = tools.call("codewiki_index_status", Map.of());
            assertTrue(first.success(), String.valueOf(first.errorMessage()));
            assertEquals(2, ((Map<?, ?>) first.data()).get("parsedFilesLastRefresh"));
            var repeat = tools.call("codewiki_index_status", Map.of());
            assertEquals(0, ((Map<?, ?>) repeat.data()).get("parsedFilesLastRefresh"));
            assertEquals(1L, ((Map<?, ?>) repeat.data()).get("totalParseBatches"));
            assertTrue(Files.isRegularFile(cachePath));

            var restarted =
                    new CodewikiRepositoryTools(repo.toString(), true, "", cachePath.toString());
            var warm = restarted.call("codewiki_index_status", Map.of());
            assertTrue(warm.success(), String.valueOf(warm.errorMessage()));
            assertEquals(0, ((Map<?, ?>) warm.data()).get("parsedFilesLastRefresh"));
            assertEquals(0L, ((Map<?, ?>) warm.data()).get("totalParseBatches"));
            assertTrue(
                    String.valueOf(
                                    restarted
                                            .call(
                                                    "codewiki_search_symbol",
                                                    Map.of("query", "First"))
                                            .data())
                            .contains("First.java"));

            // Hash catches a same-length edit even when mtime is restored.
            var modified = Files.getLastModifiedTime(repo.resolve("First.java"));
            Files.writeString(repo.resolve("First.java"), "class First { void omega() {} }\n");
            Files.setLastModifiedTime(repo.resolve("First.java"), modified);
            var updated = restarted.call("codewiki_index_status", Map.of());
            assertEquals(1, ((Map<?, ?>) updated.data()).get("parsedFilesLastRefresh"));
            assertTrue(
                    String.valueOf(
                                    restarted
                                            .call(
                                                    "codewiki_search_symbol",
                                                    Map.of("query", "omega"))
                                            .data())
                            .contains("omega"));
            assertFalse(
                    String.valueOf(
                                    restarted
                                            .call(
                                                    "codewiki_search_symbol",
                                                    Map.of("query", "alpha"))
                                            .data())
                            .contains("First.java"));

            Files.delete(repo.resolve("Second.java"));
            var deleted = restarted.call("codewiki_index_status", Map.of());
            assertEquals(1, ((Map<?, ?>) deleted.data()).get("fileCount"));
            assertFalse(
                    String.valueOf(
                                    restarted
                                            .call(
                                                    "codewiki_search_symbol",
                                                    Map.of("query", "Second"))
                                            .data())
                            .contains("Second.java"));
        } finally {
            Files.deleteIfExists(cachePath);
        }
    }

    @Test
    void explicitCacheInsideRepositoryIsRejected() {
        boolean rejected = false;
        try {
            new CodewikiRepositoryTools(
                    repo.toString(), true, "", repo.resolve(".codewiki-cache.json").toString());
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue(rejected);
    }

    @Test
    void symlinkDiskCacheTargetIsRejected() throws Exception {
        Path outside = Files.createTempFile(repo.getParent(), "sensitive-index-target", ".json");
        Path link = repo.getParent().resolve("codewiki-symbol-symlink-" + System.nanoTime());
        try {
            Files.writeString(outside, "DoNotOverwrite");
            Files.createSymbolicLink(link, outside);
            boolean rejected = false;
            try {
                new CodewikiRepositoryTools(repo.toString(), true, "", link.toString());
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            assertTrue(rejected);
            assertEquals("DoNotOverwrite", Files.readString(outside));
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void explicitlyNamedClassIsRankedAheadOfIncidentalReferences() throws Exception {
        Files.writeString(
                repo.resolve("ImpactAnalyzer.java"),
                "public class ImpactAnalyzer { void analyze() { } }\n");
        Files.writeString(
                repo.resolve("ImpactReport.java"),
                "public class ImpactReport { String text = \"ImpactAnalyzer ImpactAnalyzer\"; }\n");
        var tools = new CodewikiRepositoryTools(repo.toString(), true);
        var answer =
                tools.call(
                        "codewiki_search_code", Map.of("query", "How does ImpactAnalyzer work?"));
        assertTrue(answer.success(), String.valueOf(answer.errorMessage()));
        var data = (Map<?, ?>) answer.data();
        var matches = (java.util.List<?>) data.get("results");
        Map<?, ?> first = (Map<?, ?>) matches.get(0);
        assertEquals("ImpactAnalyzer.java", first.get("path"));
        assertEquals(1, first.get("startLine"));
        assertTrue((int) first.get("endLine") >= (int) first.get("startLine"));
    }

    @Test
    void doesNotReadSymbolicLinksOrOversizedSourceFiles() throws Exception {
        Path outside = Files.createTempFile("codewiki-outside-", ".java");
        try {
            Files.writeString(outside, "class PrivateOutOfScope {}\n");
            Files.createSymbolicLink(repo.resolve("Leak.java"), outside);
            Files.writeString(repo.resolve("Allowed.java"), "class Allowed {}\n");
            var tools = new CodewikiRepositoryTools(repo.toString(), true);
            var result = tools.call("codewiki_search_symbol", Map.of("query", "PrivateOutOfScope"));
            assertTrue(result.success(), String.valueOf(result.errorMessage()));
            assertFalse(String.valueOf(result.data()).contains("PrivateOutOfScope"));
            Files.writeString(repo.resolve("TooLarge.java"), "x".repeat(140_000));
            var rejected = tools.call("codewiki_search_code", Map.of("query", "Allowed"));
            assertFalse(rejected.success());
            assertEquals("REPOSITORY_LIMIT", rejected.errorCode());
        } finally {
            Files.deleteIfExists(outside);
        }
    }
}
