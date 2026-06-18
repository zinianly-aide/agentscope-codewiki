package com.agentscope.codewiki.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scans a local Git repository and extracts metadata including branch, commit, languages, file
 * count, directory tree, README location, and build files.
 */
public class RepositoryScanner {

    /** Directories to exclude from scanning. */
    private static final Set<String> IGNORE_DIRS =
            Set.of(
                    ".git",
                    "target",
                    "build",
                    "node_modules",
                    "__pycache__",
                    ".idea",
                    ".vscode",
                    "dist",
                    "out",
                    ".gradle",
                    "bin",
                    "obj");

    /** Recognized README filenames (checked in order). */
    private static final List<String> README_NAMES =
            List.of("README.md", "README.txt", "README", "README.rst");

    /** Build-file filename → descriptor mapping. */
    private static final Map<String, String> BUILD_FILE_PATTERNS =
            Map.ofEntries(
                    Map.entry("pom.xml", "Maven"),
                    Map.entry("build.gradle", "Gradle"),
                    Map.entry("build.gradle.kts", "Gradle (Kotlin DSL)"),
                    Map.entry("package.json", "Node.js"),
                    Map.entry("requirements.txt", "Python (requirements)"),
                    Map.entry("setup.py", "Python (setuptools)"),
                    Map.entry("pyproject.toml", "Python (pyproject)"),
                    Map.entry("Cargo.toml", "Rust (Cargo)"),
                    Map.entry("go.mod", "Go"),
                    Map.entry("Makefile", "Make"),
                    Map.entry("CMakeLists.txt", "CMake"),
                    Map.entry("Dockerfile", "Docker"));

    /** File extension → language name mapping. */
    private static final Map<String, String> EXTENSION_LANGUAGE =
            Map.ofEntries(
                    Map.entry(".java", "Java"),
                    Map.entry(".kt", "Kotlin"),
                    Map.entry(".kts", "Kotlin"),
                    Map.entry(".py", "Python"),
                    Map.entry(".js", "JavaScript"),
                    Map.entry(".mjs", "JavaScript"),
                    Map.entry(".cjs", "JavaScript"),
                    Map.entry(".ts", "TypeScript"),
                    Map.entry(".tsx", "TypeScript (React)"),
                    Map.entry(".jsx", "JavaScript (React)"),
                    Map.entry(".go", "Go"),
                    Map.entry(".rs", "Rust"),
                    Map.entry(".c", "C"),
                    Map.entry(".h", "C"),
                    Map.entry(".cpp", "C++"),
                    Map.entry(".cc", "C++"),
                    Map.entry(".cxx", "C++"),
                    Map.entry(".hpp", "C++"),
                    Map.entry(".cs", "C#"),
                    Map.entry(".rb", "Ruby"),
                    Map.entry(".php", "PHP"),
                    Map.entry(".swift", "Swift"),
                    Map.entry(".scala", "Scala"),
                    Map.entry(".groovy", "Groovy"),
                    Map.entry(".sql", "SQL"),
                    Map.entry(".sh", "Shell"),
                    Map.entry(".bash", "Shell"),
                    Map.entry(".proto", "Protobuf"),
                    Map.entry(".vue", "Vue"),
                    Map.entry(".svelte", "Svelte"),
                    Map.entry(".r", "R"),
                    Map.entry(".dart", "Dart"),
                    Map.entry(".lua", "Lua"),
                    Map.entry(".zig", "Zig"),
                    Map.entry(".html", "HTML"),
                    Map.entry(".css", "CSS"),
                    Map.entry(".scss", "SCSS"),
                    Map.entry(".xml", "XML"),
                    Map.entry(".yaml", "YAML"),
                    Map.entry(".yml", "YAML"),
                    Map.entry(".toml", "TOML"),
                    Map.entry(".json", "JSON"),
                    Map.entry(".md", "Markdown"),
                    Map.entry(".gradle", "Gradle"));

    /**
     * Scan a local Git repository and return its metadata.
     *
     * @param repoPath path to the repository root
     * @return populated RepositoryMetadata
     * @throws IOException on I/O errors
     * @throws IllegalArgumentException if the path is not a valid Git repository
     */
    public RepositoryMetadata scan(Path repoPath) throws IOException {
        if (!Files.isDirectory(repoPath)) {
            throw new IllegalArgumentException("Not a directory: " + repoPath);
        }
        Path gitDir = repoPath.resolve(".git");
        if (!Files.exists(gitDir)) {
            throw new IllegalArgumentException("Not a Git repository (no .git): " + repoPath);
        }

        String repoName = repoPath.toRealPath().getFileName().toString();
        String branch = getBranch(repoPath);
        String commitId = getCommitId(repoPath);

        // Walk the repo to collect languages, file count, readme, build files, tree
        List<String> languages = new ArrayList<>();
        Map<String, String> detectedLangs = new LinkedHashMap<>();
        int[] fileCount = {0};
        String[] readmeLoc = {null};
        List<String> buildFiles = new ArrayList<>();

        DirectoryTree tree = DirectoryTree.fromPath(repoPath, repoPath, IGNORE_DIRS);

        // Count files and detect languages (DirectoryTree contains . dirs, so let's walk raw)
        try (var stream = Files.walk(repoPath)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !isIgnored(p, repoPath))
                    .forEach(
                            p -> {
                                fileCount[0]++;
                                String ext = getExtension(p);
                                if (ext != null) {
                                    String lang = EXTENSION_LANGUAGE.get(ext);
                                    if (lang != null && !detectedLangs.containsKey(lang)) {
                                        detectedLangs.put(lang, lang);
                                    }
                                }
                                String fname = p.getFileName().toString();

                                // Check README
                                if (readmeLoc[0] == null && README_NAMES.contains(fname)) {
                                    readmeLoc[0] = repoPath.relativize(p).toString();
                                }

                                // Check build files
                                if (BUILD_FILE_PATTERNS.containsKey(fname)) {
                                    String rel = repoPath.relativize(p).toString();
                                    buildFiles.add(rel);
                                }
                            });
        }

        languages.addAll(detectedLangs.keySet());

        return RepositoryMetadata.builder()
                .repositoryName(repoName)
                .branch(branch)
                .commitId(commitId)
                .languages(Collections.unmodifiableList(languages))
                .fileCount(fileCount[0])
                .readmeLocation(readmeLoc[0])
                .buildFiles(Collections.unmodifiableList(buildFiles))
                .directoryTree(tree)
                .build();
    }

    // ── private helpers ──

    private String getBranch(Path repoPath) throws IOException {
        return runGit(repoPath, "rev-parse", "--abbrev-ref", "HEAD");
    }

    private String getCommitId(Path repoPath) throws IOException {
        return runGit(repoPath, "rev-parse", "HEAD");
    }

    private String runGit(Path repoPath, String... args) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(repoPath.toFile());
        Process process = pb.start();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = reader.readLine();
            int exitCode;
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Git process interrupted", e);
            }
            if (exitCode != 0) {
                throw new IOException("Git command failed with exit code " + exitCode);
            }
            return line != null ? line.trim() : "";
        }
    }

    private boolean isIgnored(Path file, Path repoRoot) {
        for (Path p = file; !p.equals(repoRoot); p = p.getParent()) {
            if (p == null || p.getFileName() == null) break;
            if (IGNORE_DIRS.contains(p.getFileName().toString())) {
                return true;
            }
        }
        return false;
    }

    private String getExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return null;
        return name.substring(dot).toLowerCase();
    }
}
