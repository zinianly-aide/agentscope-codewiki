# Dify 32k Optimization Result

## 1. Scope

This optimization pass only addressed P0/P1 issues evidenced by
`reports/dify-32k-evaluation.md`. No large architecture rewrite was introduced.

Selected fixes:

1. Dify prompt budget guard.
2. Chunk + retrieval before model context construction.
3. Diff Review uses changed-file retrieval context only.
4. Wiki summary is split by detected modules.
5. `ask` uses TopK relevant context.
6. Benchmark reports 32k context metrics and avoids ignored/generated artifacts.

## 2. Before vs After

| Area | Before evidence | After change | Verification evidence |
|---|---|---|---|
| Dify prompt budget guard | Evaluation estimated Diff Review Dify prompt exceeded 535k tokens because impact analysis and git diff were outside the budget guard. | `DiffReviewAgent` now budgets the whole Dify prompt, caps impact rendering, truncates diff/context sections, and keeps the final prompt within `maxContextTokens * 4` chars. | `DiffReviewAgentTest.difyPromptStaysInsideConfiguredPromptBudget` asserts prompt length is within budget and oversized context is trimmed. |
| Chunk + retrieval | Evaluation found risk of sending large file content as full context. | Added `CodeDocumentChunker`; analyze, diff review, ask, and benchmark feed chunked documents into `HybridRetriever`. | `CodeDocumentChunkerTest` validates stable chunk ids and max chunk size. |
| Diff Review changed files only | Evaluation found final Dify prompt could include large full-repo retrieval context. | `DiffReviewCliRunner` still builds the full graph for deterministic impact analysis, but the review corpus is now built only from changed files. | `DiffReviewCliEndToEndTest.changedSymbolsOnlyComeFromChangedFiles` asserts unchanged file symbols/content are absent from `diff-review.md`. |
| Wiki module generation | Evaluation found wiki generation created one repository module despite Maven modules. | `EndToEndAnalysisRunner` derives modules from build files and assigns files to module paths before `RepositoryWikiGenerator` writes pages. | `CodewikiCliEndToEndTest.analyzeCommandCreatesSeparateWikiModulesFromBuildFiles` asserts `module-a` and `module-b` appear in summary JSON and `modules.md`. |
| Repository Q&A | Evaluation found no `ask` command; all ask attempts failed with `Missing command: analyze`. | Added `codewiki ask --repo ... --question ... --top-k N`, using chunked retrieval and TopK prompt construction. | `AskRunnerTest.askRunnerUsesTopKRetrievedContextAndPromptBudget` asserts TopK=1, prompt budget, and relevant-only context. |
| Benchmark 32k metrics | Evaluation found benchmark lacked 32k prompt metrics and was contaminated by ignored generated artifacts. | `BenchmarkReport` now includes `32k Context Metrics`; `BenchmarkRunner` prefers `git ls-files` and chunks retrieval documents. | `BenchmarkRunnerTest` asserts 32k metric fields and that ignored generated files are excluded. |

## 3. Functional Impact

### 3.1 Prompt Budget Guard

Changed class:

- `codewiki-agent/src/main/java/com/agentscope/codewiki/agent/DiffReviewAgent.java`

Behavior after optimization:

- Full Dify prompt is budgeted, not only `ContextPack`.
- Impact rendering is deduplicated and capped.
- Git diff and context sections are truncated with explicit marker:
  `... [trimmed for Dify prompt budget]`.
- Markdown report impact section is also capped to prevent multi-MB reports.
- Full `impact-report.json` is still written unchanged by CLI for machine analysis.

### 3.2 Chunk + Retrieval

New class:

- `codewiki-retriever/src/main/java/com/agentscope/codewiki/retriever/CodeDocumentChunker.java`

Used by:

- `EndToEndAnalysisRunner`
- `DiffReviewCliRunner`
- `AskRunner`
- `BenchmarkRunner`

Default chunk policy:

- `DEFAULT_MAX_CHARS = 6000`
- `DEFAULT_OVERLAP_CHARS = 400`

This is intentionally small and local: it improves retrieval granularity without changing retriever interfaces.

### 3.3 Diff Review Changed Files Only

Changed class:

- `codewiki-cli/src/main/java/com/agentscope/codewiki/cli/DiffReviewCliRunner.java`

After optimization:

- Full repository analysis is still used to build `CodeGraph`.
- Deterministic impact analysis remains graph-based.
- Review retrieval corpus is restricted to changed file analyses.
- Dify/model prompt no longer receives unrelated unchanged source files through retrieval context.

### 3.4 Wiki Module Split

Changed class:

- `codewiki-cli/src/main/java/com/agentscope/codewiki/cli/EndToEndAnalysisRunner.java`

After optimization:

- Build files are used to infer module paths.
- Source files are assigned to the matching module path.
- Empty root module is skipped when child modules exist.
- Existing `RepositoryWikiGenerator` API is unchanged.

### 3.5 Ask TopK Context

New classes:

- `codewiki-cli/src/main/java/com/agentscope/codewiki/cli/AskCommandOptions.java`
- `codewiki-cli/src/main/java/com/agentscope/codewiki/cli/AskRunner.java`

New CLI:

```bash
codewiki ask --repo /path/to/repo --question "..." --top-k 5 --mock
```

Dify mode is compatible with the existing Dify flags:

```bash
codewiki ask --repo /path/to/repo --question "..." --top-k 5 --dify \
  --dify-response-mode streaming --max-context-tokens 32000
```

Prompt construction uses only the TopK retrieved code contexts and enforces the same 32k-style char budget.

### 3.6 Benchmark 32k Metrics

Changed classes:

- `codewiki-cli/src/main/java/com/agentscope/codewiki/cli/BenchmarkRunner.java`
- `codewiki-cli/src/main/java/com/agentscope/codewiki/cli/BenchmarkReport.java`

New report section:

```markdown
## 32k Context Metrics

- Context Limit Tokens: 32000
- Max Prompt Tokens: ...
- Average Prompt Tokens: ...
- Prompts Over Context Limit: ...
```

Benchmark file enumeration now prefers `git ls-files`; if unavailable, it falls back to the previous filesystem walk.

## 4. Test Coverage Added

| Test | Covers |
|---|---|
| `CodeDocumentChunkerTest.shouldSplitLargeDocumentsWithStableChunkIds` | Chunking and chunk metadata. |
| `DiffReviewAgentTest.difyPromptStaysInsideConfiguredPromptBudget` | Whole-prompt budget guard for Dify Diff Review. |
| `DiffReviewCliEndToEndTest.changedSymbolsOnlyComeFromChangedFiles` | Changed-file-only review context. |
| `CodewikiCliEndToEndTest.analyzeCommandCreatesSeparateWikiModulesFromBuildFiles` | Module-level wiki input generation. |
| `AskRunnerTest.askRunnerUsesTopKRetrievedContextAndPromptBudget` | Ask TopK retrieval and prompt budget. |
| `AskRunnerTest.askCliCommandIsAvailable` | CLI compatibility for new `ask` command. |
| `BenchmarkRunnerTest.benchmarkUsesTrackedFilesInsteadOfIgnoredGeneratedArtifacts` | Benchmark ignores generated/untracked artifacts. |
| `BenchmarkRunnerTest.benchmarkRunnerWritesReportForRepository` | 32k metric section exists in benchmark output. |

## 5. Commands Executed

| Command | Result |
|---|---|
| `mvn spotless:apply` | PASS; formatted changed Java files. |
| `mvn -pl codewiki-agent,codewiki-cli -am test` | PASS. |
| `mvn test` | PASS; 9-module reactor success. |

One manual benchmark attempt against the full current repository was interrupted after exceeding the expected local runtime. This did not block verification because the benchmark behavior is covered by targeted tests and full `mvn test`.

## 6. Remaining Non-P0/P1 Work

Not addressed in this pass:

- Semantic embeddings are still hash-based/local; retrieval quality can be improved later.
- Impact graph output JSON can still be large for very dense graphs.
- Ask mock answers are evidence summaries, not a replacement for a real LLM answer.
- Wiki generation still uses deterministic markdown generation; module splitting improves structure but does not add per-module LLM synthesis.

These are intentionally left out because the request scoped this pass to P0/P1 fixes without large-scale refactoring.
