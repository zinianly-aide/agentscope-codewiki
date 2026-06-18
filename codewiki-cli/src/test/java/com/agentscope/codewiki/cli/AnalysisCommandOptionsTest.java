package com.agentscope.codewiki.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for CLI/runtime configuration loading. */
class AnalysisCommandOptionsTest {

    @Test
    void loadsDifyRuntimeConfigFromEnvironment() {
        AnalysisCommandOptions options =
                AnalysisCommandOptions.parse(
                        new String[] {"analyze", "--repo", "/repo", "--output", "wiki", "--dify"},
                        Map.of(
                                "DIFY_BASE_URL",
                                "https://dify.example",
                                "DIFY_API_KEY",
                                "env-key",
                                "DIFY_USER",
                                "env-user",
                                "CODEWIKI_MAX_CONTEXT_TOKENS",
                                "12000"));

        assertTrue(options.difyEnabled());
        assertFalse(options.mockOnly());
        assertEquals("https://dify.example", options.difyBaseUrl());
        assertEquals("env-key", options.difyApiKey());
        assertEquals("env-user", options.difyUser());
        assertEquals(12000, options.maxContextTokens());
    }

    @Test
    void cliArgumentsOverrideEnvironmentConfig() {
        AnalysisCommandOptions options =
                AnalysisCommandOptions.parse(
                        new String[] {
                            "analyze",
                            "--repo",
                            "/repo",
                            "--output",
                            "wiki",
                            "--dify",
                            "--dify-base-url",
                            "https://cli.example",
                            "--dify-api-key",
                            "cli-key",
                            "--dify-user",
                            "cli-user",
                            "--dify-timeout-seconds",
                            "7",
                            "--max-context-tokens",
                            "4096",
                            "--session-id",
                            "session-123"
                        },
                        Map.of(
                                "DIFY_BASE_URL",
                                "https://env.example",
                                "DIFY_API_KEY",
                                "env-key",
                                "DIFY_USER",
                                "env-user",
                                "CODEWIKI_MAX_CONTEXT_TOKENS",
                                "12000"));

        assertEquals("https://cli.example", options.difyBaseUrl());
        assertEquals("cli-key", options.difyApiKey());
        assertEquals("cli-user", options.difyUser());
        assertEquals(7, options.difyTimeoutSeconds());
        assertEquals(4096, options.maxContextTokens());
        assertEquals("session-123", options.sessionId());
    }
}
