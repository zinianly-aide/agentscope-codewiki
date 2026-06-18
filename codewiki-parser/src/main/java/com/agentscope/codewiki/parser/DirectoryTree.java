package com.agentscope.codewiki.parser;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Represents a node in a directory tree. Directories have children; files do not. */
public class DirectoryTree {

    private final String name;
    private final String path;
    private final boolean directory;
    private final List<DirectoryTree> children;

    public DirectoryTree(String name, String path, boolean directory) {
        this.name = name;
        this.path = path;
        this.directory = directory;
        this.children = directory ? new ArrayList<>() : null;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("path")
    public String getPath() {
        return path;
    }

    @JsonProperty("directory")
    public boolean isDirectory() {
        return directory;
    }

    @JsonProperty("children")
    public List<DirectoryTree> getChildren() {
        return children;
    }

    /**
     * Build a directory tree from a filesystem path, ignoring certain directories.
     *
     * @param root the repository root (for computing relative paths)
     * @param current the current directory to scan
     * @param ignoreDirs directory names to skip (e.g., ".git", "target")
     * @return the root DirectoryTree node
     */
    public static DirectoryTree fromPath(Path root, Path current, Set<String> ignoreDirs)
            throws IOException {
        String relPath = root.relativize(current).toString();
        if (relPath.isEmpty()) {
            relPath = ".";
        }
        DirectoryTree node =
                new DirectoryTree(
                        current.getFileName() != null
                                ? current.getFileName().toString()
                                : root.getFileName().toString(),
                        relPath,
                        true);

        try (Stream<Path> stream = Files.list(current)) {
            List<Path> entries =
                    stream.filter(p -> !ignoreDirs.contains(p.getFileName().toString()))
                            .sorted(
                                    Comparator.<Path, Boolean>comparing(p -> !Files.isDirectory(p))
                                            .thenComparing(Path::getFileName))
                            .collect(Collectors.toList());

            for (Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    node.children.add(fromPath(root, entry, ignoreDirs));
                } else {
                    node.children.add(
                            new DirectoryTree(
                                    entry.getFileName().toString(),
                                    root.relativize(entry).toString(),
                                    false));
                }
            }
        }
        return node;
    }

    /** Pretty-print this tree as a text string (like the UNIX {@code tree} command). */
    @JsonIgnore
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("/").append(System.lineSeparator());
        renderChildren(sb, "", "");
        return sb.toString();
    }

    private void renderChildren(StringBuilder sb, String prefix, String childPrefix) {
        if (children == null || children.isEmpty()) {
            return;
        }
        for (int i = 0; i < children.size(); i++) {
            DirectoryTree child = children.get(i);
            boolean last = (i == children.size() - 1);
            String connector = last ? "└── " : "├── ";
            sb.append(prefix).append(connector).append(child.name);
            if (child.directory) {
                sb.append("/");
            }
            sb.append(System.lineSeparator());
            if (child.directory && child.children != null && !child.children.isEmpty()) {
                String nextPrefix = prefix + (last ? "    " : "│   ");
                child.renderChildren(sb, nextPrefix, "");
            }
        }
    }
}
