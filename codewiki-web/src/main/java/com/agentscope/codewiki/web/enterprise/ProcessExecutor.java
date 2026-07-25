package com.agentscope.codewiki.web.enterprise;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/** Minimal process abstraction for testing Docker sandbox execution. */
public interface ProcessExecutor {
    ProcessResult execute(List<String> command, Duration timeout, int outputLimitBytes)
            throws IOException, InterruptedException;
}
