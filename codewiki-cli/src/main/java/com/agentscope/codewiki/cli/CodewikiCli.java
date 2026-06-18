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
            AnalysisCommandOptions options = AnalysisCommandOptions.parse(args);
            new EndToEndAnalysisRunner().run(options);
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
                        + "[--dry-run] [--mock] [--log ./run.log]");
    }
}
