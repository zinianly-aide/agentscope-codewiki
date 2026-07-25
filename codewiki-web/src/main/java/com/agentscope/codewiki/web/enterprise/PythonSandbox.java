package com.agentscope.codewiki.web.enterprise;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Docker-backed Python sandbox for exact read-only calculations. */
public class PythonSandbox {
    static final int MAX_CODE_LENGTH = 8000;
    static final int MAX_OUTPUT_BYTES = 64 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final String TOOL_NAME = "run_python";
    private static final Logger LOGGER = LoggerFactory.getLogger(PythonSandbox.class);
    private final ProcessExecutor processExecutor;

    public PythonSandbox(ProcessExecutor processExecutor) {
        this.processExecutor = processExecutor;
    }

    public ToolResult<String> runPython(String code) {
        long start = System.nanoTime();
        if (code == null || code.isBlank()) {
            return failure(start, "EMPTY_CODE", "code must not be blank");
        }
        if (code.length() > MAX_CODE_LENGTH) {
            return failure(start, "CODE_TOO_LARGE", "code exceeds 8000 characters");
        }
        String denied = deniedToken(code);
        if (denied != null) {
            return failure(
                    start, "DISABLED_OPERATION", "code contains disabled operation: " + denied);
        }
        List<String> command = buildDockerCommand(code);
        try {
            ProcessResult result = processExecutor.execute(command, TIMEOUT, MAX_OUTPUT_BYTES);
            if (result.timedOut()) {
                return failure(start, "TIMEOUT", "python execution timed out after 20 seconds");
            }
            String suffix = result.truncated() ? "\n[output truncated to 64KB]" : "";
            if (result.exitCode() != 0) {
                return failure(
                        start,
                        "NON_ZERO_EXIT",
                        "python exited with code " + result.exitCode() + ": " + result.output());
            }
            ToolResult<String> success =
                    ToolResult.success(
                            TOOL_NAME,
                            result.output() + suffix,
                            "docker:python:3.12-alpine",
                            elapsed(start));
            logResult(success);
            return success;
        } catch (IOException ex) {
            return failure(start, "DOCKER_UNAVAILABLE", "Docker is not available or not running");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failure(start, "INTERRUPTED", "python execution was interrupted");
        }
    }

    List<String> buildDockerCommand(String code) {
        return List.of(
                "docker",
                "run",
                "--rm",
                "--network=none",
                "--memory=256m",
                "--cpus=1",
                "--pids-limit=64",
                "--read-only",
                "--cap-drop=ALL",
                "--security-opt=no-new-privileges",
                "--user=65534:65534",
                "--tmpfs",
                "/tmp:rw,noexec,nosuid,size=64m",
                "--env",
                "PYTHONNOUSERSITE=1",
                "python:3.12-alpine",
                "python",
                "-I",
                "-c",
                code);
    }

    private ToolResult<String> failure(long start, String code, String message) {
        ToolResult<String> result =
                ToolResult.failure(
                        TOOL_NAME, "docker:python:3.12-alpine", elapsed(start), code, message);
        logResult(result);
        return result;
    }

    private static void logResult(ToolResult<?> result) {
        MDC.put("toolName", result.toolName());
        MDC.put("durationMs", Long.toString(result.durationMs()));
        MDC.put("resultStatus", result.success() ? "success" : "failure");
        try {
            LOGGER.info(
                    "tool execution completed toolName={} iteration={} durationMs={} resultStatus={}",
                    result.toolName(),
                    MDC.get("iteration"),
                    result.durationMs(),
                    MDC.get("resultStatus"));
        } finally {
            MDC.remove("toolName");
            MDC.remove("durationMs");
            MDC.remove("resultStatus");
        }
    }

    private static long elapsed(long start) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private static String deniedToken(String code) {
        String normalized = code.toLowerCase(Locale.ROOT);
        for (String token :
                List.of(
                        "subprocess",
                        "os.system",
                        "socket",
                        "requests",
                        "urllib",
                        "pip install",
                        "apt",
                        "apk")) {
            if (normalized.contains(token)) {
                return token;
            }
        }
        return null;
    }
}
