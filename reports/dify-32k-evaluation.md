# Dify 32k Evaluation Report

## 1. Environment

- Repository under evaluation: `/Users/anshi/agentscope-codewiki`
- Current branch: `main`
- Current commit: `f768663`
- Working tree before report generation: clean and aligned with `origin/main`
- Project modules from root `pom.xml`: `codewiki-core`, `codewiki-parser`, `codewiki-indexer`, `codewiki-retriever`, `codewiki-agent`, `codewiki-dify`, `codewiki-cli`, `codewiki-web`
- Java runtime from `java -version`: OpenJDK Temurin `21.0.8`
- Maven from `mvn -version`: Apache Maven `3.9.11`; Maven itself runs with Java `24.0.2`
- Initial Dify environment in the normal shell:
  - `DIFY_API_URL`: not set
  - `DIFY_BASE_URL`: not set
  - `DIFY_API_KEY`: not set
  - `DIFY_APP_ID`: not set
- Follow-up Dify run: user supplied an app key and `https://api.dify.ai/v1`; the key was injected through a temporary shell variable and was not written to code, logs, or this report.
- Dify URL connectivity with the supplied key:
  - `GET https://api.dify.ai/v1/parameters` reached Dify and returned HTTP 403.
  - `POST /v1/chat-messages` reached Dify from the project CLI and returned HTTP 400 validation/application-mode errors described below.
- Model context limit used for CLI runs: `--max-context-tokens 32000`.

Important configuration finding: the code reads `DIFY_BASE_URL`, `DIFY_API_KEY`, `DIFY_USER`, and `CODEWIKI_MAX_CONTEXT_TOKENS` in `AnalysisCommandOptions`. It does not read `DIFY_API_URL` or `DIFY_APP_ID`, even though this evaluation request specified those variables.

## 2. Commands Executed

| Command | Result | Evidence |
|---|---:|---|
| `sed -n '1,320p' README.md` | OK | README documents analyze, review-diff, benchmark, 32k context budget, and Dify fallback behavior. |
| `sed -n '1,260p' pom.xml` | OK | Root Maven project has 8 modules and Java 21 properties. |
| `find . -maxdepth 3 -name pom.xml -print` | OK | Found 9 POM files including root and 8 modules. |
| `rg --files -g '*Test.java'` | OK | Found 25 test classes across all modules. |
| `java -version` | OK | OpenJDK Temurin 21.0.8. |
| `mvn -version` | OK | Maven 3.9.11, running on Java 24.0.2. |
| `git status --short --branch` | OK | `## main...origin/main`. |
| `python3 -c ... Dify env check ...` | OK | All requested Dify env vars absent. No secrets printed. |
| `rg --files -g '.env*' -g '*dify*' -g '*config*' -g '*.yml' -g '*.yaml' -g '*.properties'` | OK | No project Dify config file found. |
| `mvn test` | PASS | 9-module reactor build SUCCESS; 97 tests run, 0 failures/errors. Checkstyle emitted warnings but reported 0 violations. |
| `mvn -q -pl codewiki-cli -am dependency:build-classpath -Dmdep.outputFile=/private/tmp/codewiki-dify32k-deps.cp` | PASS | Built CLI dependency classpath for local runs. |
| `CodewikiCli analyze --repo /Users/anshi/agentscope-codewiki --output /private/tmp/codewiki-dify32k-eval/wiki-dify32k --log /private/tmp/codewiki-dify32k-eval/analyze-dify32k.log --dify --max-context-tokens 32000 --session-id dify32k-eval` | PASS with Dify fallback | Parsed 115 source files, generated 5 wiki pages, logged Dify fallback: missing base URL or API key. |
| `GET /v1/parameters` against `https://api.dify.ai/v1` using the supplied key | REACHED DIFY, HTTP 403 | Confirms network/API reached Dify, but parameters endpoint was forbidden for this key/app. |
| `CodewikiCli analyze ... --dify --dify-base-url https://api.dify.ai/v1 --dify-api-key <redacted> --session-id dify32k-real --max-context-tokens 32000` | PASS with Dify fallback | Dify returned HTTP 400: `conversation_id must be a valid UUID`. |
| `CodewikiCli analyze ... --dify --dify-base-url https://api.dify.ai/v1 --dify-api-key <redacted> --max-context-tokens 32000` | PASS with Dify fallback | Dify returned HTTP 400: `Agent Chat App does not support blocking mode`. |
| `CodewikiCli analyze --repo /Users/anshi/agentscope-codewiki --output /private/tmp/codewiki-dify32k-eval/review-mock --review-diff --base-ref 2e109d5 --head-ref 991de45 --mock --max-context-tokens 32000` | PASS | Produced `diff-review.md`, `impact-report.json`, `incremental-report.json`. |
| `CodewikiCli analyze --repo /Users/anshi/agentscope-codewiki --output /private/tmp/codewiki-dify32k-eval/review-dify-unreachable --review-diff --base-ref 2e109d5 --head-ref 991de45 --dify --dify-base-url http://127.0.0.1:9 --dify-api-key dummy-eval-key --dify-user codewiki-eval --dify-timeout-seconds 3 --max-context-tokens 32000` | PASS with Dify failure captured in report | Produced `diff-review.md`; summary says `Dify review failed; fallback evidence captured.` |
| `CodewikiCli analyze ... --review-diff --dify --dify-base-url https://api.dify.ai/v1 --dify-api-key <redacted> --max-context-tokens 32000` | PASS with Dify failure captured in report | Produced `diff-review.md`; summary says `Dify review failed; fallback evidence captured.` |
| `CodewikiCli benchmark --repo /Users/anshi/agentscope-codewiki --output /private/tmp/codewiki-dify32k-eval/benchmark` | PASS | Produced `benchmark-report.md`. |
| Five `CodewikiCli ask --repo ... --question ...` attempts | FAIL as expected | CLI returned `Missing command: analyze` and printed usage. No `ask` command exists. |
| `git diff 2e109d5 991de45 --stat` | OK | Small code diff: 4 files changed, 151 insertions, 7 deletions. |
| `jq` / `wc` / `python3` statistics over generated outputs | OK | Used to compute output sizes, estimated tokens, impact counts, and 32k findings. |

Generated evaluation artifacts:

- `/private/tmp/codewiki-dify32k-eval/analyze-dify32k.log`
- `/private/tmp/codewiki-dify32k-eval/wiki-dify32k/overview.md`
- `/private/tmp/codewiki-dify32k-eval/wiki-dify32k/architecture.md`
- `/private/tmp/codewiki-dify32k-eval/wiki-dify32k/modules.md`
- `/private/tmp/codewiki-dify32k-eval/wiki-dify32k/dataflow.md`
- `/private/tmp/codewiki-dify32k-eval/wiki-dify32k/review.md`
- `/private/tmp/codewiki-dify32k-eval/analyze-dify-real.log`
- `/private/tmp/codewiki-dify32k-eval/analyze-dify-real-nosession.log`
- `/private/tmp/codewiki-dify32k-eval/wiki-dify-real-nosession/overview.md`
- `/private/tmp/codewiki-dify32k-eval/wiki-dify-real-nosession/architecture.md`
- `/private/tmp/codewiki-dify32k-eval/wiki-dify-real-nosession/modules.md`
- `/private/tmp/codewiki-dify32k-eval/wiki-dify-real-nosession/dataflow.md`
- `/private/tmp/codewiki-dify32k-eval/wiki-dify-real-nosession/review.md`
- `/private/tmp/codewiki-dify32k-eval/review-mock/diff-review.md`
- `/private/tmp/codewiki-dify32k-eval/review-mock/impact-report.json`
- `/private/tmp/codewiki-dify32k-eval/review-mock/incremental-report.json`
- `/private/tmp/codewiki-dify32k-eval/review-dify-unreachable/diff-review.md`
- `/private/tmp/codewiki-dify32k-eval/review-dify-unreachable/impact-report.json`
- `/private/tmp/codewiki-dify32k-eval/review-dify-unreachable/incremental-report.json`
- `/private/tmp/codewiki-dify32k-eval/review-dify-real/diff-review.md`
- `/private/tmp/codewiki-dify32k-eval/review-dify-real/impact-report.json`
- `/private/tmp/codewiki-dify32k-eval/review-dify-real/incremental-report.json`
- `/private/tmp/codewiki-dify32k-eval/benchmark/benchmark-report.md`

## 3. Functional Results

### Repository indexing

Result: partially works.

Evidence:

- `analyze-dify32k.log`:
  - `parsed source files=115`
  - `wiki pages written=5, skipped=0`
- `git ls-files '*.java' '*.py' '*.ts' '*.tsx' | wc -l` returned `115`, matching the source-file parse count.
- `overview.md` reports:
  - `CodeWiki analyzed agentscope-codewiki with 115 source files and 1064 parsed symbols.`
  - `Files: 179`

Problems:

- Repository file count is polluted by local ignored/generated artifacts. `git ls-files | wc -l` returned `134`, while generated output reports `Files: 179`.
- `git status --ignored --short` showed ignored local directories: `wiki/`, `review/`, `review-v3/`, `benchmark/`, module `target/` directories, and validation reports.
- `benchmark-report.md` top largest files included ignored generated outputs such as `wiki/codewiki/.codewiki-summary.json`, `review/codewiki/diff-review.md`, and `wiki/codewiki/modules.md`.

Conclusion: source indexing for tracked Java/Python/TypeScript files is consistent, but repository-level file traversal does not honor `.gitignore` or `git ls-files`, so benchmark and repo metadata can be contaminated by local generated outputs.

### Wiki generation

Result: completes, but output quality is mostly heuristic and shallow without real Dify.

Evidence:

- Analyze run generated all 5 pages:
  - `overview.md`: 943 chars
  - `architecture.md`: 819 chars
  - `modules.md`: 92,947 chars
  - `dataflow.md`: 389 chars
  - `review.md`: 659 chars
- `overview.md` explicitly says:
  - `AgentScope mock analyzer used (reason: missing Dify base URL or API key).`
  - `Budget total tokens=21202, trimmed=false.`
- `architecture.md` contains only one top-level module node, `agentscope-codewiki`, despite the Maven build having 8 modules.
- `review.md` says manual review is still required.

Conclusion: wiki generation is operational, but it is not doing module-by-module Dify generation in this run. It creates one repository module with file summaries. The result is useful as a structural inventory, not as a high-quality DeepWiki-style explanation.

Follow-up with supplied Dify key:

- With `--session-id dify32k-real`, Dify returned HTTP 400 because `conversation_id` must be a valid UUID. This shows the CLI currently accepts arbitrary session ids but forwards them directly to Dify as `conversation_id`.
- Without `--session-id`, the request reached Dify but returned HTTP 400: `Agent Chat App does not support blocking mode`.
- `wiki-dify-real-nosession/overview.md` therefore still used the mock analyzer and included the fallback reason: `Agent Chat App does not support blocking mode`.

Conclusion after real Dify attempt: the Dify endpoint/key were usable enough to reach Dify, but the current adapter is incompatible with this Dify app because `DifyChatRequest.response_mode` is hardcoded to `blocking`.

### Repository Q&A

Result: not implemented in current CLI.

Evidence:

- `CodewikiCli` usage exposes only:
  - `codewiki analyze ...`
  - `codewiki analyze ... --review-diff ...`
  - `codewiki benchmark ...`
- Five actual `ask` attempts all failed with:
  - `Missing command: analyze`
  - CLI usage text
- No `ask` runner or ask command was found in CLI source.

Questions attempted:

1. `这个仓库有哪些模块，各自职责是什么？`
2. `analyze 命令如何构建 32k Dify 上下文？`
3. `Diff Review 是否只使用 changed files 和 impact context？`
4. `Impact analysis 是基于 graph 还是 LLM？`
5. `Wiki 生成是否按模块分批调用模型？`

Conclusion: repository Q&A cannot be evaluated as a working feature because no CLI command or endpoint exists.

### Diff review

Result: runs and emits reports, but the Dify prompt construction is not safe for 32k.

Evidence:

- Diff input: `git diff 2e109d5 991de45 --stat`
  - 4 files changed
  - 151 insertions
  - 7 deletions
- Mock diff review output:
  - `Reviewed 4 changed file(s), 28 changed symbol(s), and impact risk CRITICAL.`
  - Generated `diff-review.md`: 2,137,412 bytes
- Dify-unreachable diff review output:
  - Summary: `Dify review failed; fallback evidence captured.`
  - Generated `diff-review.md`: 2,130,886 bytes
  - Potential Risks contains `HIGH Dify review failed and needs retry before release sign-off.`
- Real Dify diff review with supplied key:
  - Generated `/private/tmp/codewiki-dify32k-eval/review-dify-real/diff-review.md`.
  - Summary: `Dify review failed; fallback evidence captured.`
  - Potential Risks contains `HIGH Dify review failed and needs retry before release sign-off.`
  - Output size: 2,130,899 bytes.
  - Impact statistics matched the mock run: 63 changed symbols, 1,034 direct impacts, 6,875 transitive impacts, risk `CRITICAL`.
- Source evidence:
  - `DiffReviewAgent.buildDifyPrompt(...)` concatenates changed files, changed symbols, full rendered impact analysis, full budgeted ContextPack, and full git diff.
  - `DiffReviewAgent.applyContextBudget(...)` only budgets the rendered `ContextPack`, not `Impact analysis` or `Git diff`.

Conclusion: diff review uses changed files and changed symbols to build retrieval context, and impact is graph-based before review. However, the final Dify prompt can still exceed 32k badly because impact analysis and git diff are outside the budget guard. With the supplied Dify app, the request also fails before model evaluation because the app does not support blocking mode.

### Impact analysis

Result: deterministic graph-based impact analysis works, but output is too large and noisy.

Evidence:

- `DiffReviewCliRunner` builds a `CodeGraph` from parsed symbols and calls `new ImpactAnalyzer(graph).analyze(...)`.
- `impact-report.json` statistics:
  - `changedSymbols`: 63
  - `directImpacts`: 1,034
  - `transitiveImpacts`: 6,875
  - `riskLevel`: `CRITICAL`
  - `affectedModules`: `[root]`
- `impact-report.json` size: 6,763,958 chars.
- The rendered impact section in `diff-review.md` is 2,010,677 chars, estimated 502,670 tokens.

Quality issue:

- The changed-symbol extraction includes suspicious symbols such as `if` and `UnchangedOnly.*`, based on rendered diff evidence.
- Impact edges contain many repeated-looking references and calls. This inflates risk and prompt size.

Conclusion: impact analysis is not LLM guessing; it is graph-based. But it needs deduplication, ranking, and hard output caps before it is safe to feed into a 32k Dify model.

### Benchmark

Result: command completes, but benchmark input is contaminated by ignored generated artifacts.

Evidence from `benchmark-report.md`:

- Repository Size: 1.3 MiB
- File Count: 179
- Language Distribution:
  - Java: 113
  - Markdown: 31
  - Other: 33
  - Python: 1
  - TypeScript: 1
- Parse Time: 7,725 ms
- Graph Build Time: 21,253 ms
- Index Time: 218 ms
- Impact Analysis Time: 46 ms
- Review Time: 103 ms
- Wiki Generation Time: 72 ms
- Peak Memory: 137.9 MiB
- Context Size: 77.2 KiB
- Token Usage: 19,765
- Dify Calls: 0
- Review Findings Count: 0

Contamination evidence:

- Top largest files include ignored generated outputs:
  - `wiki/codewiki/.codewiki-summary.json`
  - `review/codewiki/diff-review.md`
  - `wiki/codewiki/modules.md`
  - `wiki/QuestPhoneStream/.codewiki-summary.json`
  - `review/QuestPhoneStream/diff-review.md`
- `BenchmarkRunner.listFiles(...)` uses `Files.walk(repo)` and only filters `.git`, `target`, `build`, `node_modules`; it does not honor `.gitignore`.

Conclusion: benchmark itself runs, but its measurements are not reliable in a dirty local workspace with ignored generated artifacts.

## 4. 32k Context Findings

No successful real Dify answer was produced. Initially no Dify URL/key was available in the environment; after using the supplied key, requests reached Dify but failed with HTTP 400 because the app rejects the adapter's blocking mode. The following figures are based on actual CLI logs, generated reports, source prompt construction, and internal token estimates.

### Prompt/token statistics

| Scenario | Evidence source | Prompt/token result | 32k status |
|---|---|---:|---|
| Analyze/wiki Dify path | `analyze-dify32k.log` | `total tokens=21202`, `max_context_tokens=32000`, `trimmed=false` | Under 32k |
| Analyze/wiki real Dify path with arbitrary session id | `analyze-dify-real.log` | `total tokens=21202`, Dify HTTP 400 `conversation_id must be a valid UUID` | Under 32k but request invalid |
| Analyze/wiki real Dify path without session id | `analyze-dify-real-nosession.log` | `total tokens=21182`, Dify HTTP 400 `Agent Chat App does not support blocking mode` | Under 32k but app mode incompatible |
| Benchmark retrieval context | `benchmark-report.md` | `Token Usage: 19765`, `Dify Calls: 0` | Under 32k, but no Dify call |
| Diff review Dify path | generated diff-review sections plus source `buildDifyPrompt(...)` | lower-bound prompt estimate exceeds 535k tokens | Far over 32k |

Diff review lower-bound calculation:

- Rendered Impact Analysis section in `diff-review.md`: 2,010,677 chars, estimated 502,670 tokens.
- Review Evidence / ContextPack section: 119,353 chars, estimated 29,839 tokens.
- Raw `git diff 2e109d5 991de45`: 12,613 chars, estimated 3,154 tokens.
- Lower-bound total before changed-file/symbol/static prompt overhead: 2,142,643 chars, estimated 535,661 tokens.

Maximum prompt length observed/derived:

- Max estimated prompt tokens: at least 535,661 tokens in diff review Dify path.
- Max estimated prompt chars: at least 2,142,643 chars in diff review Dify path.

Average prompt length over would-be Dify calls:

- Analyze/wiki: 21,202 tokens.
- Diff review lower bound: 535,661 tokens.
- Average across these two Dify-capable paths: about 278,432 tokens.

### Did anything exceed 32k?

Yes.

- Analyze/wiki stayed under 32k.
- Benchmark retrieval context stayed under 32k but did not call Dify.
- Diff review exceeded 32k by at least 16x because the full impact analysis is appended outside the ContextPack budget.

### What was truncated?

- Analyze/wiki: nothing. Log says `trimmed=false`.
- Diff review: the ContextPack has a budget function, but the generated report does not show the actual trim marker at the top of review evidence. The evidence section itself is under 32k at about 29,839 tokens.
- Diff review impact analysis and git diff are not truncated by the context budget. This is the main 32k failure mode.

### Tasks most likely to fail

1. Diff review with high graph fan-out: impact analysis can explode into thousands of direct/transitive edges and dominate the prompt.
2. Impact analysis rendering: safe as a JSON artifact, unsafe when fully embedded into LLM prompt.
3. Benchmark on dirty workspaces: ignored generated outputs inflate file counts, repository size, and top-file metrics.
4. Repository Q&A: fails immediately because no `ask` command exists.
5. Wiki quality with real Dify unavailable: CLI falls back to mock and produces shallow heuristic pages.

## 5. Quality Problems Found

### P0: Repository Q&A is unavailable

Evidence: five `CodewikiCli ask ...` attempts all failed with `Missing command: analyze`. Current CLI usage only exposes analyze, review-diff, and benchmark.

Impact: one of the core requested capabilities cannot be evaluated or used.

### P0: Real Dify answers cannot run with the supplied app/key and current adapter

Evidence:

- Before the user supplied a key, `DIFY_API_URL`, `DIFY_BASE_URL`, `DIFY_API_KEY`, and `DIFY_APP_ID` were all absent. Analyze logged `Dify fallback reason: missing Dify base URL or API key`.
- After using the supplied key and endpoint, `POST /v1/chat-messages` reached Dify but returned:
  - HTTP 400 `conversation_id must be a valid UUID` when `--session-id dify32k-real` was used.
  - HTTP 400 `Agent Chat App does not support blocking mode` when no session id was used.

Impact: no successful real 32k Dify answer quality can be measured from this run. The project adapter must support this app type's response mode before model quality can be evaluated.

### P1: Diff review Dify prompt can exceed 32k by a large margin

Evidence: diff review lower-bound prompt estimate is at least 535,661 tokens. Source `buildDifyPrompt(...)` appends full `renderImpactAnalysis(...)`, budgeted context, and raw git diff; only the ContextPack is budgeted.

Impact: any real 32k Dify app is likely to reject, truncate, or produce incomplete/hallucinated review results on non-trivial diffs.

### P1: Impact analysis output is too large and noisy

Evidence: `impact-report.json` has 63 changed symbols, 1,034 direct impacts, 6,875 transitive impacts, and 6,763,958 chars. Rendered impact section alone is about 502,670 tokens.

Impact: downstream review prompt becomes unusable, and CRITICAL risk may be inflated by repeated or weak graph edges.

### P1: Benchmark does not honor `.gitignore`

Evidence: benchmark top largest files include ignored generated outputs under `wiki/` and `review/`; `git status --ignored --short` confirms those directories are ignored. Source `BenchmarkRunner.isIgnored(...)` only excludes `.git`, `target`, `build`, `node_modules`.

Impact: benchmark timing, repository size, file count, language distribution, and largest-file metrics are not reliable in common local workspaces.

### P1: Wiki generation is not module-aware enough

Evidence: `architecture.md` contains one module node, `agentscope-codewiki`, while root `pom.xml` declares 8 Maven modules. `modules.md` is large but mostly file-summary inventory.

Impact: generated wiki under-represents real architecture and module boundaries.

### P2: Prompt length / request body metrics are not logged

Evidence: analyze logs token buckets, but `DifyClient` does not log request body char length, estimated tokens, input sizes, response size, or conversation id. Diff review logs no prompt-size metrics.

Impact: 32k failures are hard to diagnose without external instrumentation.

### P2: Dify env var names do not match requested deployment convention

Evidence: code reads `DIFY_BASE_URL`, not `DIFY_API_URL`; it does not read `DIFY_APP_ID`.

Impact: deployment scripts using `DIFY_API_URL` will silently fall back or fail unless they map to `DIFY_BASE_URL`.

### P2: Diff review changed-symbol extraction is imprecise

Evidence: generated review includes diff symbols such as `if` and `UnchangedOnly.*` in a review of changes between `2e109d5` and `991de45`.

Impact: retrieval query and impact report include noise, increasing false positives and prompt size.

### P3: Existing Checkstyle warnings remain

Evidence: `mvn test` reports many warnings such as `WhitespaceAround`, `NeedBraces`, and `AvoidStarImport`, although final result is 0 violations and build success.

Impact: not blocking, but reduces signal quality in CI logs.

## 6. Recommended Optimization Direction

### Short term: 1 week

1. Add hard prompt accounting around every Dify call.
   - Log safe metrics only: query chars/tokens, each input field chars/tokens, total estimated tokens, trim status, response status.
   - Do not log API keys or full prompts by default.

2. Fix Dify env aliases.
   - Accept `DIFY_API_URL` as alias for `DIFY_BASE_URL`.
   - Decide whether `DIFY_APP_ID` is needed; if not, log that it is ignored.

3. Fix Dify response-mode compatibility.
   - Do not hardcode `response_mode="blocking"` for all apps.
   - Add configurable response mode and support streaming if the app is an Agent Chat App.
   - Validate `--session-id` as a UUID before forwarding it as Dify `conversation_id`, or separate local session ids from Dify conversation ids.

4. Cap diff review prompt.
   - Apply one total budget to changed files, changed symbols, impact summary, retrieved context, and git diff together.
   - Replace full impact edge dumps with top-N ranked impacts plus counts.

5. Exclude ignored/generated files from repository traversal.
   - Prefer `git ls-files` for Git repos.
   - Fall back to `Files.walk` only for non-Git directories.
   - Respect `.gitignore` and existing generated-output ignore rules.

6. Add a clear Q&A unsupported message or implement minimal `ask` skeleton.
   - Current generic `Missing command: analyze` is misleading for users trying Q&A.

### Medium term: 2-4 weeks

1. Implement real repository Q&A.
   - Add `codewiki ask --repo ... --question ...`.
   - Build or load an index, retrieve top-k chunks, assemble a budgeted prompt, call Dify or mock mode.
   - Persist answer evidence: retrieved files, snippets, scores, token counts.

2. Make wiki generation module-aware.
   - Discover Maven modules from root `pom.xml`.
   - Generate per-module summaries and architecture pages.
   - Call Dify per module/page instead of one repository-level prompt.

3. Improve impact graph quality.
   - Deduplicate equivalent edges.
   - Separate direct code dependencies from weak textual references.
   - Rank impacts by changed symbol proximity, relation type, and module boundary.
   - Add max edge counts for JSON and prompt rendering.

4. Add context-budget tests for large diffs.
   - Test prompt stays under 32k for generated high-fanout impact graphs.
   - Test truncation clearly records what was dropped.

### Long term: architecture upgrade

1. Introduce persistent indexing.
   - Store chunked source documents, symbols, summaries, dependency graph, and embeddings.
   - Avoid rebuilding everything for each CLI command.

2. Separate internal reports from LLM prompt views.
   - Full `impact-report.json` can remain large.
   - LLM prompt should receive a compressed `ImpactPromptView` with top risks, counts, and evidence links.

3. Add multi-stage Dify workflows.
   - Stage 1: module summaries.
   - Stage 2: graph/impact compression.
   - Stage 3: page or Q&A answer generation from retrieved evidence only.
   - Stage 4: citation/consistency validation.

4. Add evaluator harness.
   - Store gold questions, expected evidence files, and rubric checks.
   - Capture prompt metrics and answer quality over time.

## 7. Concrete Code Change Plan

No business code was changed in this evaluation. Recommended future changes:

### Classes to modify

- `codewiki-cli/src/main/java/com/agentscope/codewiki/cli/AnalysisCommandOptions.java`
  - Accept `DIFY_API_URL` as alias for `DIFY_BASE_URL`.
  - Add validation and safe config diagnostics for Dify.

- `codewiki-dify/src/main/java/com/agentscope/codewiki/dify/DifyClient.java`
  - Add request/response metrics hooks.
  - Log body size and estimated tokens without logging secrets or full prompt.

- `codewiki-dify/src/main/java/com/agentscope/codewiki/dify/DifyChatRequest.java`
  - Make response mode configurable.
  - Support app-compatible streaming mode where required.

- `codewiki-dify/src/main/java/com/agentscope/codewiki/dify/DifyChatModel.java`
  - Distinguish local session ids from Dify conversation ids.
  - Validate Dify conversation ids before sending.

- `codewiki-cli/src/main/java/com/agentscope/codewiki/cli/ContextBudgetReport.java`
  - Add total payload-size accounting for query + inputs.
  - Expose per-field char/token counts in logs and output artifacts.

- `codewiki-agent/src/main/java/com/agentscope/codewiki/agent/DiffReviewAgent.java`
  - Replace `buildDifyPrompt(...)` with a full-budget prompt builder.
  - Budget impact analysis and git diff, not only ContextPack.
  - Add top-N impact rendering and explicit truncation markers.

- `codewiki-indexer/src/main/java/com/agentscope/codewiki/indexer/impact/ImpactAnalyzer.java`
  - Deduplicate edges.
  - Rank and cap direct/transitive impact lists.
  - Separate output modes: full JSON vs prompt-safe summary.

- `codewiki-cli/src/main/java/com/agentscope/codewiki/cli/BenchmarkRunner.java`
  - Use Git-aware file listing or `.gitignore` matching.
  - Exclude generated outputs consistently with `.gitignore`.

- `codewiki-cli/src/main/java/com/agentscope/codewiki/cli/EndToEndAnalysisRunner.java`
  - Use module discovery instead of a single root module.
  - Generate per-module wiki context.

- New classes:
  - `AskCommandOptions`
  - `AskCliRunner`
  - `PromptMetrics`
  - `PromptBudgeter`
  - `ImpactPromptView`

### Tests to add

- `AnalysisCommandOptionsTest`
  - `DIFY_API_URL` maps to effective base URL.
  - `DIFY_BASE_URL` remains supported.

- `DifyClientTest`
  - Request metrics are emitted without API key leakage.

- `DifyChatRequestTest` / `DifyChatModelTest`
  - Agent Chat App-compatible response mode can be configured.
  - Non-UUID local session ids are not sent as Dify `conversation_id`.

- `DiffReviewAgentTest`
  - Full Dify prompt remains under configured 32k token budget.
  - Oversized impact reports are summarized and marked as truncated.
  - Raw git diff is capped.

- `ImpactAnalyzerTest`
  - Duplicate edges are removed.
  - High-fanout graph returns capped prompt view.

- `BenchmarkRunnerTest`
  - Ignored directories `wiki/`, `review/`, `benchmark/`, `target/`, and `.git` are excluded.
  - Git-tracked file count matches benchmark file count on clean fixtures.

- `CodewikiCliEndToEndTest`
  - `ask` command answers from retrieved evidence in mock mode.
  - Unsupported commands produce command-specific help.

- `RepositoryWikiGeneratorTest` / new module discovery tests
  - Multi-module Maven repo produces distinct module summaries.

### Config to add

- Optional CLI/env configuration:
  - `CODEWIKI_DIFY_BASE_URL`
  - `DIFY_API_URL` alias
  - `CODEWIKI_PROMPT_METRICS=true`
  - `CODEWIKI_MAX_CONTEXT_TOKENS=32000`
  - `CODEWIKI_MAX_IMPACT_PROMPT_EDGES`
  - `CODEWIKI_MAX_DIFF_CHARS`

### Verification plan

1. Run `mvn test`.
2. Run analyze on current repo with `--mock --max-context-tokens 32000`.
3. Run analyze on current repo with real Dify env, confirming:
   - prompt total <= 32k
   - request metrics logged
   - no key leakage
4. Run `ask` with the same five repository questions and verify:
   - answers cite retrieved files
   - prompt total <= 32k
5. Run diff review on `2e109d5..991de45` and verify:
   - generated prompt <= 32k
   - impact summary is capped
   - full impact JSON remains available as an artifact
6. Run benchmark in a workspace containing ignored generated outputs and verify they are excluded.
7. Run `git diff --check`.
