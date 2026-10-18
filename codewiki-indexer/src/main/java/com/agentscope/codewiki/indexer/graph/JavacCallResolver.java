package com.agentscope.codewiki.indexer.graph;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Opt-in javac semantic evidence. It parses and resolves symbols, but NEVER compiles, loads, or
 * executes repository code or annotation processors. Requires a complete self-contained source set:
 * any javac ERROR invalidates the entire result.
 */
public final class JavacCallResolver {
    private static final int MAX_FILES = 80;
    private static final long MAX_BYTES = 2 * 1024 * 1024;

    public record Call(
            Path callerFile,
            String callerName,
            int callerLine,
            Path targetFile,
            String targetName,
            int targetLine,
            int callLine) {}

    public record Result(boolean complete, List<Call> calls, int errorCount) {}

    public Result resolve(List<Path> inputFiles) throws IOException {
        if (inputFiles == null || inputFiles.isEmpty() || inputFiles.size() > MAX_FILES) {
            throw new IllegalArgumentException("javac input must contain 1..80 Java files");
        }
        List<Path> files = new ArrayList<>();
        long bytes = 0;
        for (Path file : inputFiles) {
            if (Files.isSymbolicLink(file.toAbsolutePath().normalize())) {
                throw new IllegalArgumentException("javac source file must not be a symlink");
            }
            Path normalized = file.toRealPath();
            if (!normalized.getFileName().toString().endsWith(".java")
                    || Files.isSymbolicLink(normalized)
                    || !Files.isRegularFile(normalized)) {
                throw new IllegalArgumentException("javac inputs must be regular .java files");
            }
            bytes += Files.size(normalized);
            if (bytes > MAX_BYTES) {
                throw new IllegalArgumentException("javac source size limit exceeded");
            }
            files.add(normalized);
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new Result(false, List.of(), 1);
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Path isolatedClasspath = Files.createTempDirectory("codewiki-javac-empty-");
        try (StandardJavaFileManager manager =
                compiler.getStandardFileManager(diagnostics, null, null)) {
            var inputs = manager.getJavaFileObjectsFromPaths(files);
            // Explicit empty user classpath/sourcepath: JDK symbols and supplied files only.
            // -proc:none prevents arbitrary annotation processor code execution.
            List<String> options =
                    List.of(
                            "-proc:none",
                            "-implicit:none",
                            "-classpath",
                            isolatedClasspath.toString(),
                            "-sourcepath",
                            isolatedClasspath.toString(),
                            "-Xlint:none");
            JavacTask task =
                    (JavacTask) compiler.getTask(null, manager, diagnostics, options, null, inputs);
            List<CompilationUnitTree> units = new ArrayList<>();
            try {
                for (CompilationUnitTree tree : task.parse()) {
                    units.add(tree);
                }
                task.analyze();
            } catch (RuntimeException exception) {
                // Never return partially resolved edges as authoritative.
                return new Result(false, List.of(), 1);
            }
            int errors =
                    (int)
                            diagnostics.getDiagnostics().stream()
                                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                                    .count();
            if (errors > 0) {
                return new Result(false, List.of(), errors);
            }
            Trees trees = Trees.instance(task);
            List<Call> results = new ArrayList<>();
            for (CompilationUnitTree unit : units) {
                Path file = Path.of(unit.getSourceFile().toUri()).toRealPath();
                new TreePathScanner<Void, Void>() {
                    private String callerName;
                    private int callerLine;

                    @Override
                    public Void visitMethod(MethodTree method, Void unused) {
                        String priorName = callerName;
                        int priorLine = callerLine;
                        callerName = method.getName().toString();
                        callerLine =
                                lineOf(
                                        unit,
                                        trees.getSourcePositions().getStartPosition(unit, method));
                        super.visitMethod(method, unused);
                        callerName = priorName;
                        callerLine = priorLine;
                        return null;
                    }

                    @Override
                    public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
                        if (callerName != null) {
                            Element resolved = trees.getElement(getCurrentPath());
                            if (resolved instanceof ExecutableElement executable
                                    && (resolved.getKind() == ElementKind.METHOD
                                            || resolved.getKind() == ElementKind.CONSTRUCTOR)) {
                                TreePath declaration = trees.getPath(resolved);
                                if (declaration != null
                                        && declaration.getLeaf() instanceof MethodTree m) {
                                    CompilationUnitTree targetUnit =
                                            declaration.getCompilationUnit();
                                    Path target =
                                            Path.of(targetUnit.getSourceFile().toUri())
                                                    .toAbsolutePath()
                                                    .normalize();
                                    int targetLine =
                                            lineOf(
                                                    targetUnit,
                                                    trees.getSourcePositions()
                                                            .getStartPosition(targetUnit, m));
                                    int callLine =
                                            lineOf(
                                                    unit,
                                                    trees.getSourcePositions()
                                                            .getStartPosition(unit, call));
                                    results.add(
                                            new Call(
                                                    file,
                                                    callerName,
                                                    callerLine,
                                                    target,
                                                    executable.getSimpleName().toString(),
                                                    targetLine,
                                                    callLine));
                                }
                            }
                        }
                        return super.visitMethodInvocation(call, unused);
                    }
                }.scan(unit, null);
            }
            results.sort(
                    Comparator.comparing((Call call) -> call.callerFile().toString())
                            .thenComparingInt(Call::callLine)
                            .thenComparing(Call::targetName));
            return new Result(true, List.copyOf(results), 0);
        } finally {
            Files.deleteIfExists(isolatedClasspath);
        }
    }

    private static int lineOf(CompilationUnitTree unit, long pos) {
        return pos < 0 ? 0 : (int) unit.getLineMap().getLineNumber(pos);
    }
}
