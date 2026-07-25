package com.agentscope.codewiki.cli;

import com.agentscope.codewiki.dify.DifyResponseMode;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

/** Parsed options for {@code codewiki ask}. */
final class AskCommandOptions {

    private static final int DEFAULT_TOP_K = 5;

    private final Path repoPath;
    private final String question;
    private final int topK;
    private final boolean difyEnabled;
    private final String difyBaseUrl;
    private final String difyApiKey;
    private final String difyUser;
    private final DifyResponseMode difyResponseMode;
    private final int difyTimeoutSeconds;
    private final int maxContextTokens;
    private final String sessionId;

    private AskCommandOptions(
            Path repoPath,
            String question,
            int topK,
            boolean difyEnabled,
            String difyBaseUrl,
            String difyApiKey,
            String difyUser,
            DifyResponseMode difyResponseMode,
            int difyTimeoutSeconds,
            int maxContextTokens,
            String sessionId) {
        this.repoPath = repoPath;
        this.question = question;
        this.topK = topK;
        this.difyEnabled = difyEnabled;
        this.difyBaseUrl = difyBaseUrl;
        this.difyApiKey = difyApiKey;
        this.difyUser = difyUser;
        this.difyResponseMode = difyResponseMode;
        this.difyTimeoutSeconds = difyTimeoutSeconds;
        this.maxContextTokens = maxContextTokens;
        this.sessionId = sessionId;
    }

    static AskCommandOptions parse(String[] args) {
        return parse(args, System.getenv());
    }

    static AskCommandOptions parse(String[] args, Map<String, String> env) {
        if (args == null || args.length == 0 || !"ask".equals(args[0])) {
            throw new IllegalArgumentException("Missing command: ask");
        }
        Path repo = null;
        String question = null;
        int topK = DEFAULT_TOP_K;
        boolean mock = false;
        boolean dify = false;
        String difyBaseUrl = textOrDefault(env.get("DIFY_BASE_URL"), env.get("DIFY_API_URL"));
        String difyApiKey = text(env.get("DIFY_API_KEY"));
        String difyUser =
                textOrDefault(env.get("DIFY_USER"), AnalysisCommandOptions.DEFAULT_DIFY_USER);
        DifyResponseMode difyResponseMode =
                DifyResponseMode.from(
                        textOrDefault(
                                env.get("CODEWIKI_DIFY_RESPONSE_MODE"),
                                env.get("DIFY_RESPONSE_MODE")));
        int difyTimeoutSeconds = AnalysisCommandOptions.DEFAULT_DIFY_TIMEOUT_SECONDS;
        int maxContextTokens =
                parsePositiveInt(
                        env.get("CODEWIKI_MAX_CONTEXT_TOKENS"),
                        AnalysisCommandOptions.DEFAULT_MAX_CONTEXT_TOKENS,
                        "CODEWIKI_MAX_CONTEXT_TOKENS");
        String sessionId = null;
        for (int index = 1; index < args.length; index++) {
            String arg = args[index];
            switch (arg) {
                case "--repo" -> repo = Path.of(requireValue(args, ++index, arg));
                case "--question" -> question = requireValue(args, ++index, arg);
                case "--top-k" ->
                        topK =
                                parsePositiveInt(
                                        requireValue(args, ++index, arg), DEFAULT_TOP_K, arg);
                case "--mock" -> mock = true;
                case "--dify" -> dify = true;
                case "--dify-base-url" -> difyBaseUrl = requireValue(args, ++index, arg);
                case "--dify-api-key" -> difyApiKey = requireValue(args, ++index, arg);
                case "--dify-user" -> difyUser = requireValue(args, ++index, arg);
                case "--dify-response-mode" ->
                        difyResponseMode = DifyResponseMode.from(requireValue(args, ++index, arg));
                case "--dify-timeout-seconds" ->
                        difyTimeoutSeconds =
                                parsePositiveInt(
                                        requireValue(args, ++index, arg),
                                        AnalysisCommandOptions.DEFAULT_DIFY_TIMEOUT_SECONDS,
                                        arg);
                case "--max-context-tokens" ->
                        maxContextTokens =
                                parsePositiveInt(
                                        requireValue(args, ++index, arg),
                                        AnalysisCommandOptions.DEFAULT_MAX_CONTEXT_TOKENS,
                                        arg);
                case "--session-id" -> sessionId = requireValue(args, ++index, arg);
                default -> throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }
        if (repo == null) {
            throw new IllegalArgumentException("Missing required --repo argument");
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Missing required --question argument");
        }
        if (mock && dify) {
            throw new IllegalArgumentException("Use either --mock or --dify, not both");
        }
        return new AskCommandOptions(
                repo,
                question,
                topK,
                dify,
                text(difyBaseUrl),
                text(difyApiKey),
                textOrDefault(difyUser, AnalysisCommandOptions.DEFAULT_DIFY_USER),
                difyResponseMode,
                difyTimeoutSeconds,
                maxContextTokens,
                text(sessionId));
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length || isFlag(args[index])) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return args[index];
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

    Path repoPath() {
        return repoPath;
    }

    String question() {
        return question;
    }

    int topK() {
        return topK;
    }

    boolean difyEnabled() {
        return difyEnabled;
    }

    String difyBaseUrl() {
        return difyBaseUrl;
    }

    String difyApiKey() {
        return difyApiKey;
    }

    String difyUser() {
        return difyUser;
    }

    DifyResponseMode difyResponseMode() {
        return difyResponseMode;
    }

    int difyTimeoutSeconds() {
        return difyTimeoutSeconds;
    }

    int maxContextTokens() {
        return maxContextTokens;
    }

    String sessionId() {
        return sessionId;
    }
}
