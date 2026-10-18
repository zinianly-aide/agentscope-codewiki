# P2: conservative code-graph relation inference

## Problem

The original graph builder linked every identically named symbol across a repository.
This incorrectly treated same-named methods in unrelated modules as callees, counted
references inside comments/string literals, mixed Java and Python call edges, and
linked both a Java class and unrelated classes with the same imported simple name.
The impact analyzer then traversed those speculative edges as if they were resolved
calls, inflating risk and affected-module counts.

## Current inference rules

1. Ignore comments, ordinary quoted literals, Python triple-quoted strings, and
   TypeScript template literals during *graph edge* inference. This is a lightweight
   lexical filter, **not** a full compiler AST semantic pass.
2. Only resolve edges within the same language family; TypeScript and TSX are grouped.
3. If multiple symbols share the target name, prefer the same source file; otherwise
   use a uniquely matching Java fully-qualified `import` for types. If multiple
   plausible targets remain, **omit** the uncertain edge rather than linking all.
4. Treat a class with same-file constructor symbols as a unique type target; don't
   classify methods as inheritance bases, or classes as method-call destinations.
5. Ignore a method declaration's self-name when deciding whether it calls a different
   same-named method; avoid creating both a `CALLS` and a `REFERENCES` edge for a
   single observed invocation.
6. Retain previously working simple calls, TS-to-TSX calls, Java inheritance and
   imports where names are unambiguous.

These are **precision-first heuristics**, not complete Java/Python/TS name resolution.
There are deliberate recall tradeoffs: overloaded or dynamically dispatched methods
without a resolved receiver, wildcard imports, reflective calls, nested templates,
regex literals and inheritance requiring full module/type binding may be missed.
Source content returned by `codewiki_search_code` is unchanged; the filter only
changes relationship inference and downstream impact results.

## Validation

The new `CodeGraphBuilderTest` cases exercise Java/Python/TypeScript/TSX, realistic
same-name ambiguity, explicit fully-qualified imports, literal/comment noise,
constructor-vs-class collisions, self-declarations, and `CALLS`/`REFERENCES`
de-duplication. The first six ambiguity cases failed against the old implementation
and passed after the change. Follow-up self-reference and duplicate-call regressions
were added as separate tests.

Run:

```bash
mvn -q -o -pl codewiki-indexer -am test
mvn -q -o test
```

Local full project result (2026-10-18): **179 tests, 0 failures/errors/skips**.
The project-level MiniCPM-V 4.7 1B Q&A smoke should be rerun after each graph
change to guard the MCP -> retrieval -> model path; passing 3 sample prompts is
not a production-accuracy metric.

Do not interpret an automatically derived risk level as a proven dependency chain.
A future high-confidence graph should store semantic receiver/type resolution and
confidence/evidence per edge, with source-anchored AST facts instead of regex guesses.

## Next layer: AST facts

P3 adds source-located AST Java call sites and `origin/confidence/evidenceLine`
provenance, while keeping legacy heuristics for symbols without AST facts.
Read [AST binding and controlled evaluation](code-graph-ast-p3.md).
