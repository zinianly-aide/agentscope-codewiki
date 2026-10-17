package com.agentscope.codewiki.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Extracts code symbols (class, method, function, etc.) from source files using tree-sitter via a
 * Node.js bridge script.
 *
 * <p>Supported languages: Java, Python, TypeScript.
 */
public class TreeSitterSymbolExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, String> EXTENSION_LANG =
            Map.of(
                    ".java", "java",
                    ".py", "python",
                    ".ts", "typescript",
                    ".tsx", "typescript");

    private final Path bridgeScript;

    /** Creates an extractor that auto-resolves the bridge script from the classpath. */
    public TreeSitterSymbolExtractor() throws IOException {
        URL resource =
                getClass().getClassLoader().getResource("tree-sitter-bridge/extract-symbols.mjs");
        if (resource == null) {
            throw new IOException(
                    "tree-sitter bridge script not found on classpath. "
                            + "Ensure src/main/resources/tree-sitter-bridge is built.");
        }
        this.bridgeScript = Path.of(resource.getPath());
    }

    /** Creates an extractor with an explicit bridge script path (for testing). */
    public TreeSitterSymbolExtractor(Path bridgeScript) {
        this.bridgeScript = bridgeScript;
    }

    /**
     * Extract symbols from a source file.
     *
     * @param sourceFile the source file to analyze
     * @return list of extracted symbols
     * @throws IOException on I/O or bridge errors
     * @throws IllegalArgumentException if the language is unsupported
     */
    public List<CodeSymbol> extract(Path sourceFile) throws IOException {
        String lang = detectLanguage(sourceFile);
        if (lang == null) {
            throw new IllegalArgumentException("Unsupported file extension: " + sourceFile);
        }
        return extract(sourceFile, lang);
    }

    /**
     * Extracts many files through a single Node bridge process, avoiding per-file startup cost.
     * Paths are passed through stdin as JSON rather than interpolated into shell commands.
     */
    public List<CodeSymbol> extractBatch(List<Path> sourceFiles) throws IOException {
        if (sourceFiles == null || sourceFiles.isEmpty()) {
            return List.of();
        }
        if (sourceFiles.size() > 800) {
            throw new IllegalArgumentException("batch source file limit exceeded");
        }
        for (Path file : sourceFiles) {
            if (detectLanguage(file) == null) {
                throw new IllegalArgumentException("unsupported source language");
            }
        }
        Path workDir = bridgeScript.getParent();
        if (!Files.isDirectory(workDir.resolve("node_modules"))) {
            throw new IOException("tree-sitter bridge dependencies are not installed");
        }
        Process process =
                new ProcessBuilder("node", bridgeScript.toAbsolutePath().toString(), "--batch")
                        .directory(workDir.toFile())
                        .redirectErrorStream(true)
                        .start();
        try (var writer = process.getOutputStream()) {
            writer.write(
                    MAPPER.writeValueAsBytes(
                            sourceFiles.stream().map(p -> p.toAbsolutePath().toString()).toList()));
        }
        String output;
        try (var reader = process.getInputStream()) {
            output = new String(reader.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        try {
            if (process.waitFor() != 0) {
                throw new IOException("tree-sitter batch bridge failed: " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("tree-sitter batch interrupted", e);
        }
        return MAPPER.readValue(output, new TypeReference<List<CodeSymbol>>() {});
    }

    private List<CodeSymbol> extract(Path sourceFile, String lang) throws IOException {
        String json = runBridge(lang, sourceFile.toAbsolutePath().toString());
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        return MAPPER.readValue(json, new TypeReference<List<CodeSymbol>>() {});
    }

    private String runBridge(String lang, String filePath) throws IOException {
        Path workDir = bridgeScript.getParent();
        // Ensure node_modules are available
        Path nodeModules = workDir.resolve("node_modules");
        if (!Files.isDirectory(nodeModules)) {
            throw new IOException(
                    "tree-sitter bridge node_modules not found at "
                            + nodeModules
                            + ". Run: cd "
                            + workDir
                            + " && npm install --legacy-peer-deps");
        }

        ProcessBuilder pb =
                new ProcessBuilder(
                        "node",
                        bridgeScript.toAbsolutePath().toString(),
                        "--lang",
                        lang,
                        "--file",
                        filePath);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String output = reader.lines().collect(Collectors.joining("\n"));
            int exitCode;
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Bridge process interrupted", e);
            }
            if (exitCode != 0) {
                throw new IOException("Bridge exited with code " + exitCode + ": " + output);
            }
            return output;
        }
    }

    private String detectLanguage(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        for (Map.Entry<String, String> entry : EXTENSION_LANG.entrySet()) {
            if (name.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
