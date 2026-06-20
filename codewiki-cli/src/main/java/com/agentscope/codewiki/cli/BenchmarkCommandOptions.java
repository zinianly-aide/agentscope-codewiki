package com.agentscope.codewiki.cli;

import java.nio.file.Path;

/** Parsed options for {@code codewiki benchmark}. */
record BenchmarkCommandOptions(Path repoPath, Path outputDirectory) {

    static BenchmarkCommandOptions parse(String[] args) {
        if (args == null || args.length == 0 || !"benchmark".equals(args[0])) {
            throw new IllegalArgumentException("Missing command: benchmark");
        }
        Path repo = null;
        Path output = null;
        for (int index = 1; index < args.length; index++) {
            String arg = args[index];
            switch (arg) {
                case "--repo" -> repo = Path.of(requireValue(args, ++index, arg));
                case "--output" -> output = Path.of(requireValue(args, ++index, arg));
                default -> throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }
        if (repo == null) {
            throw new IllegalArgumentException("Missing required --repo argument");
        }
        if (output == null) {
            throw new IllegalArgumentException("Missing required --output argument");
        }
        return new BenchmarkCommandOptions(repo, output);
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return args[index];
    }
}
