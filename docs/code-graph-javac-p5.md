# P5 experiment: javac-based semantic call binding and gold evaluation

## What actually works

`JavacCallResolver` uses Java 21's public `JavacTask`/`Trees` APIs to resolve
**declaration targets** for Java method invocation AST nodes. It understands
Java overload selection (argument types), imported types and declared interface
receiver methods, rather than guessing based solely on simple method names.

For an explicitly chosen *self-contained* source set, invoke:

```java
var result = new JavacCallResolver().resolve(List.of(javaFileA, javaFileB));
// result.complete() == false if the set cannot be fully resolved.

CodeGraph graph = new CodeGraphBuilder()
    .buildWithCompilerEvidence(parsedSymbols, repositoryRoot, List.of(javaFileA, javaFileB));
```

The optional overlay replaces **only** Java `CALLS` from the specified compiled
source files. A matched edge is tagged `origin=JAVAC`, `confidence=COMPILER` and
has its caller's source line in `evidenceLines`. Compiler binding is **not**
runtime dispatch proof: virtual calls resolve to their static declaration
(e.g. an interface method), not a guaranteed concrete implementation.

## Safety boundaries and fallback

This API is deliberately *opt-in and NOT wired to the live MCP/Agent index*.
It rejects non-Java/symlink source inputs and batches larger than 80 files or
2 MiB. Java analysis is run in-process with `-proc:none` (annotation processors
disabled), `-implicit:none`, and an isolated temporary empty directory as
both the user classpath and source path (never the current working directory).
It does **not** generate class files or run repository application code.
Because javac runs in-process, this experimental API has **no hard execution
deadline** and should be used only on small, trusted source sets. Do not expose
it as an unauthenticated tool or execute it automatically on arbitrary repositories.

If compiler diagnostics contain an error (e.g. missing external dependencies),
no partial output is presented as verified. `buildWithCompilerEvidence` falls
back to the existing conservative P4 graph. For large or dependency-heavy Maven
projects, the current isolated source set often cannot compile; a real project
classpath/build integration is deliberately deferred.

The mapping to CodeWiki graph nodes uses source file + method name + declaration
line. Overloads declared on different lines are resolved; multiple overloads
on the *same line*, method references, library-only targets and constructors not
represented by the existing graph schema are omitted rather than misidentified.

## Reproducible gold tests

`JavacCallResolverTest`: compiler selects `int` vs `String` overloads, and a
polymorphic interface receiver resolves to its **declared interface method**.
Incompletely typed projects return `complete=false`.

`CodeGraphCompilerIntegrationTest`: real Tree-sitter symbols → javac overlay →
source-anchored graph edges; three manually reviewed call edges yield TP=3,
FP=0, FN=0, precision=1.0, recall=1.0 **only on this small fixture**.
`CallGraphEvaluator.evaluate(graph, manualGold)` computes distinct edge-level
TP/FP/FN, precision, recall and F1 and is suitable for extending to a larger
independently annotated corpus. A tiny fixture is not a claim of production
accuracy or increased coverage on real repositories.

```bash
mvn -q -o -pl codewiki-indexer -am test
mvn -q -o test
```

## Next required work before production

1. Obtain a **real, independently annotated** corpus from multiple repositories
   and languages (with separate precision, recall and unsupported-case counts).
2. Add *safe* project-classpath resolution and incremental compiler caching;
   avoid invoking arbitrary build tools or annotation processors.
3. Add graph identity for overloads whose declaration start line is shared,
   plus constructor/method-reference modeling and receiver-type provenance.
4. Independently validate security and resource limits before offering compiler
   semantics through MCP; production tools remain unchanged in this stage.

### Local JSON evaluation workflow

For a complete, independently reviewed call-edge gold set, run:

```bash
python3 scripts/codegraph_compare.py \
  --graph /path/to/graph.json --gold /path/to/manual_gold.json
python3 -m unittest scripts/test_codegraph_compare.py
```

The manually maintained gold JSON is an array of rows such as:

```json
[{"callerPath":"demo/Api.java","callerDeclarationLine":3,
  "targetPath":"demo/Service.java","targetDeclarationLine":5}]
```

This produces TP/FP/FN, precision/recall/F1 and examples for review. Every
candidate `CALLS` edge **must** be covered by manual labeling to interpret
false positives meaningfully; sampling only positive gold references and
scoring the entire predicted graph would spuriously count unannotated true
calls as false positives. Use the **same evaluated source scope** for both.
