package com.agentscope.codewiki.cli;

/** Command-line entry point for CodeWiki acceptance workflows. */
public class CodewikiCli {

    public static void main(String[] args) {
        int exitCode = new CodewikiCli().execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public int execute(String[] args) {
        try {
            if (args != null && args.length > 0 && "benchmark".equals(args[0])) {
                BenchmarkCommandOptions options = BenchmarkCommandOptions.parse(args);
                new BenchmarkRunner().run(options.repoPath(), options.outputDirectory());
                return 0;
            }
            AnalysisCommandOptions options = AnalysisCommandOptions.parse(args);
            if (options.reviewDiff()) {
                new DiffReviewCliRunner().run(options);
            } else {
                new EndToEndAnalysisRunner().run(options);
            }
            return 0;
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            printUsage();
            return 2;
        } catch (Exception exception) {
            System.err.println("codewiki analyze failed: " + exception.getMessage());
            return 1;
        }
    }

    private void printUsage() {
        System.err.println(
                "Usage: codewiki analyze --repo /path/to/repo --output ./wiki "
                        + "[--dry-run] [--mock|--dify] [--log ./run.log] "
                        + "[--dify-base-url URL] [--dify-api-key KEY] [--dify-user USER] "
                        + "[--dify-timeout-seconds N] [--max-context-tokens N] [--session-id ID]\n"
                        + "       codewiki analyze --repo /path/to/repo --output ./review --review-diff "
                        + "[--base-ref main] [--head-ref HEAD] [--changed-only] [--mock|--dify]\n"
                        + "       codewiki benchmark --repo /path/to/repo --output ./benchmark");
    }
}
