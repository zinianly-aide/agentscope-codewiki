package com.agentscope.codewiki.web.enterprise;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/** Lightweight Docker availability checks for actuator health details. */
public class DockerHealthService {
    private final ProcessExecutor processExecutor;

    public DockerHealthService(ProcessExecutor processExecutor) {
        this.processExecutor = processExecutor;
    }

    public boolean dockerAvailable() {
        return run(List.of("docker", "version", "--format", "{{.Server.Version}}"));
    }

    public boolean pythonImagePresent() {
        return run(List.of("docker", "image", "inspect", "python:3.12-alpine"));
    }

    private boolean run(List<String> command) {
        try {
            ProcessResult result = processExecutor.execute(command, Duration.ofSeconds(3), 2048);
            return !result.timedOut() && result.exitCode() == 0;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
