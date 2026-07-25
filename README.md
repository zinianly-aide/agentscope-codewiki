# AgentScope CodeWiki

> Code analysis system built with AgentScope Java — DeepWiki-style code intelligence.

## Project Vision

AgentScope CodeWiki transforms any Git repository into an interactive, queryable knowledge base.
By combining multi-agent orchestration (AgentScope) with deep code parsing, indexing, and
retrieval, CodeWiki enables developers to ask natural-language questions and receive precise,
context-aware answers about their codebase — similar to how DeepWiki understands and explains
code at scale.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     codewiki-web                            │
│                 (Spring Boot REST API)                      │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────┴──────────────────────────────────┐
│                     codewiki-agent                          │
│              (AgentScope Multi-Agent Pipeline)              │
└──┬──────────────┬──────────────┬──────────────┬─────────────┘
   │              │              │              │
┌──┴──┐    ┌──────┴──────┐ ┌─────┴──────┐ ┌────┴──────────┐
│Parser│    │  Indexer    │ │ Retriever  │ │ codewiki-dify │
│      │    │             │ │            │ │ (Dify Plugin) │
└──┬───┘    └──────┬──────┘ └─────┬──────┘ └───────────────┘
   │               │              │
┌──┴───────────────┴──────────────┴──────────────────────────┐
│                      codewiki-core                         │
│            (Shared Models, Interfaces, Utilities)          │
└────────────────────────────────────────────────────────────┘
```

| Module | Purpose |
|--------|---------|
| **codewiki-core** | Shared domain models, interfaces, and utilities |
| **codewiki-parser** | AST parsing and symbol extraction |
| **codewiki-indexer** | Code knowledge graph construction and index management |
| **codewiki-retriever** | Semantic and structural code search |
| **codewiki-agent** | Wiki generation, impact analysis, and diff-review workflows |
| **codewiki-dify** | Dify platform integration plugin |
| **codewiki-cli** | End-to-end local analysis command for acceptance runs |
| **codewiki-web** | Spring Boot application shell for future API and UI work |

## Current Status

The repository analysis pipeline is implemented and covered by unit and end-to-end tests. It
supports Git-aware repository scanning, Java/Python/TypeScript symbol extraction, code graph
construction, hybrid retrieval, incremental indexing, deterministic impact analysis, mock or
Dify-backed diff review, wiki generation, and benchmark reporting.

The web module is currently an application shell; REST endpoints, API documentation, and a web
UI are not implemented yet. Likewise, the current workflow classes are orchestration facades,
not a full AgentScope multi-agent runtime.

## Quick Start

Run an end-to-end local acceptance analysis and generate Markdown wiki pages:

```bash
mvn -pl codewiki-cli -am test
java -cp codewiki-cli/target/classes:codewiki-core/target/classes:codewiki-parser/target/classes:codewiki-retriever/target/classes:codewiki-agent/target/classes:codewiki-dify/target/classes \
  com.agentscope.codewiki.cli.CodewikiCli analyze --repo /path/to/repo --output ./wiki --mock
```

The command scans the Git repository, parses supported code symbols, generates or reuses a local summary cache, builds retrieval context, runs the AgentScope workflow path in mock mode, and writes `overview.md`, `architecture.md`, `modules.md`, `dataflow.md`, and `review.md`.

For a step-by-step operating procedure covering local setup, mock mode, Dify mode, diff review, benchmark runs, outputs, and troubleshooting, see [CodeWiki 使用 SOP](docs/usage-sop.zh-CN.md).

### Code Graph

`codewiki-indexer` builds a code knowledge graph from the existing symbol extraction output. It reuses `CodeSymbol`, `RepositoryScanner`, and `TreeSitterSymbolExtractor` instead of introducing a second source parser. The graph serializes to `graph.json` with this top-level shape:

```json
{
  "nodes": [],
  "edges": []
}
```

Supported source languages are Java, TypeScript, and Python. Supported relationships are `CALLS`, `IMPORTS`, `IMPLEMENTS`, `EXTENDS`, `USES`, and `REFERENCES`. `CodeGraphQueryService` supports symbol-to-related-symbol lookup, file-to-impacted-file lookup, and module dependency-chain traversal.

### Impact Analysis

`codewiki-indexer` includes a deterministic `ImpactAnalyzer` that reuses `CodeGraph`, `CodeGraphQueryService`, and incremental-indexing inputs to compute downstream effects without an LLM, diff review, or web UI. `codewiki-agent` exposes the same engine through `ImpactAnalysisAgent` for agent pipeline orchestration.

Inputs:

- changed files, including paths from `IncrementalReport`
- changed symbols, such as `UserService.login` or `login`

Output is written as `impact-report.json` with this top-level shape:

```json
{
  "changedSymbols": [],
  "directImpacts": [],
  "transitiveImpacts": [],
  "riskLevel": "LOW",
  "affectedModules": []
}
```

Supported analysis modes:

- Symbol impact: changed symbols to direct and transitive callers/dependents.
- File impact: changed file to dependent files.
- Module impact: changed module to dependent module chains.
- Risk calculation: `LOW`, `MEDIUM`, `HIGH`, or `CRITICAL` based on impacted node count, affected module count, and call-chain depth.

### AI Diff Review

`codewiki-agent` includes an AI Diff Review Engine that turns `git diff`, changed files, and `impact-report.json` into `diff-review.md`. The engine extracts changed symbols from added diff lines, reuses `ImpactAnalyzer`/`ImpactReport` for downstream risk context, retrieves related code with `HybridRetriever`, builds a `ContextPack`, applies a context-budget guard, and then runs either local mock review logic or `DifyChatModel`.

Inputs:

- `git diff` text
- changed file paths
- `impact-report.json` / `ImpactReport`

Output sections in `diff-review.md`:

- `# Summary`
- `# Changed Files`
- `# Impact Analysis`
- `# Potential Risks`
- `# Code Quality Issues`
- `# Security Concerns`
- `# Performance Concerns`
- `# Suggested Tests`
- `# Suggested Refactors`
- `# Review Evidence`

Review findings support severities `INFO`, `LOW`, `MEDIUM`, `HIGH`, and `CRITICAL`. Each finding can cite file path, class name, method name, and review evidence so reviewers can trace the recommendation back to the diff, impact analysis, retrieved `ContextPack`, or Dify response.

Supported modes:

- Mock mode: `DiffReviewAgent.mockMode(corpus, maxContextTokens)` performs deterministic local review and is suitable for tests/offline runs.
- Dify mode: `DiffReviewAgent.difyMode(corpus, difyChatModel, maxContextTokens)` sends a budgeted prompt to `DifyChatModel` and records model evidence in the report.

### Benchmark

`codewiki-cli` includes a benchmark/profiling command for real repository paths. It runs the existing scan, parse, graph build, retrieval/index, impact analysis, mock diff review, and wiki generation paths for timing only, then writes `benchmark-report.md`.

Example:

```bash
java -cp codewiki-cli/target/classes:codewiki-core/target/classes:codewiki-parser/target/classes:codewiki-indexer/target/classes:codewiki-retriever/target/classes:codewiki-agent/target/classes:codewiki-dify/target/classes \
  com.agentscope.codewiki.cli.CodewikiCli benchmark \
  --repo /path/to/repo \
  --output ./benchmark
```

The report includes repository size, file count, language distribution, parse time, graph build time, index time, impact analysis time, review time, wiki generation time, peak memory, context size, token usage, Dify calls, review findings count, top 10 largest files, top 10 most connected symbols, and top 10 high risk modules.

Useful flags:

- `--dry-run` executes the pipeline and writes the run log without writing wiki pages or summary cache.
- `--mock` forces local mock workflow output and does not call Dify.
- `--dify` calls Dify. If Dify fails, the CLI logs the fallback reason and continues with the local mock analyzer.
- `--dify-response-mode streaming` uses Dify streaming responses and is required for Dify Agent Chat apps that reject blocking mode.
- `--log ./run.log` writes a detailed run log to a custom path.
- `--max-context-tokens 32000` enables the context budget guard and automatic trimming.
- `--session-id <id>` carries a Dify conversation/session id into the runtime context.

Real Dify example:

```bash
export DIFY_BASE_URL=https://api.dify.ai
export DIFY_API_KEY=app-xxxxxxxx
export DIFY_USER=codewiki-local
export CODEWIKI_DIFY_RESPONSE_MODE=streaming
export CODEWIKI_MAX_CONTEXT_TOKENS=32000

java -cp codewiki-cli/target/classes:codewiki-core/target/classes:codewiki-parser/target/classes:codewiki-retriever/target/classes:codewiki-agent/target/classes:codewiki-dify/target/classes \
  com.agentscope.codewiki.cli.CodewikiCli analyze \
  --repo /path/to/repo \
  --output ./wiki \
  --dify \
  --dify-response-mode streaming \
  --dify-timeout-seconds 60 \
  --session-id local-acceptance
```

CLI arguments override environment variables, so this is also valid:

```bash
com.agentscope.codewiki.cli.CodewikiCli analyze \
  --repo /path/to/repo \
  --output ./wiki \
  --dify \
  --dify-base-url https://api.dify.ai \
  --dify-api-key app-xxxxxxxx \
  --dify-user codewiki-local \
  --dify-response-mode streaming \
  --max-context-tokens 32000
```

For 32k-context Dify apps, use `CODEWIKI_MAX_CONTEXT_TOKENS=32000` or `--max-context-tokens 32000`. The CLI logs token estimates for `repo_summary`, `module_summaries`, `retrieved_code_context`, `dependency_graph`, `memory_summary`, and `total`; when the budget is exceeded it preserves the user question, high-relevance code, file paths/line numbers, and module summaries while trimming low-relevance code, long comments, duplicate/oversized snippets, and old memory first.

## Enterprise Single-Agent REST API

`codewiki-web` now includes a minimal enterprise internal single-agent runtime baseline with deterministic and future LLM ReAct execution modes. The default remains deterministic, so the service starts and answers in mock mode without model API keys.

`codewiki-web` includes this enterprise internal single-agent REST path:

```text
HTTP POST /api/agent/chat
  -> EnterpriseAgentController
  -> EnterpriseAgentService
  -> enterpriseAgent (single ReAct-style agent bean, max-iters default 6)
  -> local sms-analysis Skill
  -> query_sms_summary Java Tool
  -> run_python Docker Tool only when exact ratios/sorting/checks are needed
  -> final answer
```

This first version is intentionally small: no multi-agent orchestration, Redis, Nacos, Kafka,
vector database, MCP Gateway, WebSocket, Kubernetes, approval center, or separate sandbox service.
The local repository currently does not contain an external AgentScope Java runtime dependency or
stable ReAct API; the implementation follows the existing project's AgentScope-style facade pattern
and keeps the runtime in `codewiki-web` so the build remains reproducible.

### Requirements

- Java 21 or newer. The project is configured for Java 21.
- Maven 3.9+.
- Docker only if `run_python` should execute real Python calculations.

Prepare the Python image:

```bash
docker pull python:3.12-alpine
```

### Configuration

No secrets are committed. Configure through environment variables or `application.yml`:

```yaml
enterprise:
  agent:
    model: ${AGENT_MODEL:}
    api-key: ${AGENT_API_KEY:}
    base-url: ${AGENT_BASE_URL:}
    max-iters: ${AGENT_MAX_ITERS:6}
    execution-mode: ${AGENT_EXECUTION_MODE:deterministic} # deterministic or llm-react
    max-sessions: ${AGENT_MAX_SESSIONS:10000}
    session-expire-after-access: ${AGENT_SESSION_EXPIRE_AFTER_ACCESS:30m}
    max-turns-per-session: ${AGENT_MAX_TURNS_PER_SESSION:10}
    skills-root: ${AGENT_SKILLS_ROOT:skills}
    max-skill-file-bytes: ${AGENT_MAX_SKILL_FILE_BYTES:65536}
  sms-api:
    base-url: ${SMS_API_BASE_URL:http://127.0.0.1:8081}
    token: ${SMS_API_TOKEN:}
    mock-enabled: ${SMS_API_MOCK_ENABLED:true}
```

Mock SMS mode is enabled by default and returns fixed read-only data where `platform-b` has the
higher sensitive-word block rate. To use a real internal API, set:

```bash
export SMS_API_MOCK_ENABLED=false
export SMS_API_BASE_URL=http://127.0.0.1:8081
export SMS_API_TOKEN=***
```

The read-only SMS tool calls:

```text
GET {SMS_API_BASE_URL}/internal/sms/summary?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD
Authorization: Bearer ${SMS_API_TOKEN}
Accept: application/json
```

### Build and run

```bash
mvn -pl codewiki-web -am test
mvn -pl codewiki-web -am package
java -jar codewiki-web/target/codewiki-web-0.0.1-SNAPSHOT.jar
```

For local development from the web module after dependencies have been built, this also works:

```bash
cd codewiki-web
mvn spring-boot:run
```

### Chat API example

```bash
curl -X POST http://127.0.0.1:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "E10086",
    "sessionId": "sms-analysis-001",
    "requestId": "req-001",
    "message": "查询2026年7月1日至7月7日短信敏感词拦截情况，判断哪个平台异常"
  }'
```

Response shape:

```json
{
  "requestId": "req-001",
  "answer": "...",
  "executionMode": "deterministic"
}
```

Demo UI:

```bash
java -jar codewiki-mcp-server/target/codewiki-mcp-server-0.0.1-SNAPSHOT.jar
java -jar codewiki-web/target/codewiki-web-0.0.1-SNAPSHOT.jar
open http://127.0.0.1:8080/agent.html
```

MCP demo server:

```bash
curl http://127.0.0.1:8091/mcp/tools
curl -X POST http://127.0.0.1:8091/mcp/tools/get_platform_owner/call \
  -H "Content-Type: application/json" \
  -d '{"arguments":{"platform":"platform-b"}}'
```

Async conversation + SSE:

```bash
curl -X POST http://127.0.0.1:8080/api/agent/conversations \
  -H "Content-Type: application/json" \
  -d '{"userId":"E10086","sessionId":"sms-analysis-001","requestId":"req-001","message":"查询2026年7月1日至7月7日短信敏感词拦截情况，找到异常平台，并查询负责人，生成告警摘要"}'

curl -N http://127.0.0.1:8080/api/agent/conversations/<conversationId>/events
```

Health endpoint:

```bash
curl http://127.0.0.1:8080/actuator/health
```

The health response includes application health, Docker availability, whether `python:3.12-alpine` is present, SMS API mock/real mode, and discovered Skill count. It intentionally omits tokens, API keys, Authorization headers, and full internal endpoint URLs.

### Current security boundaries

- All implemented enterprise tools are read-only.
- `query_sms_summary` validates date format, end date, and 90-day max range; model/user input cannot
  provide arbitrary URLs.
- API tokens are not logged or returned in errors.
- `run_python` uses `ProcessBuilder(List<String>)` and Docker arguments, not shell strings.
- Docker sandbox uses `--network=none`, memory/CPU/PID limits, `--cap-drop=ALL`, `--security-opt=no-new-privileges`, non-root `--user=65534:65534`, read-only filesystem, tmpfs `/tmp`, no host mounts, no Docker Socket, and a 20-second timeout with 64 KB output cap.
- The Python keyword blacklist is only a supplemental pre-check; Docker isolation and resource limits are the primary sandbox boundary.
- Tool calls return structured `ToolResult<T>` values with success, toolName, data, source, durationMs, errorCode, and errorMessage; the agent does not parse natural-language tool errors for control flow.
- Session isolation is a Caffeine in-memory cache keyed by `userId + sessionId`, with configurable maximum size, expire-after-access, and recent-turn cap. It is suitable for this minimal development version only.

## Tech Stack

- **Java 21** — virtual threads, pattern matching, sealed classes
- **Spring Boot 3.5** — modern Spring ecosystem
- **Maven** — multi-module build
- **Lombok** — boilerplate reduction
- **JUnit 5** — testing
- **Spotless** + **Checkstyle** — code quality

## Roadmap

### Phase 1 — Foundation (current)
- [x] Multi-module Maven project scaffolding
- [x] Java 21 + Spring Boot 3.5 baseline
- [x] Code quality toolchain (Spotless, Checkstyle)

### Phase 2 — Core Parsing
- [x] Git-aware repository scanning and file traversal
- [x] Multi-language symbol extraction (Java, Python, TypeScript)
- [x] Symbol extraction and code graph construction

### Phase 3 — Indexing & Retrieval
- [x] Hybrid embedding + keyword retrieval
- [x] Incremental index updates
- [ ] Semantic code search API

### Phase 4 — Analysis Pipeline
- [x] End-to-end CLI analysis and wiki generation
- [x] Context assembly and context-budget enforcement
- [x] Deterministic impact analysis and diff review
- [ ] AgentScope multi-agent orchestration
- [ ] Interactive query understanding and response generation

### Phase 5 — Platform Integration
- [x] Dify-compatible chat and embedding integration
- [ ] REST API and Swagger documentation
- [ ] Web UI with code exploration

## License

Apache 2.0
