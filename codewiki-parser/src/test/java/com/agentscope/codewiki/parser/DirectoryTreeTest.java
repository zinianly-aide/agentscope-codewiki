package com.agentscope.codewiki.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DirectoryTreeTest {

    @TempDir Path tempDir;

    @Test
    void shouldBuildTreeFromDirectory() throws Exception {
        // given: a small directory structure
        Path subDir = tempDir.resolve("src");
        Files.createDirectory(subDir);
        Files.createFile(subDir.resolve("Main.java"));
        Files.createFile(tempDir.resolve("README.md"));

        // when
        DirectoryTree tree = DirectoryTree.fromPath(tempDir, tempDir, Set.of());

        // then
        assertEquals(tempDir.getFileName().toString(), tree.getName());
        assertTrue(tree.isDirectory());
        assertEquals(2, tree.getChildren().size());
    }

    @Test
    void shouldIgnoreDirectories() throws Exception {
        Path subDir = tempDir.resolve("src");
        Path ignoredDir = tempDir.resolve("target");
        Files.createDirectory(subDir);
        Files.createDirectory(ignoredDir);
        Files.createFile(subDir.resolve("App.java"));
        Files.createFile(ignoredDir.resolve("App.class"));

        DirectoryTree tree = DirectoryTree.fromPath(tempDir, tempDir, Set.of("target"));

        assertEquals(1, tree.getChildren().size());
        assertEquals("src", tree.getChildren().get(0).getName());
    }

    @Test
    void shouldSortDirectoriesBeforeFiles() throws Exception {
        Files.createDirectory(tempDir.resolve("aaa"));
        Files.createFile(tempDir.resolve("bbb.txt"));
        Files.createDirectory(tempDir.resolve("ccc"));

        DirectoryTree tree = DirectoryTree.fromPath(tempDir, tempDir, Set.of());

        // directories first, then files — both alphabetically
        assertEquals("aaa", tree.getChildren().get(0).getName());
        assertTrue(tree.getChildren().get(0).isDirectory());
        assertEquals("ccc", tree.getChildren().get(1).getName());
        assertTrue(tree.getChildren().get(1).isDirectory());
        assertEquals("bbb.txt", tree.getChildren().get(2).getName());
        assertFalse(tree.getChildren().get(2).isDirectory());
    }

    @Test
    void shouldProduceTextOutput() throws Exception {
        Files.createFile(tempDir.resolve("README.md"));
        Path src = tempDir.resolve("src");
        Files.createDirectory(src);
        Files.createFile(src.resolve("Main.java"));

        DirectoryTree tree = DirectoryTree.fromPath(tempDir, tempDir, Set.of());
        String text = tree.toText();

        assertTrue(text.contains(tempDir.getFileName().toString()));
        assertTrue(text.contains("README.md"));
        assertTrue(text.contains("Main.java"));
        assertTrue(text.contains("src/"));
        assertTrue(text.contains("├──") || text.contains("└──"));
    }

    @Test
    void emptyDirectoryShouldHaveNoChildren() throws Exception {
        DirectoryTree tree = DirectoryTree.fromPath(tempDir, tempDir, Set.of());

        assertEquals(tempDir.getFileName().toString(), tree.getName());
        assertTrue(tree.getChildren().isEmpty());
    }
}
