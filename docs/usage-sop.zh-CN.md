# CodeWiki 使用 SOP

本文档用于指导开发者在本地使用 AgentScope CodeWiki 对 Git 仓库生成代码知识库、执行 diff review，并输出基准报告。

## 1. 适用范围

当前 SOP 覆盖已实现的本地 CLI 工作流：

- 生成 Markdown Wiki：`overview.md`、`architecture.md`、`modules.md`、`dataflow.md`、`review.md`
- 对 Git diff 执行代码评审：`diff-review.md`
- 生成影响分析：`impact-report.json`
- 生成增量分析报告：`incremental-report.json`
- 运行 benchmark：`benchmark-report.md`

当前不覆盖 Web UI、REST API、Swagger 文档和完整 AgentScope 多智能体运行时；这些能力仍在路线图中。

## 2. 前置条件

确认本机具备以下环境：

- JDK 21
- Maven 3.9+
- Git
- 待分析项目必须是一个可访问的 Git 仓库

在 CodeWiki 仓库根目录执行：

```bash
mvn -pl codewiki-cli -am test
```

该命令会编译 CLI 及其依赖模块，并运行相关测试。只有该命令通过后，才进入实际分析流程。

## 3. 推荐目录约定

不要把分析产物写入待分析仓库的源码目录内。推荐使用独立输出目录：

```text
/tmp/codewiki-runs/
  my-repo-wiki/
  my-repo-review/
  my-repo-benchmark/
```

如果输出到当前 CodeWiki 仓库下，`wiki/`、`review/`、`review-v*/`、`benchmark/` 和 `real-repo-validation-report*.md` 已被 `.gitignore` 忽略。

## 4. 构造本地运行 classpath

CLI 当前没有发布成独立可执行包。推荐先让 Maven 生成外部依赖 classpath，再叠加本仓库各模块的 `target/classes`：

```bash
mvn -q -pl codewiki-cli -am dependency:build-classpath \
  -Dmdep.outputFile=/tmp/codewiki-deps.cp

export CODEWIKI_MODULE_CP="codewiki-core/target/classes:codewiki-parser/target/classes:codewiki-indexer/target/classes:codewiki-retriever/target/classes:codewiki-dify/target/classes:codewiki-agent/target/classes:codewiki-cli/target/classes"
export CODEWIKI_CP="${CODEWIKI_MODULE_CP}:$(cat /tmp/codewiki-deps.cp)"
```

后续命令均默认已经设置 `CODEWIKI_CP`。

## 5. 离线生成 Wiki

离线模式不调用 Dify，适合本地验收、CI smoke test 和没有外部模型凭证的环境。

```bash
java -cp "$CODEWIKI_CP" com.agentscope.codewiki.cli.CodewikiCli \
  analyze \
  --repo /path/to/target-repo \
  --output /tmp/codewiki-runs/my-repo-wiki \
  --mock
```

成功后检查输出目录：

```text
overview.md
architecture.md
modules.md
dataflow.md
review.md
codewiki-run.log
```

验收标准：

- 命令退出码为 `0`
- `codewiki-run.log` 没有未处理异常
- 至少生成 `overview.md`、`architecture.md`、`modules.md`
- 文档中的文件路径和模块名能对应到目标仓库

## 6. 使用 Dify 生成 Wiki

使用 Dify 前，先配置环境变量：

```bash
export DIFY_BASE_URL=https://api.dify.ai
export DIFY_API_KEY=app-xxxxxxxx
export DIFY_USER=codewiki-local
export CODEWIKI_DIFY_RESPONSE_MODE=streaming
export CODEWIKI_MAX_CONTEXT_TOKENS=32000
```

运行：

```bash
java -cp "$CODEWIKI_CP" com.agentscope.codewiki.cli.CodewikiCli \
  analyze \
  --repo /path/to/target-repo \
  --output /tmp/codewiki-runs/my-repo-wiki \
  --dify \
  --dify-response-mode streaming \
  --dify-timeout-seconds 60 \
  --session-id local-acceptance
```

也可以通过 CLI 参数覆盖环境变量：

```bash
java -cp "$CODEWIKI_CP" com.agentscope.codewiki.cli.CodewikiCli \
  analyze \
  --repo /path/to/target-repo \
  --output /tmp/codewiki-runs/my-repo-wiki \
  --dify \
  --dify-base-url https://api.dify.ai \
  --dify-api-key app-xxxxxxxx \
  --dify-user codewiki-local \
  --dify-response-mode streaming \
  --max-context-tokens 32000
```

注意事项：

- 不要同时使用 `--mock` 和 `--dify`。
- Dify Agent Chat App 通常需要 `--dify-response-mode streaming`；普通 Chat App 可继续使用默认 `blocking`。
- `--max-context-tokens` 和 `CODEWIKI_MAX_CONTEXT_TOKENS` 必须是正整数。
- Dify 不可用时，CLI 会记录失败原因；根据具体流程可能回退到本地 mock 分析。
- 不要把真实 `DIFY_API_KEY` 写入仓库文档、脚本或提交记录。

## 7. 控制 Wiki 生成范围

默认使用增量生成并生成全部页面。可按需调整：

```bash
# 强制全量生成
java -cp "$CODEWIKI_CP" com.agentscope.codewiki.cli.CodewikiCli \
  analyze --repo /path/to/target-repo --output /tmp/codewiki-runs/my-repo-wiki --mock --wiki-full

# 只生成 architecture 页面
java -cp "$CODEWIKI_CP" com.agentscope.codewiki.cli.CodewikiCli \
  analyze --repo /path/to/target-repo --output /tmp/codewiki-runs/my-repo-wiki --mock --wiki-page architecture

# 禁用 Mermaid 图
java -cp "$CODEWIKI_CP" com.agentscope.codewiki.cli.CodewikiCli \
  analyze --repo /path/to/target-repo --output /tmp/codewiki-runs/my-repo-wiki --mock --wiki-no-mermaid
```

`--wiki-page` 支持：

- `overview`
- `architecture`
- `modules`
- `dataflow`
- `review`

## 8. 执行 Diff Review

Diff review 基于目标仓库中的 Git diff。默认比较 `main..HEAD`：

```bash
java -cp "$CODEWIKI_CP" com.agentscope.codewiki.cli.CodewikiCli \
  analyze \
  --repo /path/to/target-repo \
  --output /tmp/codewiki-runs/my-repo-review \
  --review-diff \
  --base-ref main \
  --head-ref HEAD \
  --mock
```

只评审变更文件：

```bash
java -cp "$CODEWIKI_CP" com.agentscope.codewiki.cli.CodewikiCli \
  analyze \
  --repo /path/to/target-repo \
  --output /tmp/codewiki-runs/my-repo-review \
  --review-diff \
  --base-ref origin/main \
  --head-ref HEAD \
  --changed-only \
  --mock
```

成功后检查：

```text
diff-review.md
impact-report.json
incremental-report.json
```

验收标准：

- `diff-review.md` 包含 Summary、Changed Files、Impact Analysis、Potential Risks、Suggested Tests 等章节
- `impact-report.json` 包含 `riskLevel` 和 `affectedModules`
- 如果没有代码变更，报告应明确说明无可评审变更，而不是失败

## 9. 运行 Benchmark

Benchmark 用于记录真实仓库分析流程的耗时、规模和上下文统计：

```bash
java -cp "$CODEWIKI_CP" com.agentscope.codewiki.cli.CodewikiCli \
  benchmark \
  --repo /path/to/target-repo \
  --output /tmp/codewiki-runs/my-repo-benchmark
```

成功后检查：

```text
benchmark-report.md
```

重点查看：

- 文件数量和语言分布
- 解析、图构建、检索、影响分析、评审、Wiki 生成耗时
- 峰值内存
- context size、token usage、Dify calls
- top 10 largest files、top 10 most connected symbols、top 10 high risk modules

## 10. Dry Run

Dry run 用于验证扫描、解析和上下文构建，不写 Wiki 页面：

```bash
java -cp "$CODEWIKI_CP" com.agentscope.codewiki.cli.CodewikiCli \
  analyze \
  --repo /path/to/target-repo \
  --output /tmp/codewiki-runs/my-repo-dry-run \
  --dry-run \
  --mock
```

验收标准：

- 命令退出码为 `0`
- 输出目录包含运行日志
- 日志中能看到扫描、解析和上下文预算信息

## 11. 常见故障处理

| 现象 | 处理方式 |
|------|----------|
| `Missing required --repo argument` | 补充 `--repo /path/to/target-repo` |
| `Missing required --output argument` | 补充 `--output /path/to/output` |
| `Use either --mock or --dify, not both` | 二选一，不能同时传入 |
| `Unknown wiki page` | 确认 `--wiki-page` 只使用 `overview`、`architecture`、`modules`、`dataflow`、`review` |
| `ClassNotFoundException` | 先执行第 2 节和第 4 节，确认 `CODEWIKI_CP` 包含全部模块和外部依赖 |
| Dify 调用失败 | 检查 `DIFY_BASE_URL` / `DIFY_API_URL`、`DIFY_API_KEY`、网络连通性、`--dify-response-mode` 和 `--dify-timeout-seconds` |
| 输出内容过少 | 先确认目标仓库存在 Java、Python 或 TypeScript 源码，并检查 `codewiki-run.log` |
| diff review 没有结果 | 在目标仓库执行 `git diff baseRef headRef`，确认确实存在代码变更 |

## 12. 标准交付清单

向他人交付一次 CodeWiki 分析结果时，至少包含：

- 使用的 CodeWiki commit id
- 目标仓库路径或仓库 URL
- 目标仓库 commit id / branch / base-ref / head-ref
- 运行模式：`--mock` 或 `--dify`
- 输出目录压缩包或 Wiki Markdown 文件
- `codewiki-run.log`
- 如果执行 diff review，附带 `diff-review.md`、`impact-report.json`、`incremental-report.json`
- 如果执行 benchmark，附带 `benchmark-report.md`

建议在交付说明中记录完整命令，但必须移除真实 API Key。
