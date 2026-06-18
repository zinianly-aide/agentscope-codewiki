# CodeWiki — Class Diagram

## Summary Model Hierarchy

```
┌─────────────────────────────────────────────────────┐
│                 RepositorySummary                   │
│  name, branch, commitId, languages, fileCount       │
│  buildFiles, readmePath, summary                    │
│  modules: List<ModuleSummary>                       │
└───────────────────────┬─────────────────────────────┘
                        │ *
┌───────────────────────┴─────────────────────────────┐
│                   ModuleSummary                     │
│  name, path, buildSystem, summary                   │
│  files: List<FileSummary>                           │
│  subModules: List<ModuleSummary>  ◄── recursive     │
└───────────────────────┬─────────────────────────────┘
                        │ *
┌───────────────────────┴─────────────────────────────┐
│                    FileSummary                      │
│  name, path, language, linesOfCode, summary         │
│  classes: List<ClassSummary>                        │
│  functions: List<FunctionSummary>                   │
└────────┬───────────────────────────┬────────────────┘
         │ *                         │ *
┌────────┴──────────┐     ┌──────────┴───────────────┐
│   ClassSummary    │     │   FunctionSummary         │
│                   │     │                           │
│  name             │     │  name                     │
│  kind ◄───────────│─────│── class | interface       │  kind ◄─── method | constructor
│                   │     │    | enum | record        │           | function | arrow
│  modifiers        │     │                           │
│  superClass       │     │  signature                │
│  interfaces       │     │  returnType               │
│  methods ◄────────│─────│── List<FunctionSummary>   │  parameters: List<Parameter>
│  innerClasses ◄───│──┐  │  summary ◄ LLM-generated  │  summary ◄ LLM-generated
│  summary          │  │  │  startLine, endLine       │  startLine, endLine
│  startLine        │  │  │  filePath                 │  filePath
│  endLine          │  │  └───────────────────────────┘
│  filePath         │  │
└───────────────────┘  │  ┌───────────────────────────┐
                        └──│ ClassSummary (inner)      │
                           └───────────────────────────┘
```

## Interfaces

```
┌──────────────────────────────────────┐
│       SummaryGenerator<I, O>         │
├──────────────────────────────────────┤
│ + generate(I input): O               │
│ + generateAll(List<I>): List<O>      │
└──────────────────────────────────────┘
         △
         │ implements
         │
┌────────┴─────────────────────────────┐
│    FunctionSummaryGenerator          │
│  (CodeSymbol → FunctionSummary)      │
├──────────────────────────────────────┤
│    ClassSummaryGenerator             │
│  (ClassSymbols → ClassSummary)       │
├──────────────────────────────────────┤
│    FileSummaryGenerator              │
│  (Path → FileSummary)                │
├──────────────────────────────────────┤
│    ModuleSummaryGenerator            │
│  (ModulePath → ModuleSummary)        │
├──────────────────────────────────────┤
│    RepositorySummaryGenerator        │
│  (RepositoryMetadata → RepoSummary)  │
└──────────────────────────────────────┘

┌──────────────────────────────────────┐
│          SummaryStorage              │
├──────────────────────────────────────┤
│ + save(RepositorySummary): void      │
│ + load(String): Optional<...>        │
│ + delete(String): boolean            │
│ + exists(String): boolean (default)  │
└──────────────────────────────────────┘
         △
         │ implements
         │
┌────────┴─────────────────────────────┐
│    JsonFileSummaryStorage            │
│  (local JSON files)                  │
├──────────────────────────────────────┤
│    InMemorySummaryStorage            │
│  (ConcurrentHashMap)                 │
└──────────────────────────────────────┘
```

## Data Flow

```
Source Files                  Models                      Consumers
────────────                 ─────────                   ──────────

.java   ─┐                ┌─ FunctionSummary ──┐
.py     ─┤ tree-sitter    │                     │
.ts     ─┘          ───▶  ├─ ClassSummary       ├──▶ SummaryStorage
                           │                     │       (JSON/DB)
                           ├─ FileSummary        │
          RepositoryScanner│                     ├──▶ REST API
          + RepoMetadata ─▶├─ ModuleSummary      │
                           │                     ├──▶ Dify Plugin
                           └─ RepositorySummary ─┘
```

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Hierarchy: Repository → Module → File → Class → Function | Mirrors natural code organization; enables drill-down UIs |
| `summary` field on every level | Each node is independently meaningful for LLM context injection |
| `SummaryGenerator<I,O>` generic interface | Allows pluggable generators (LLM, template, heuristic) |
| `SummaryStorage` as interface | Decouples persistence from model; enables swap between JSON files, DB, cache |
| `@JsonInclude(NON_NULL)` | Clean serialization; omits empty lists/optionals |
| Recursive `ModuleSummary.subModules` | Supports nested Maven modules, monorepos |
| `FunctionSummary.kind` vs `ClassSummary.kind` | Type-safe yet flexible — future languages can add kinds without model changes |
