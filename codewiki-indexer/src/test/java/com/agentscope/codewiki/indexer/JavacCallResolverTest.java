package com.agentscope.codewiki.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentscope.codewiki.indexer.graph.JavacCallResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavacCallResolverTest {
    @TempDir Path temp;

    @Test
    void compilerResolvesOverloadsAndPolymorphicDeclarations() throws Exception {
        Path service = temp.resolve("demo/Service.java");
        Path caller = temp.resolve("demo/Controller.java");
        Files.createDirectories(service.getParent());
        Files.writeString(
                service,
                "package demo;\ninterface Service { void execute(String s); }\n"
                        + "class DefaultService implements Service {\n"
                        + " public void execute(String s) {}\n"
                        + " public void execute(int i) {}\n}\n");
        Files.writeString(
                caller,
                "package demo;\nclass Controller {\n"
                        + " void run(Service polymorphic, DefaultService concrete) {\n"
                        + "   polymorphic.execute(\"a\");\n"
                        + "   concrete.execute(123);\n"
                        + "   concrete.execute(\"b\");\n"
                        + " }\n}\n");
        var result = new JavacCallResolver().resolve(List.of(caller, service));
        assertTrue(result.complete());
        assertEquals(0, result.errorCount());
        var calls = result.calls().stream().filter(c -> c.callerName().equals("run")).toList();
        assertEquals(3, calls.size());
        assertEquals(
                Set.of(4, 5, 6),
                calls.stream().map(JavacCallResolver.Call::callLine).collect(Collectors.toSet()));
        assertTrue(
                calls.stream().anyMatch(c -> c.callLine() == 4 && c.targetLine() == 2),
                "interface receiver resolves to declared interface, not guessed implementation");
        assertTrue(
                calls.stream().anyMatch(c -> c.callLine() == 5 && c.targetLine() == 5),
                "int overload selected by compiler");
        assertTrue(
                calls.stream().anyMatch(c -> c.callLine() == 6 && c.targetLine() == 4),
                "String overload selected by compiler");
    }

    @Test
    void compilerRejectsIncompleteProjectsInsteadOfReturningPartialClaims() throws Exception {
        Path caller = temp.resolve("Broken.java");
        Files.writeString(caller, "class Broken { void run() { Missing.value(); } }\n");
        var result = new JavacCallResolver().resolve(List.of(caller));
        assertFalse(result.complete());
        assertTrue(result.errorCount() > 0);
        assertTrue(result.calls().isEmpty());
    }

    @Test
    void rejectsSymlinkSourceRatherThanFollowingIt() throws Exception {
        Path real = temp.resolve("Actual.java");
        Path link = temp.resolve("Linked.java");
        Files.writeString(real, "class Actual {}\n");
        Files.createSymbolicLink(link, real);
        assertThrows(
                IllegalArgumentException.class,
                () -> new JavacCallResolver().resolve(List.of(link)));
    }

    @Test
    void invalidInputsNeverReachCompiler() throws Exception {
        Path file = temp.resolve("other.txt");
        Files.writeString(file, "no");
        assertThrows(
                IllegalArgumentException.class,
                () -> new JavacCallResolver().resolve(List.of(file)));
        assertThrows(
                IllegalArgumentException.class, () -> new JavacCallResolver().resolve(List.of()));
    }
}
