package com.agentscope.codewiki.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Collects and writes end-to-end analysis run logs. */
final class RunLogger {

    private final List<String> lines = new ArrayList<>();

    void step(String message) {
        String line = Instant.now() + " " + message;
        lines.add(line);
        System.out.println(message);
    }

    void write(Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, String.join(System.lineSeparator(), lines), StandardCharsets.UTF_8);
    }
}
