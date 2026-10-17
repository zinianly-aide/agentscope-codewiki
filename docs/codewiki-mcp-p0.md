# CodeWiki code-analysis tools (P0)

This step exposes the existing parser/indexer/retriever via both the original custom HTTP bridge
and a **stateless JSON-RPC subset of Streamable HTTP MCP**. It does not implement stdio,
server-initiated notifications, or every optional MCP feature.

## Enable for one trusted local repository

The feature is opt-in and bound to loopback by default.

```bash
export CODEWIKI_ANALYSIS_ENABLED=true
export CODEWIKI_REPOSITORY_ROOT=/absolute/path/to/trusted/repository
# Required for standalone JAR symbol/impact analysis; use the checked-out parser bridge.
export CODEWIKI_TREE_SITTER_BRIDGE=/absolute/path/to/codewiki-parser/src/main/resources/tree-sitter-bridge/extract-symbols.mjs
# The bridge directory must have its compatible Node.js dependencies installed.
mvn -pl codewiki-mcp-server -am package
java -jar codewiki-mcp-server/target/codewiki-mcp-server-0.0.1-SNAPSHOT.jar
```

Check tools:

```bash
curl -s http://127.0.0.1:8091/mcp/tools
curl -s -X POST http://127.0.0.1:8091/mcp/tools/codewiki_search_symbol/call \
  -H 'Content-Type: application/json' \
  -d '{"arguments":{"query":"ImpactAnalyzer"}}'
```

Tools currently available once enabled:

- `codewiki_search_code` — hybrid retrieval with source-path evidence and short snippets.
- `codewiki_search_symbol` — parsed symbol names and file/line locations.
- `codewiki_analyze_impact` — risk grade, affected modules, direct and transitive dependents; accepts `file` (repository-relative) and/or `symbol`.
- `codewiki_index_status` — file/symbol counts and incremental parsing activity (without exposing server paths).

The existing Web Agent may discover these tools through `HttpMcpClient`. When the Web Agent started before the analysis server, call the existing `POST /internal/mcp/refresh` **only on the trusted local Web instance**. Use `AGENT_EXECUTION_MODE=llm-react` and configure a compatible model for autonomous tool selection.

## Current limits / security

- Explicitly configured repository root only; callers cannot choose arbitrary roots.
- Disabled unless both configuration values are present and valid; never auto-exposes the user's home directory.
- Ignores hidden files, symlink files, build outputs, node_modules, and unsupported languages.
- Hard limits: 800 source files, 128 KiB per file, 4 MiB aggregate, 3,500 parsed symbols. A limit failure returns `REPOSITORY_LIMIT` rather than partial results.
- Bound to `127.0.0.1` unless `MCP_SERVER_BIND_ADDRESS` is explicitly overridden. There is no production authentication on the existing demo protocol.
- Symbol and impact analysis require Node.js and the parser bridge's `node_modules`; a fat JAR cannot run the bridge directly from its bundled resource URL.
- The Node bridge parses files in one batch to reduce startup cost. Graph and retrieval are still calculated from source on each request; no persistent index/cache or Git ref selection. Code relationships use the existing approximate graph builder.
- Complete Streamable HTTP features, per-user authorization, and production networking are deferred. **Do not expose this endpoint on an untrusted network.**

## Standard MCP Streamable HTTP (stateless)

In addition to the legacy custom endpoints (`GET /mcp/tools`, `POST /mcp/tools/{name}/call`),
`POST /mcp` accepts JSON-RPC 2.0 requests for `initialize`, `ping`, `tools/list`, and
`tools/call`. Initialized notifications return HTTP 202. The server returns JSON (no SSE)
and uses no persistent MCP sessions or server-initiated notifications. Do not expect other
optional MCP capabilities, dynamic tools/list subscriptions, or production access controls.

```bash
curl -sS -X POST http://127.0.0.1:8091/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

See [MiniCPM-V 4.7 local evaluation](minicpm47-evaluation.md) for a fully local
model-grounded Q&A smoke test.

The source search adapter now slightly favors `src/main` results over test/eval
snippets and chooses an excerpt near query identifiers instead of always
returning the first lines of imports. This is a small heuristic, not an
advanced semantic re-ranker; retrieval evidence must still be reviewed.

The MiniCPM 4.7 multimodal model has been exercised against the updated
local llama.cpp (`llama.cpp-qwen36`) with image input and against the
Web-Agent-to-MCP code lookup path. See the exact [test commands](minicpm47-evaluation.md).

## P1: incremental symbol cache (opt-in)

By default the server keeps an in-memory snapshot (no symbol cache saved to disk). To persist
per-file parsed symbols across restarts, configure a dedicated file **outside** the repository:

```bash
mkdir -p /Volumes/ssd/tmp/codewiki-index
export CODEWIKI_INDEX_CACHE_FILE=/Volumes/ssd/tmp/codewiki-index/symbols.json
```

Cache entries are keyed by full **SHA-256 source content**; unchanged files reuse parsed
symbols, and renames/deletions/changes trigger only necessary updates. Corrupt or stale
cache files are rebuilt. The persisted cache includes extracted source-code fragments;
**treat it as sensitive**, keep it outside tracked directories, and do not commit or share.
On POSIX filesystems it is written with `0600` permissions and atomically replaced.
The parent directory must already exist and cannot be a symlink. The source scanner still
enforces the 800-file / 4 MiB repository cap, so this is a local prototype, not yet a
large-repository indexing service.

Run the tool to check cache behavior:

```bash
curl -s -X POST http://127.0.0.1:8091/mcp \
 -H 'Content-Type: application/json' \
 -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"codewiki_index_status","arguments":{}}}'
```

Sample M4/16GB local smoke, 233 files / 1,699 symbols (results vary with current source):
initial parse `0.607s`, repeated status `0.040s`; after restarting the server with
existing cache, changed files only were reparsed. The graph is lazily built once per
snapshot: tested cold ~2.4s and repeated impact queries ~0.07s on this particular
repository. These are local latency measurements, not standardized benchmarks.

Source search now prioritizes explicitly named file/class identifiers and includes
`startLine`/`endLine` for selected snippets. The MiniCPM-V 4.7 **1B** evaluation's
three simple keyword questions passed on one P1 run, but this tiny example is **not**
reliable proof of general code-answer accuracy.
