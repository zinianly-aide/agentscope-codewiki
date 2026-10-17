# MiniCPM-V 4.7 local CodeWiki smoke test

## Scope

This is a reproducible *offline-to-third-parties* repository-Q&A test: local CodeWiki
source indexing / MCP tool call -> bounded evidence snippets -> local Ollama MiniCPM-V 4.7 1B.
It does not establish that this 1B model reliably selects or calls tools autonomously.
The test script intentionally invokes retrieval before prompting the model, and marks its
simple keyword and citation checks as heuristics, **not an accuracy benchmark**.

MiniCPM-V 4.7 has two variants: `1b` (Ollama 1.8 GB Q4_K_M) and `35b-a3b`
(Ollama 22 GB Q4_K_M). For a 16 GB M4, start with `1b`, never `latest`
(which points to 35B-A3B).

## Local setup

Use a local model directory on an SSD rather than filling a small system drive:

```bash
export OLLAMA_MODELS=/Volumes/ssd/models/ollama
ollama serve
# in a separate shell:
ollama pull openbmb/minicpm-v4.7:1b
```

Build the CodeWiki MCP server and start it for a single trusted repository:

```bash
mvn -pl codewiki-mcp-server -am package
export CODEWIKI_ANALYSIS_ENABLED=true
export CODEWIKI_REPOSITORY_ROOT=/absolute/path/to/trusted/repository
export CODEWIKI_TREE_SITTER_BRIDGE=/absolute/path/to/agentscope-codewiki/codewiki-parser/src/main/resources/tree-sitter-bridge/extract-symbols.mjs
java -jar codewiki-mcp-server/target/codewiki-mcp-server-0.0.1-SNAPSHOT.jar
```

The Node bridge directory needs `node_modules`. The app is loopback-only by default.
Verify the JSON-RPC MCP endpoint and run the model test:

```bash
curl -s -X POST http://127.0.0.1:8091/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'

python3 scripts/minicpm47_codewiki_eval.py --max-cases 3 \
  --output /tmp/codewiki-minicpm47-results.json
```

The JSON report includes exact questions, answers, retrieved source paths, keyword/citation
heuristics, wall times and model token usage. Manually inspect reported answers for
hallucination, incorrect citations, lack of code understanding or cut-off due to token limits.

This uses only local endpoints; **do not send private code to an external inference provider**
without explicit authorization. Real model tool-calling capability must be tested separately
through `codewiki-web`'s `llm-react` mode and is not guaranteed by the `1b` checkpoint.

## Observed on Mac mini M4 / 16 GB, 2026-10-17

An early check used the **official 1B Q4_K_M language GGUF** imported into
Ollama as `minicpm47-text` while the separate vision projector downloaded.
This is **text-only inference**, not yet an image/video-input evaluation.

- The HTTP JSON-RPC CodeWiki tool endpoint listed all five tools; all three
  code-analysis tools were discoverable from the Web Agent.
- `python3 scripts/minicpm47_agent_smoke.py --web-url http://127.0.0.1:18084`
  passed: the local model chose `codewiki_search_symbol`, the MCP tool audit showed
  one successful call, and the final answer included the actual `ImpactAnalyzer.java`
  path. The reproduced run took **3.454 s** (another run took 4.35 s, of which the
  MCP symbol tool took 467 ms).
- For three less guided repository Q&A prompts, the 1B model was not sufficiently
  reliable. With thinking enabled and a 480-token output limit, one answer was
  empty (budget spent on reasoning); with thinking disabled, it answered a
  `CodeGraphBuilder` question acceptably, confused `ImpactReport` with
  `ImpactAnalyzer` on another question, and gave an incomplete account of
  `requestId` conflicts on the third. These are **small smoke samples**, not a
  representative accuracy estimate.
- A natural-language multi-tool turn failed with a model-side HTTP 400 / chat
  template parser error after earlier successful tool calls. This should be
  reproduced against the full official Ollama package and a compatible newer
  runtime before using the 1B model for autonomous multi-step agent work.

For a lower-latency baseline with Ollama's native `think:false` setting:

```bash
python3 scripts/minicpm47_codewiki_eval.py \
  --model minicpm47-text --ollama-no-think --max-tokens 200
```

Tool selection can succeed, but **do not equate a passing path-lookup smoke test
with reliable open-ended code analysis**. Keep retrieval evidence visible and
prefer stronger models for multi-file impact reasoning or production reviews.

## Full 1B multimodal runtime verification (same day)

The complete official Ollama model `openbmb/minicpm-v4.7:1b` was eventually downloaded
(about **1.8 GB** language GGUF + image projector). The Mac's packaged Ollama
**0.30.7 could not start the projector**, returning HTTP 500 with the precise
runtime error `load_hparams: unknown projector type: minicpmv4_7`. Likewise the
installed Homebrew llama.cpp build 7880 does not recognize the new `qwen35`
model architecture; these failures are **runtime compatibility**, not proof
that 16 GB is too little memory.

Built the local newer llama.cpp source at `/Volumes/ssd/dev/llama.cpp-qwen36`
into `/Volumes/ssd/tmp/codewiki-minicpm47-llama-build`, without modifying that
source checkout. Its executable supports the language GGUF and the MiniCPM-V4.7
projector from `/Volumes/ssd/models/ollama/blobs/`:

```bash
/Volumes/ssd/tmp/codewiki-minicpm47-llama-build/bin/llama-server \
  -m /Volumes/ssd/models/ollama/blobs/sha256-588c6c94c82f756b86dba35c42ed9ba9ec187d65824eb52f039c347df563e63d \
  --mmproj /Volumes/ssd/models/ollama/blobs/sha256-fb9cc047a3cdad5317fd8433a4aeda2d22f00f02ca4bd93b34ad84165b25a900 \
  -ngl 99 -c 4096 --host 127.0.0.1 --port 18086 --alias minicpm-v47-full --no-webui
```

Using this updated runtime, the full multimodal model **successfully answered
`Red`** for a newly generated 64x64 red PNG submitted as an OpenAI-compatible
image message, on the **M4 / 16GB**, in **0.54s**. Reproduce with:

```bash
python3 scripts/minicpm47_vision_smoke.py
```

Re-running CodeWiki retrieval Q&A through the *complete* model yielded
**2/3 keyword checks PASS** with source citations in all three answers,
but the answer to the impact-analysis-class question named the wrong
class (`DiffReviewAgent` instead of `ImpactAnalyzer`). Under the short
three-question set this remains a weak grounding result, not a general metric.

The **Web Agent -> MCP -> actual code symbol -> MiniCPM-V4.7 full model**
guided smoke test passed, with verified MCP tool metrics and the real
`ImpactAnalyzer.java` path; full-model **2.507 s**. Reproduce using the
CodeWiki Web app configured `AGENT_MODEL=minicpm-v47-full` and
`AGENT_BASE_URL=http://127.0.0.1:18086/v1`:

```bash
python3 scripts/minicpm47_agent_smoke.py --web-url http://127.0.0.1:18085
python3 scripts/minicpm47_codewiki_eval.py \
  --model-url http://127.0.0.1:18086/v1/chat/completions \
  --model minicpm-v47-full --max-tokens 420
```

The image smoke test proves the image projector can load and identify a trivial
color; it does **not** validate document OCR, complex charts, video understanding,
or performance on production code repositories. Keep access local; no private
source is submitted to an external model endpoint.
