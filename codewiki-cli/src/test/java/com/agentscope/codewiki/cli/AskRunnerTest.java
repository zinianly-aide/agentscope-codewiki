package com.agentscope.codewiki.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AskRunnerTest {

    @TempDir private Path tempDir;

    @Test
    void askRunnerUsesTopKRetrievedContextAndPromptBudget() throws Exception {
        Path repo = createRepository();
        AskCommandOptions options =
                AskCommandOptions.parse(
                        new String[] {
                            "ask",
                            "--repo",
                            repo.toString(),
                            "--question",
                            "How does LoginService authenticate users?",
                            "--top-k",
                            "1",
                            "--max-context-tokens",
                            "120",
                            "--mock"
                        },
                        Map.of());

        AskRunner.AskResult result = new AskRunner().run(options);

        assertEquals(1, result.contextCount());
        assertTrue(result.promptTokens() <= 120);
        assertTrue(result.prompt().contains("LoginService"));
        assertFalse(result.prompt().contains("BillingService"));
        assertTrue(result.answer().contains("TopK contexts used"));
    }

    @Test
    void askCliCommandIsAvailable() throws Exception {
        Path repo = createRepository();

        int exitCode =
                new CodewikiCli()
                        .execute(
                                new String[] {
                                    "ask",
                                    "--repo",
                                    repo.toString(),
                                    "--question",
                                    "login flow",
                                    "--top-k",
                                    "1",
                                    "--mock"
                                });

        assertEquals(0, exitCode);
    }

    private Path createRepository() throws Exception {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo.resolve("src/main/java/example"));
        Files.writeString(
                repo.resolve("src/main/java/example/LoginService.java"),
                "package example;\n"
                        + "public class LoginService {\n"
                        + "  public boolean authenticate(String token) { return token != null; }\n"
                        + "}\n");
        Files.writeString(
                repo.resolve("src/main/java/example/BillingService.java"),
                "package example;\n"
                        + "public class BillingService {\n"
                        + "  public int invoice() { return 42; }\n"
                        + "}\n");
        return repo;
    }
}
