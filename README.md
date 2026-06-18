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
| **codewiki-parser** | AST parsing, symbol extraction, and code graph construction |
| **codewiki-indexer** | Embedding generation and vector index management |
| **codewiki-retriever** | Semantic and structural code search |
| **codewiki-agent** | AgentScope-driven multi-agent pipeline orchestration |
| **codewiki-dify** | Dify platform integration plugin |
| **codewiki-cli** | End-to-end local analysis command for acceptance runs |
| **codewiki-web** | Spring Boot web API and frontend |

## Quick Start

Run an end-to-end local acceptance analysis and generate Markdown wiki pages:

```bash
mvn -pl codewiki-cli -am test
java -cp codewiki-cli/target/classes:codewiki-core/target/classes:codewiki-parser/target/classes:codewiki-retriever/target/classes:codewiki-agent/target/classes:codewiki-dify/target/classes \
  com.agentscope.codewiki.cli.CodewikiCli analyze --repo /path/to/repo --output ./wiki --mock
```

The command scans the Git repository, parses supported code symbols, generates or reuses a local summary cache, builds retrieval context, runs the AgentScope workflow path in mock mode, and writes `overview.md`, `architecture.md`, `modules.md`, `dataflow.md`, and `review.md`.

Useful flags:

- `--dry-run` executes the pipeline and writes the run log without writing wiki pages or summary cache.
- `--mock` forces local mock workflow output and does not call Dify.
- `--dify` calls Dify. If Dify fails, the CLI logs the fallback reason and continues with the local mock analyzer.
- `--log ./run.log` writes a detailed run log to a custom path.
- `--max-context-tokens 32000` enables the context budget guard and automatic trimming.
- `--session-id <id>` carries a Dify conversation/session id into the runtime context.

Real Dify example:

```bash
export DIFY_BASE_URL=https://api.dify.ai
export DIFY_API_KEY=app-xxxxxxxx
export DIFY_USER=codewiki-local
export CODEWIKI_MAX_CONTEXT_TOKENS=32000

java -cp codewiki-cli/target/classes:codewiki-core/target/classes:codewiki-parser/target/classes:codewiki-retriever/target/classes:codewiki-agent/target/classes:codewiki-dify/target/classes \
  com.agentscope.codewiki.cli.CodewikiCli analyze \
  --repo /path/to/repo \
  --output ./wiki \
  --dify \
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
  --max-context-tokens 32000
```

For 32k-context Dify apps, use `CODEWIKI_MAX_CONTEXT_TOKENS=32000` or `--max-context-tokens 32000`. The CLI logs token estimates for `repo_summary`, `module_summaries`, `retrieved_code_context`, `dependency_graph`, `memory_summary`, and `total`; when the budget is exceeded it preserves the user question, high-relevance code, file paths/line numbers, and module summaries while trimming low-relevance code, long comments, duplicate/oversized snippets, and old memory first.

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
- [ ] Repository cloning and file traversal
- [ ] Multi-language AST parsing (Java, Python, TypeScript)
- [ ] Symbol extraction and code graph construction

### Phase 3 — Indexing & Retrieval
- [ ] Hybrid embedding + keyword indexing
- [ ] Incremental index updates
- [ ] Semantic code search API

### Phase 4 — Agent Pipeline
- [ ] AgentScope multi-agent orchestration
- [ ] Query understanding and decomposition
- [ ] Context assembly and response generation

### Phase 5 — Platform Integration
- [ ] REST API and Swagger documentation
- [ ] Dify plugin integration
- [ ] Web UI with code exploration

## License

Apache 2.0
