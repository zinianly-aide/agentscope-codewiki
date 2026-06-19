package com.agentscope.codewiki.cli;

import com.agentscope.codewiki.agent.WikiGenerationOptions;
import com.agentscope.codewiki.agent.WikiPage;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;

/** Parsed options for {@code codewiki analyze}. */
public final class AnalysisCommandOptions {

    static final int DEFAULT_DIFY_TIMEOUT_SECONDS = 60;
    static final int DEFAULT_MAX_CONTEXT_TOKENS = 32000;
    static final String DEFAULT_DIFY_USER = "codewiki-cli";

    private final Path repoPath;
    private final Path outputDirectory;
    private final boolean dryRun;
    private final boolean mockOnly;
    private final boolean difyEnabled;
    private final Path logPath;
    private final String difyBaseUrl;
    private final String difyApiKey;
    private final String difyUser;
    private final int difyTimeoutSeconds;
    private final int maxContextTokens;
    private final String sessionId;
    private final WikiGenerationOptions wikiGenerationOptions;

    private AnalysisCommandOptions(
            Path repoPath,
            Path outputDirectory,
            boolean dryRun,
            boolean mockOnly,
            boolean difyEnabled,
            Path logPath,
            String difyBaseUrl,
            String difyApiKey,
            String difyUser,
            int difyTimeoutSeconds,
            int maxContextTokens,
            String sessionId,
            WikiGenerationOptions wikiGenerationOptions) {
        this.repoPath = repoPath;
        this.outputDirectory = outputDirectory;
        this.dryRun = dryRun;
        this.mockOnly = mockOnly;
        this.difyEnabled = difyEnabled;
        this.logPath = logPath;
        this.difyBaseUrl = difyBaseUrl;
        this.difyApiKey = difyApiKey;
        this.difyUser = difyUser;
        this.difyTimeoutSeconds = difyTimeoutSeconds;
        this.maxContextTokens = maxContextTokens;
        this.sessionId = sessionId;
        this.wikiGenerationOptions =
                wikiGenerationOptions == null
                        ? WikiGenerationOptions.incremental()
                        : wikiGenerationOptions;
    }

    public static AnalysisCommandOptions parse(String[] args) {
        return parse(args, System.getenv());
    }

    static AnalysisCommandOptions parse(String[] args, Map<String, String> env) {
        if (args == null || args.length == 0 || !"analyze".equals(args[0])) {
            throw new IllegalArgumentException("Missing command: analyze");
        }
        Path repo = null;
        Path output = null;
        boolean dryRun = false;
        boolean mock = false;
        boolean dify = false;
        Path log = null;
        String difyBaseUrl = text(env.get("DIFY_BASE_URL"));
        String difyApiKey = text(env.get("DIFY_API_KEY"));
        String difyUser = textOrDefault(env.get("DIFY_USER"), DEFAULT_DIFY_USER);
        int difyTimeoutSeconds = DEFAULT_DIFY_TIMEOUT_SECONDS;
        int maxContextTokens =
                parsePositiveInt(
                        env.get("CODEWIKI_MAX_CONTEXT_TOKENS"),
                        DEFAULT_MAX_CONTEXT_TOKENS,
                        "CODEWIKI_MAX_CONTEXT_TOKENS");
        String sessionId = null;
        boolean wikiIncremental = true;
        boolean mermaidEnabled = true;
        EnumSet<WikiPage> wikiPages = EnumSet.allOf(WikiPage.class);
        for (int index = 1; index < args.length; index++) {
            String arg = args[index];
            switch (arg) {
                case "--repo" -> repo = Path.of(requireValue(args, ++index, arg));
                case "--output" -> output = Path.of(requireValue(args, ++index, arg));
                case "--log" -> log = Path.of(requireValue(args, ++index, arg));
                case "--dry-run" -> dryRun = true;
                case "--mock" -> mock = true;
                case "--dify" -> dify = true;
                case "--dify-base-url" -> difyBaseUrl = requireValue(args, ++index, arg);
                case "--dify-api-key" -> difyApiKey = requireValue(args, ++index, arg);
                case "--dify-user" -> difyUser = requireValue(args, ++index, arg);
                case "--dify-timeout-seconds" ->
                        difyTimeoutSeconds =
                                parsePositiveInt(
                                        requireValue(args, ++index, arg),
                                        DEFAULT_DIFY_TIMEOUT_SECONDS,
                                        arg);
                case "--max-context-tokens" ->
                        maxContextTokens =
                                parsePositiveInt(
                                        requireValue(args, ++index, arg),
                                        DEFAULT_MAX_CONTEXT_TOKENS,
                                        arg);
                case "--session-id" -> sessionId = requireValue(args, ++index, arg);
                case "--wiki-incremental" -> wikiIncremental = true;
                case "--wiki-full" -> wikiIncremental = false;
                case "--wiki-page" ->
                        wikiPages = EnumSet.of(parseWikiPage(requireValue(args, ++index, arg)));
                case "--wiki-no-mermaid" -> mermaidEnabled = false;
                default -> throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }
        if (repo == null) {
            throw new IllegalArgumentException("Missing required --repo argument");
        }
        if (output == null) {
            throw new IllegalArgumentException("Missing required --output argument");
        }
        if (mock && dify) {
            throw new IllegalArgumentException("Use either --mock or --dify, not both");
        }
        boolean effectiveDify = dify;
        boolean effectiveMock = mock || !effectiveDify;
        Path effectiveLog = log == null ? output.resolve("codewiki-run.log") : log;
        WikiGenerationOptions wikiOptions =
                WikiGenerationOptions.builder()
                        .incremental(wikiIncremental)
                        .mermaidEnabled(mermaidEnabled)
                        .pages(wikiPages)
                        .build();
        return new AnalysisCommandOptions(
                repo,
                output,
                dryRun,
                effectiveMock,
                effectiveDify,
                effectiveLog,
                text(difyBaseUrl),
                text(difyApiKey),
                textOrDefault(difyUser, DEFAULT_DIFY_USER),
                difyTimeoutSeconds,
                maxContextTokens,
                text(sessionId),
                wikiOptions);
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length || isFlag(args[index])) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return args[index];
    }

    private static WikiPage parseWikiPage(String value) {
        return WikiPage.fromInput(value)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Unknown wiki page: "
                                                + value
                                                + " (expected overview, architecture, modules, dataflow, or review)"));
    }

    private static boolean isFlag(String value) {
        return value != null
                && value.startsWith("--")
                && !Arrays.asList("--dify-api-key").contains(value);
    }

    private static int parsePositiveInt(String value, int fallback, String name) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a positive integer", exception);
        }
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String textOrDefault(String value, String fallback) {
        String text = text(value);
        return text == null ? fallback : text;
    }

    public Path repoPath() {
        return repoPath;
    }

    public Path outputDirectory() {
        return outputDirectory;
    }

    public boolean dryRun() {
        return dryRun;
    }

    public boolean mockOnly() {
        return mockOnly;
    }

    public boolean difyEnabled() {
        return difyEnabled;
    }

    public Path logPath() {
        return logPath;
    }

    public String difyBaseUrl() {
        return difyBaseUrl;
    }

    public String difyApiKey() {
        return difyApiKey;
    }

    public String difyUser() {
        return difyUser;
    }

    public int difyTimeoutSeconds() {
        return difyTimeoutSeconds;
    }

    public int maxContextTokens() {
        return maxContextTokens;
    }

    public String sessionId() {
        return sessionId;
    }

    public WikiGenerationOptions wikiGenerationOptions() {
        return wikiGenerationOptions;
    }
}
