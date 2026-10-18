# P4 — conservative Java AST receiver-type binding

## Behavior

This stage extends the P3 method-invocation AST records with an optional
`receiverType` derived from a source-located *nominal type declaration*.
Only simple Java identifiers are considered:

- Method parameters: `void go(BillingService service) { service.process(); }`
- Local variables declared before the call in the same/ancestor lexical block:
  `BillingService service = new BillingService(); service.process();`
- Fields of the containing class: `service.process()` and `this.service.process()`
- Local declarations in an inner block do not leak into the outer block, and a
  declaration after the call does not retroactively bind earlier invocations.
- A receiver with an assignment/update before the call is conservatively unbound.
- The indexed class owner must match the inferred receiver type. For a different
  file, an explicit fully qualified import must match the class source path.
  Ambiguous duplicate methods/classes are intentionally left unresolved.

This is **not** compiler semantic analysis: generics, arrays, `var`, qualified
receiver expressions, aliases, inheritance, overloads, type inference,
annotations affecting binding, static imports and polymorphic dispatch remain
unsupported. The AST-based receiver type is a lexical, best-effort fact. Do not
use this as authoritative security- or release-impact analysis.

## Provenance, evidence and caching

Explicit class/`this` calls retain P3 `AST/HIGH` when uniquely bound. Calls bound
through local/parameter/field type declarations are `AST/MEDIUM` — MEDIUM is a
*rule-level confidence label*, not a measured probability. Calls with unresolved
receivers are omitted instead of guessing. Multiple invocations of the same
source→target symbol relation collapse into **one edge** while preserving
*distinct source line numbers* in `evidenceLines` (the original `evidenceLine`
field retains the first line). `ImpactAnalyzer` propagates this list into
`codewiki_analyze_impact` tool results.

The symbol cache advances to format **v3**, so previous parsed symbols from v2
are automatically rebuilt rather than silently missing receiver metadata.
The cache continues to be opt-in, private, and outside the scanned repository.

## Regression tests

- Parser integration: parameter, local, field, `this.field`, inner-block visibility,
  future declaration and reassignment behavior.
- Java Tree-sitter → `CodeGraphBuilder` → `ImpactAnalyzer`: three-file gold
  fixture with two competing same-name owner classes, five supported calls
  collapsed into one relation (`evidenceLines` on lines 7 and 9), and negative
  unresolved/out-of-scope receivers. Explicitly imported owner disambiguated.
- Existing P3 static-style and same-owner call tests remain passing.

These are carefully selected small fixtures. They are **not** representative
real-repository precision/recall results. The next milestone should implement
receiver-type resolution using a real Java compiler symbol table and build a
larger independently annotated multi-repository validation set.

Run locally:

```bash
mvn -q -o test
python3 scripts/minicpm47_codewiki_eval.py \
  --model-url http://127.0.0.1:18086/v1/chat/completions \
  --model minicpm-v47-full --max-cases 3
```
