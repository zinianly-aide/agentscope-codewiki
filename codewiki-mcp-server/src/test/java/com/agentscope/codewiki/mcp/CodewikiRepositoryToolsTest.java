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
        assertEquals(3, tools.descriptors().size());

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
