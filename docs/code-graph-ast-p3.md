# P3: AST-anchored Java call edges (small verified slice)

## Implemented

The Tree-sitter Java bridge records each method/constructor's `method_invocation`
name, optional receiver and one-based source line; its owning class and explicit
imports are also serialized in `CodeSymbol`. Calls inside comments/string literals
never become AST facts. Symbols for other languages are unchanged.

`CodeGraphBuilder` uses **AST** `CALLS` edges for Java symbols parsed with the new
schema, and does not fall back to a guessed method-name call when that AST exists.
Only two deliberately narrow resolutions currently qualify:

1. `localMethod()` / `this.localMethod()` to a uniquely matched method in the
   same file **and owning type**.
2. `ImportedClass.method()` to a uniquely matched method in an explicitly
   imported class, where the relative source path matches the full import.

Unresolved `variable.method()`, overloaded targets, polymorphic calls, inherited
methods, static-star imports, method references, lambdas and reflection are **not**
assigned a target. This preserves precision at the cost of substantial recall.
Existing manually constructed/legacy `CodeSymbol` data without AST references
still uses the P2 conservative heuristic path. New Java files with an empty parsed
reference list deliberately have no inferred `CALLS` edges.

Each graph/impact edge now exposes `origin` (`AST` vs `HEURISTIC`), `confidence`
(`HIGH` on uniquely bound local/imported AST calls, `LOW` on heuristics), and
`evidenceLine` (one-based for AST call sites, 0 when unknown). The fields flow
through `CodeGraph` JSON and `ImpactAnalyzer` responses, including the
`codewiki_analyze_impact` MCP tool. **Confidence is a rule-level label rather than
a statistical probability.**

The on-disk symbol cache format advances from 1 to 2, forcing older caches to be
reparsed so that legacy Java symbols aren't incorrectly mistaken for AST-verified
symbols. No main-branch changes or permanent cache files are committed.

## Verification / benchmark boundaries

`CodeGraphAstIntegrationTest` parses **real Java source with Tree-sitter** in a
three-file fixture containing competing same-name classes, two true call edges,
comments, string literals and an unresolved instance receiver. It compares the
predicted `CALLS` edges against two manually enumerated gold edges:

- 2 true positives, 0 false positives, 0 false negatives;
- **precision 2/2 = 100%, recall 2/2 = 100% on this fixture only**;
- both edges carry `origin=AST`, `confidence=HIGH` and source line evidence;
- impact traversal carries that provenance through to its output.

This tiny controlled fixture must **not** be quoted as accuracy on unknown projects.
Run a representative manually annotated multi-repository holdout before changing
impact risk policies. In particular, compare recall against the prior P2 graph;
conservative AST scope will likely omit many real calls in large applications.

```bash
mvn -q -o -pl codewiki-indexer -am test
mvn -q -o test
```

The preexisting MiniCPM-V 4.7 1B local Q&A and Web Agent smoke scripts exercise
retrieval and tool invocation, **not** semantic correctness of the generated graph.
A future milestone should add receiver-type inference backed by symbol tables and
receiver/owner binding tests, then evaluate true call precision/recall on larger
real-world Java, TypeScript and Python projects.
