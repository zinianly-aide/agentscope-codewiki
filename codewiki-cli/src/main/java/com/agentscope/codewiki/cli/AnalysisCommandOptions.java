package com.agentscope.codewiki.cli;

import java.nio.file.Path;
import java.util.Arrays;

/** Parsed options for {@code codewiki analyze}. */
public final class AnalysisCommandOptions {

    private final Path repoPath;
    private final Path outputDirectory;
    private final boolean dryRun;
    private final boolean mockOnly;
    private final Path logPath;

    private AnalysisCommandOptions(
            Path repoPath, Path outputDirectory, boolean dryRun, boolean mockOnly, Path logPath) {
        this.repoPath = repoPath;
        this.outputDirectory = outputDirectory;
        this.dryRun = dryRun;
        this.mockOnly = mockOnly;
        this.logPath = logPath;
    }

    public static AnalysisCommandOptions parse(String[] args) {
        if (args == null || args.length == 0 || !"analyze".equals(args[0])) {
            throw new IllegalArgumentException("Missing command: analyze");
        }
        Path repo = null;
        Path output = null;
        boolean dryRun = false;
        boolean mock = false;
        boolean dify = false;
        Path log = null;
        for (int index = 1; index < args.length; index++) {
            String arg = args[index];
            switch (arg) {
                case "--repo" -> repo = Path.of(requireValue(args, ++index, arg));
                case "--output" -> output = Path.of(requireValue(args, ++index, arg));
                case "--log" -> log = Path.of(requireValue(args, ++index, arg));
                case "--dry-run" -> dryRun = true;
                case "--mock" -> mock = true;
                case "--dify" -> dify = true;
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
        boolean effectiveMock = !dify;
        if (mock) {
            effectiveMock = true;
        }
        Path effectiveLog = log == null ? output.resolve("codewiki-run.log") : log;
        return new AnalysisCommandOptions(repo, output, dryRun, effectiveMock, effectiveLog);
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length
                || Arrays.asList("--repo", "--output", "--log").contains(args[index])) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return args[index];
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

    public Path logPath() {
        return logPath;
    }
}
