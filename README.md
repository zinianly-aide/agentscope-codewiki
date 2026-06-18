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
| **codewiki-web** | Spring Boot web API and frontend |

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
