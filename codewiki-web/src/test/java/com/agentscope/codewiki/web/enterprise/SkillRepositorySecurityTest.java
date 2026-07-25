package com.agentscope.codewiki.web.enterprise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillRepositorySecurityTest {
    @TempDir Path tempDir;

    @Test
    void rejectsPathTraversalSkillName() throws Exception {
        Path root = tempDir.resolve("skills");
        Files.createDirectories(root.resolve("safe"));
        Files.writeString(
                root.resolve("safe/SKILL.md"), "---\nname: safe\ndescription: ok\n---\nbody");
        SkillRepository repository = new SkillRepository(root, 1024);
        assertEquals("", repository.readSkill("../safe"));
    }

    @Test
    void ignoresTooLargeSkillFile() throws Exception {
        Path root = tempDir.resolve("skills");
        Files.createDirectories(root.resolve("large"));
        Files.writeString(root.resolve("large/SKILL.md"), "x".repeat(2048));
        SkillRepository repository = new SkillRepository(root, 1024);
        assertEquals(0, repository.skillCount());
        assertEquals("", repository.readSkill("large"));
    }

    @Test
    void duplicateSkillNamesAreIgnored() throws Exception {
        Path root = tempDir.resolve("skills");
        Files.createDirectories(root.resolve("a"));
        Files.createDirectories(root.resolve("b"));
        Files.writeString(
                root.resolve("a/SKILL.md"), "---\nname: dup\ndescription: one\n---\nbody");
        Files.writeString(
                root.resolve("b/SKILL.md"), "---\nname: dup\ndescription: two\n---\nbody");
        SkillRepository repository = new SkillRepository(root, 2048);
        assertEquals(1, repository.skillCount());
        assertTrue(repository.listSkills().get(0).description().contains("one"));
    }
}
