# Agent AI Ops

基于 Java 的服务自动化修复 Agent 演示项目。

## 项目模块

- `agent-platform`：自动修复 Agent 平台，负责读取异常、规划修复、修改代码、运行测试、创建 PR、发送飞书通知和生成修复记录。
- `target-service`：被监控和被修复的 Spring Boot 示例服务，内置一个参数校验类 bug，用于比赛演示。

## 核心流程

```text
服务报错
-> 读取 Traceback
-> 诊断根因 (AI Agent)
-> 生成修复计划 (AI Agent)
-> 准备源码上下文
-> 生成补丁 (AI Agent)
-> 应用补丁 (Java，写文件唯一通道)
-> 运行测试
   ├── 测试通过 -> 继续
   └── 测试失败 + 还有 attempt -> 回滚 -> 把 stderr 喂回 Patch Agent 重写 (Reflexion)
-> 审查 diff
-> 创建 repair 分支并 commit
-> 通过 GitHub REST API 创建 PR
-> 发送飞书 v2 卡片（含耗时/token、查看 PR 按钮）
-> 反思沉淀
-> 生成修复记录
```

修复链路是一条确定性的 Java DAG：4 个 LangChain4j AI 子 Agent 只负责语言任务（诊断、规划、补丁/重写补丁、反思），其余节点全部由 Java 顺序调用，不依赖 LLM 决策。第一版采用手动按钮/API 触发，LLM、GitHub PR 和飞书通知默认关闭，录制比赛演示时通过环境变量开启真实 PR 和真实飞书卡片即可。

## 当前 Agent 成熟度

当前代码已经打通后端闭环，并收敛为「确定性 Java DAG + 4 个 LangChain4j AI 子 Agent」单一路径：

- 已实现：异常读取、诊断、计划、代码读取、补丁生成、补丁回写、测试执行、reflexion 测试失败重写、diff review、Git commit/push、GitHub REST API 创建 PR、飞书 v2 卡片、修复记录和反思沉淀。
- 编排方式：`AgenticRepairRunner` 用 Java 顺序调用各 operator，不再使用 `SupervisorAgent`；AI 子 Agent 通过 `AgenticServices.agentBuilder` 直接构造和调用，其它节点（证据、补丁应用、测试、审查、commit、PR、飞书、记录）都是 non-AI Java 组件。
- AI 角色：`AgenticDiagnosisAgent`、`AgenticPlanAgent`、`AgenticPatchAgent`、`AgenticReflectionAgent` 都返回强类型对象（`DiagnosisResult` / `RepairPlan` / `PatchProposal` / `RepairReflection`），字段使用 LangChain4j `@Description` 描述。`AgenticPatchAgent.regeneratePatchFromTestFailure` 在 reflexion 重试中根据测试 stderr 重写补丁；结构化输出会经过 Java 校验，格式/业务校验失败会重试一次。
- Reflexion：`repair.workflow.max-patch-attempts` 控制重写次数（默认 2）。一次补丁应用前会先快照原文件内容，测试失败后回滚到该快照再重新生成补丁，避免错叠多份补丁。
- GitHub 集成：默认走 GitHub REST API（`repair.github.client=rest`），不依赖 `gh CLI`。Owner/Repo 优先读 `REPAIR_GITHUB_OWNER` / `REPAIR_GITHUB_REPO`，未配置时自动从 `git remote get-url origin` 解析 HTTPS/SSH URL。
- 飞书 v2 卡片：成功修复和失败修复使用不同标题/文案，卡片内容包括根因摘要、Review 结论、PR 链接、总耗时、token 消耗（provider 返回 usage 时展示真实值，否则明确显示未返回/未采集，不再显示 0），并提供「查看 PR」按钮和会话 footer；如果配置 `FEISHU_SIGNING_SECRET`，请求体会带上 `timestamp` + `sign` 校验。
- 已验证：`real-e2e-003` 在已提交故障的 `demo/fault/quantity-division-by-zero` base 分支上跑通完整比赛链路：读取独立 traceback、定位 `OrderService.calculateUnitPrice`、生成并应用补丁、跑通 `target-service` 5 个测试、通过 review、push `repair/real-e2e-003`、创建 GitHub PR [#1](https://github.com/deibudei/agent-aiOps/pull/1)、发送飞书「已修复，请 Review」卡片，并写入 `repair-records/real-e2e-003.json` / `.md`，最终 `outcome=FIXED`。
- 当前限制：修复运行必须配置 `REPAIR_LLM_ENABLED=true` 和对应 provider API key；模型不可用或结构化输出连续两次非法会发布 `ERROR`，并写入最小错误记录，不再走确定性 fallback。写文件仍只能通过 `PatchTools + ToolPolicy`，不能让模型直接落盘。

## 当前实现状态与下一步

当前纵向 MVP 和真实 PR + 飞书主链路已经跑通，重点不再是“是否能调用模型”，而是继续扩大故障类型、补齐编排测试和沉淀修复记录。

已完成：

- 结构化证据：`EvidenceBundle` 聚合 traceback、baseline tests、候选文件和源码片段。
- Agentic 诊断：LangChain4j Agentic 子 Agent 直接返回强类型 `DiagnosisResult`，字段使用 `@Description` 描述。
- Agentic 规划：LangChain4j Agentic 子 Agent 直接返回强类型 `RepairPlan`，字段使用 `@Description` 描述，并在 prompt 中提供两个 few-shot 示例。
- Agentic 补丁：LangChain4j Agentic 子 Agent 直接返回强类型 `PatchProposal`，字段使用 `@Description` 描述，并由 Java gate 做业务校验。
- 安全执行：`PatchTools + ToolPolicy` 是唯一写文件通道。
- 审查门禁：路径越界、空 diff、测试失败、review 不通过都会阻断后续 commit/PR/飞书。
- Agentic 编排：`AgenticRepairRunner` 负责装配并顺序调用 Java DAG，AI Agent 拆在 `repair/agentic/agents`，non-AI 执行节点拆在 `repair/agentic/operators`。
- 修复耗时与 token 观测：`RepairTiming` 记录总耗时、每个 Java DAG 节点耗时、模型名称和 token 消耗，并写入 SSE、JSON 记录和 Markdown 记录。部分 OpenAI-compatible provider 不返回 token usage 时，记录中的 token 字段保持为空，飞书卡片明确显示 provider 未返回 token usage。
- 演示故障：已提供 `quantity-division-by-zero`、`wrong-quote-route`、`wrong-error-status` 三类故障注入 API。
- 修复结果：SSE completed 事件和修复记录写入 `outcome=FIXED|FAILED|ERROR` 与 `outcomeReason`。patch/test/review/PR 的受控失败是 `COMPLETED + FAILED`；系统异常是 `ERROR`，并写入最小错误记录。

### 真实 PR 演示分支模型

`main` 永远保持正常修复态。真实 PR 演示不要在 `main` 上临时注入故障后期待 diff；应使用一条已经提交故障的 demo base 分支：

```text
main                                  正常修复态
demo/fault/quantity-division-by-zero  当前 main + 已提交的除零故障
repair/{sessionId}                    Agent 自动创建的修复分支
```

录制真实 PR 演示时设置：

```powershell
$env:REPAIR_BASE_BRANCH="demo/fault/quantity-division-by-zero"
```

这样 PR 会从 `repair/{sessionId}` 指向 `demo/fault/quantity-division-by-zero`，diff 只展示 Agent 把故障代码修回正确代码。旧的 `repair-demo-target` 不再作为比赛主演示 base，因为它可能落后当前 `main` 太多。

后续项目更新仍然在 `main` 或面向 `main` 的普通 feature 分支上做，不要在 `demo/fault/...` 或 `repair/{sessionId}` 分支上改文档/平台代码。`main` 更新后，需要从最新 `main` 刷新 demo 故障分支，并让这条分支只保留一个“故障注入”提交；`repair/{sessionId}` 分支只是每次演示的自动修复产物，用完即可关闭或删除。

当 `main` 更新后，需要重建 demo 故障 base 时：

```powershell
git checkout main
git pull
git checkout -B demo/fault/quantity-division-by-zero main
# 只注入 quantity-division-by-zero 故障，然后提交这个故障
git push --force-with-lease origin demo/fault/quantity-division-by-zero
```

下一步：

1. 给 `wrong-quote-route` 和 `wrong-error-status` 跑完整 Agentic 修复验证。
2. 增加 Agentic 编排层面的测试，覆盖 JSON 解析失败、空补丁、测试失败、路径越界等场景。
3. 优化修复记录检索和经验沉淀，后续再决定是否接入向量数据库/RAG。
4. 比赛主链路录制稳定后，再做前端工作台和更复杂触发源。

### 修复耗时与 token 观测

当前已经在保持完整 Agentic 修复链路的前提下，量化“修复一个 bug 到底花了多久、每个模型步骤花了多少 token”。诊断、计划、补丁、测试、审查、commit、PR、飞书、反思和记录等节点仍然完整执行，观测数据只作为旁路记录。

已实现：

- `RepairTiming` 和 `RepairStepTiming` 记录总耗时、每个步骤开始/结束时间、耗时、成功状态、摘要、模型名称和 token 数。
- `RepairModelUsage` 汇总每个模型步骤的角色、配置模型、响应模型、调用次数、input tokens、output tokens 和 total tokens。
- 内部 `RepairTimingCollector` 用 `Instant.now()` 记录展示时间，用 `System.nanoTime()` 计算耗时，避免系统时间跳变影响统计。
- `AgenticRepairRunner` 对 Java DAG 节点主动计时，不再依赖 LangChain4j listener 是否触发。
- `ObservedChatModel` 包装每个分角色 `ChatModel`，在模型边界直接记录 `ChatResponse.modelName()` 和 `tokenUsage()`，避免 Agentic 强类型输出路径丢失响应元数据。
- `RepairAgenticListener` 只发布 Agentic 生命周期和工具调用事件。
- Agentic 链路对 evidence、diagnosis、plan、patch、test、review、commit、PR、notify、reflect、record 等节点逐个计时。
- SSE 完成事件的 `details` 增加 `durationMillis`、`stepName` 和 `modelUsage`。
- `repair-records/{sessionId}.json` 增加 `timing.modelUsage` 字段，`repair-records/{sessionId}.md` 增加 `Timing` 和 `Model Usage` 表格，用于后续报告和性能分析。若 provider 没返回 token usage，usage 行仍可记录模型调用次数，但 token 字段为空；若模型 wrapper 没采到模型 usage，飞书卡片显示“未采集到模型 usage”。

验收方式：

- `mvn -pl agent-platform test`
- 在 `demo/fault/quantity-division-by-zero` 上跑一次真实 E2E，确认 GitHub PR、飞书卡片、SSE、JSON 记录和 Markdown 记录里都能看到 `outcome=FIXED`。若模型 provider 返回 token usage，再确认 token 消耗被记录。

## LangChain4j 集成说明

当前是「确定性 Java DAG + LangChain4j AI 子 Agent」单一路径：

```text
Java DAG (AgenticRepairRunner)
  ├── EvidenceOperator
  ├── AgenticDiagnosisAgent  (LangChain4j @Agent，返回 DiagnosisResult)
  ├── AgenticPlanAgent       (LangChain4j @Agent，返回 RepairPlan)
  ├── PlanParserOperator
  ├── SourceContextOperator
  ├── for attempt in 1..max-patch-attempts:
  │     ├── AgenticPatchAgent.generate / regenerate (LangChain4j @Agent)
  │     ├── PatchParserOperator
  │     ├── PatchApplyOperator (含快照)
  │     ├── TestOperator
  │     └── 失败 -> rollback -> 回到 AgenticPatchAgent 重写
  ├── ReviewOperator
  ├── CommitOperator
  ├── PullRequestOperator    (GitHub REST API)
  ├── NotifyOperator         (飞书 v2 卡片)
  ├── AgenticReflectionAgent (LangChain4j @Agent，返回 RepairReflection)
  ├── ReflectOperator
  └── RecordOperator
```

关键类：

- `RepairChatModelProvider`：按 `REPAIR_LLM_PROVIDER` 延迟创建 OpenAI 或 DashScope `ChatModel`，并支持 diagnosis/plan/patch/reflection 分角色模型覆盖。
- `AgenticRepairRunner`：用 Java 顺序调用所有 operator，AI 子 Agent 通过 `AgenticServices.agentBuilder(...)` 直接构造，无 SupervisorAgent。
- `AgenticDiagnosisAgent`、`AgenticPlanAgent`、`AgenticPatchAgent`、`AgenticReflectionAgent`：直接返回强类型对象，字段使用 LangChain4j `@Description` 描述；Plan/Patch prompt 内置 few-shot 示例；`AgenticPatchAgent.regeneratePatchFromTestFailure` 接收测试 stderr 用于 reflexion 重写。
- `PatchApplyOperator`：在写文件前为每个目标文件生成 `PatchSnapshot`，便于 reflexion 失败后回滚。
- `GitHubRestPullRequestProvider` + `GitRepoLocator`：默认走 GitHub REST API 创建 PR，自动从 `git remote get-url origin` 解析 owner/repo。
- `FeishuTools`：构造飞书 v2 卡片 schema（header / 主文 / 耗时 token block / 查看 PR 按钮 / footer），支持可选签名；成功和失败 outcome 使用不同卡片文案。
- `PatchTools`：唯一允许写入文件的工具，模型不能直接写文件。应用补丁前会预检所有 operation，`oldText` 必须唯一命中，全部通过后才写文件。

启用 OpenAI LLM：

```powershell
$env:REPAIR_LLM_ENABLED="true"
$env:REPAIR_LLM_PROVIDER="openai"
$env:OPENAI_API_KEY="你的 OpenAI API Key"
$env:OPENAI_BASE_URL="https://api.openai.com/v1"
$env:REPAIR_LLM_TIMEOUT_SECONDS="90"
$env:REPAIR_LLM_MAX_RETRIES="1"
$env:REPAIR_LLM_MAX_TOKENS="4096"
$env:REPAIR_LLM_REFLECTION_MODEL=""
```

模型名和分角色模型覆盖建议放在 `application-local.yml`，不要放在可上传的 `application.yml`。如果使用 OpenAI-compatible 模型（例如 DeepSeek、Qwen 等）时出现 `request timed out`，优先调大 `REPAIR_LLM_TIMEOUT_SECONDS`，并保持 `REPAIR_LLM_MAX_RETRIES` 较小，避免一次修复被多轮重试拖得太久。DAG 路径会对 traceback、源码片段和 SSE 工具事件做截断，防止把完整堆栈和文件内容反复送进模型。复杂故障建议把 `REPAIR_LLM_MAX_TOKENS` 提高到 `4096` 或以上。四个模型角色里，`AgenticPatchAgent` 既要生成补丁也要在 reflexion 中根据 stderr 重写补丁，对精确代码修改和指令遵循能力要求最高，建议优先用更强模型；`AgenticDiagnosisAgent`、`AgenticPlanAgent` 和 `AgenticReflectionAgent` 通常可以先用默认模型，多因子故障再单独升配。

如果 `REPAIR_LLM_ENABLED` 未启用或缺少 API key，`POST /api/repair/run` 会接受请求但在 SSE 中发布 `error` 事件，不会执行旧修复链路。

暂缓内容：

- 前端工作台。
- RAG/Milvus 经验库。
- Sentry/GitHub Webhook 自动触发。
- 多语言、多项目泛化。

这些能力作为后续扩展，不影响当前已跑通的比赛主链路。

## 文档维护约定

为了重新打开终端或新开 Codex 会话时能快速恢复上下文，后续凡是发生以下变化，都要同步更新 `README.zh-CN.md`、`README.md` 和 `AGENTS.md`：

- 模块职责、架构边界或安全白名单变化。
- 启动命令、测试命令、环境变量变化。
- 演示流程、比赛口径或当前 Agent 能力状态变化。
- GitHub、飞书、Sentry、LLM、MCP 或其他外部集成方式变化。

## 启动服务

所有命令都建议在项目根目录运行：

```powershell
cd D:\java_web_project\agent-aiOps
```

需要开两个终端窗口。

终端 1：启动被修复服务：

```powershell
mvn -pl target-service spring-boot:run
```

终端 2：启动 Agent 平台：

```powershell
mvn -pl agent-platform spring-boot:run
```

默认端口：

- `target-service`：`http://localhost:9910`
- `agent-platform`：`http://localhost:9901`

## 触发演示 Bug

这个命令在 **新的 PowerShell 终端** 里运行即可。

运行前需要确保：

- 终端 1 里的 `target-service` 已经启动成功。
- 能访问 `http://localhost:9910`。

命令本身是 HTTP 请求，所以理论上在哪个目录运行都可以；为了统一，建议仍然先进入项目根目录：

```powershell
cd D:\java_web_project\agent-aiOps
```

调用下面的接口用于复现 `quantity=0` 参数校验缺失问题。注意：当前主线代码已经加入校验，因此会返回 400；需要先通过后面的故障注入 API 切到故障态，或手动恢复故障代码，才会产生 `/ by zero` 异常：

```powershell
Invoke-WebRequest "http://localhost:9910/api/orders/quote?totalCents=100&quantity=0"
```

目标服务保留普通应用日志，同时把每次未预期 500 异常写成单独 traceback 文件，避免所有报错堆在一个文件里：

```text
target-service/logs/target-service.log
target-service/logs/tracebacks/traceback-{timestamp}-{traceId}.log
```

`agent-platform` 默认读取整个 `target-service/logs` 目录，并从其中选择最新的异常 traceback 作为修复证据。

## 触发自动修复

这个命令也在 **新的 PowerShell 终端** 里运行。

运行前需要确保：

- 终端 1：`target-service` 正在运行。
- 终端 2：`agent-platform` 正在运行。
- 已经调用过上面的 Bug 触发接口，或 `target-service/logs` 下已经有异常 traceback 文件。

```powershell
$body = @{ sessionId = "demo-001" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/repair/run" -ContentType "application/json" -Body $body
```

查看 SSE 流式事件：

```text
GET http://localhost:9901/api/repair/stream/demo-001
```

事件阶段包括：

```text
detecting
planning
executing
patching
testing
reviewing
committing
pr_created
notified
reflecting
completed
error
```

## 演示故障注入

`agent-platform` 提供本地演示故障 API，用于把当前已修复的 `target-service` 自动切到故障态。该能力只写入 `target-service/src/main` 下固定演示文件，方便后续选择不同故障类型再让 Agent 修复。

列出可用故障：

```powershell
Invoke-RestMethod -Uri "http://localhost:9901/api/demo/faults"
```

当前支持：

- `quantity-division-by-zero`：移除 `OrderService` 的 quantity 校验，`quantity=0` 会触发 `/ by zero`。
- `wrong-quote-route`：把 `/api/orders/quote` 改成错误路由，Controller 测试会 404。
- `wrong-error-status`：把参数校验异常错误映射成 500，Controller 测试会失败。

注入一个故障：

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/demo/faults/quantity-division-by-zero/inject"
```

验证故障：

```powershell
mvn -pl target-service test
```

故障注入和恢复只改源码，不会热更新正在运行的 `target-service`。如果要通过 HTTP 触发运行时异常，需要重启 `target-service` 后再请求：

```powershell
Invoke-WebRequest "http://localhost:9910/api/orders/quote?totalCents=100&quantity=0"
```

恢复修复态：

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/demo/faults/reset"
```

## 环境变量

```text
REPAIR_LLM_ENABLED=false
REPAIR_LLM_PROVIDER=openai
REPAIR_LLM_TEMPERATURE=0.1
REPAIR_LLM_MAX_TOKENS=4096
REPAIR_LLM_REFLECTION_MODEL=
REPAIR_MAX_PATCH_ATTEMPTS=2

REPAIR_TARGET_LOG=target-service/logs
TARGET_SERVICE_LOG_FILE=logs/target-service.log
TARGET_SERVICE_TRACEBACK_LOG_DIR=logs/tracebacks

OPENAI_API_KEY=
OPENAI_BASE_URL=https://api.openai.com/v1

DASHSCOPE_API_KEY=
DASHSCOPE_BASE_URL=

REPAIR_GIT_ENABLED=false
REPAIR_GIT_REMOTE=origin
REPAIR_BASE_BRANCH=demo/fault/quantity-division-by-zero

REPAIR_GITHUB_ENABLED=false
REPAIR_GITHUB_CLIENT=rest
REPAIR_GITHUB_OWNER=
REPAIR_GITHUB_REPO=
REPAIR_GITHUB_API_BASE_URL=https://api.github.com
GITHUB_TOKEN=

FEISHU_ENABLED=false
FEISHU_WEBHOOK_URL=
FEISHU_SIGNING_SECRET=
```

## 本地配置文件

上传到 GitHub 的配置使用：

```text
agent-platform/src/main/resources/application.yml
```

本地真实运行使用：

```text
agent-platform/src/main/resources/application-local.yml
```

`application-local.yml` 已加入 `.gitignore`，可以在里面填写真实 key 和本地模型选择，不要上传。`application.yml` 默认 include `local` profile，并通过 `optional:classpath:application-local.yml` 自动导入它，因此正常启动命令就会读取本地配置。当前可上传的 `application.yml` 不再选择 OpenAI/DashScope 默认模型，也不再写 diagnosis/plan/patch/reflection 分角色模型覆盖：

```powershell
cd D:\java_web_project\agent-aiOps
mvn -pl agent-platform spring-boot:run
```

如果使用 OpenAI-compatible 本地或演示 provider，`application-local.yml` 中保持：

```yaml
openai:
  api-key: "你的 key"
  model: deepseek-v4-flash
  base-url: "你的 OpenAI-compatible /v1 endpoint"

repair:
  llm:
    enabled: true
    provider: openai
    patch-model: deepseek-v4-flash
    reflection-model: deepseek-v4-flash
```

隐私注意：

- 不要把真实 `OPENAI_API_KEY`、`DASHSCOPE_API_KEY`、`FEISHU_WEBHOOK_URL` 写入 README 或提交到 Git。
- 建议只在 PowerShell 环境变量里配置 key。
- `.gitignore` 已忽略 `.env`、`.env.*`、`*.secret`、`local-secrets/`、`local-reports/`。
- `agent-platform/src/main/resources/application.yml` 当前只保留上传安全的通用默认值，不应写入真实 key 或本地模型选择。

说明：

- `REPAIR_GIT_ENABLED=false` 时，不会创建分支、commit 或 push。
- `REPAIR_GITHUB_ENABLED=false` 时，不会调用 GitHub API 创建 PR。
- `FEISHU_ENABLED=false` 时，不会发送飞书卡片。
- 默认 base 分支是 `demo/fault/quantity-division-by-zero`：这条分支应从当前 `main` 创建，只提交一个除零故障。每次真实 PR 演示都会从这条分支切出 `repair/{sessionId}` 子分支并向其发起 PR，避免直接对抗 `main`，也避免修回 `main` 后没有 diff。
- 开启真实 PR 时，默认 `REPAIR_GITHUB_CLIENT=rest`，不需要安装 `gh CLI`；如需走 CLI，可设 `REPAIR_GITHUB_CLIENT=cli`。`GITHUB_TOKEN` 如果使用 fine-grained personal access token，Repository access 必须包含 `deibudei/agent-aiOps`，Repository permissions 至少需要 `Contents: Read and write` 和 `Pull requests: Read and write`。只有 PR/code 读权限会在创建 PR 时返回 GitHub HTTP 403。
- `REPAIR_GITHUB_OWNER` / `REPAIR_GITHUB_REPO` 留空时，会从 `git remote get-url origin` 自动解析。
- 飞书 v2 卡片可选签名校验：在群机器人开启签名校验后填 `FEISHU_SIGNING_SECRET` 即可。
- `REPAIR_MAX_PATCH_ATTEMPTS=2` 表示：第一版补丁测试失败时，最多再让 Patch Agent 根据 stderr 重写 1 次；提高数值会更稳但更慢更费 token。

## 测试命令

Agent 平台测试：

```powershell
mvn -pl agent-platform test
```

被修复服务测试：

```powershell
mvn -pl target-service test
```

注意：当前主线 `target-service` 已处于修复后状态，测试应通过。演示故障态优先使用 `agent-platform` 的故障注入 API。

如果只想确认 `target-service` 能编译打包，可以跳过测试：

```powershell
mvn -pl target-service -DskipTests package
```

## 修复记录

每次自动修复都会生成记录：

```text
repair-records/{sessionId}.json
repair-records/{sessionId}.md
```

如果记录出现在下面这个目录，说明 `agent-platform` 仍在运行旧进程，或者工作区根目录没有正确识别：

```text
agent-platform/repair-records/
```

处理方式：在终端 2 按 `Ctrl+C` 停止 `agent-platform`，然后从项目根目录重新启动：

```powershell
cd D:\java_web_project\agent-aiOps
mvn -pl agent-platform spring-boot:run
```

记录内容包括：

- 异常摘要
- 证据摘要
- 修复计划
- 工具执行步骤
- LLM 补丁 proposal 和落盘结果
- diff 摘要
- 测试结果
- Review 结论
- PR 结果
- 飞书通知结果
- Agent 反思沉淀

## 新终端快速恢复上下文

如果以后重新打开终端或重新开始对话，先看这两个文件：

```text
README.zh-CN.md
AGENTS.md
```

最短操作顺序：

```powershell
cd D:\java_web_project\agent-aiOps

# 终端 1
mvn -pl target-service spring-boot:run

# 终端 2
mvn -pl agent-platform spring-boot:run

# 终端 3：注入故障
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/demo/faults/quantity-division-by-zero/inject"

# 重启终端 1 的 target-service，让源码故障生效

# 终端 3：触发 bug
Invoke-WebRequest "http://localhost:9910/api/orders/quote?totalCents=100&quantity=0"

# 终端 3：触发修复
$body = @{ sessionId = "demo-001" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/repair/run" -ContentType "application/json" -Body $body
```

真实 PR + 飞书演示需要开启这些环境变量：

```powershell
$env:REPAIR_GIT_ENABLED="true"
$env:REPAIR_GITHUB_ENABLED="true"
$env:REPAIR_GITHUB_CLIENT="rest"
$env:GITHUB_TOKEN="你的 GitHub fine-grained token"
$env:REPAIR_BASE_BRANCH="demo/fault/quantity-division-by-zero"
$env:FEISHU_ENABLED="true"
$env:FEISHU_WEBHOOK_URL="你的飞书 webhook"
$env:FEISHU_SIGNING_SECRET="飞书机器人签名密钥（如启用）"
```

## 安全边界

自动修复工具采用强白名单：

- 只读取 `target-service/src` 和 `target-service/logs`
- 只允许修改 `target-service/src/main` 和 `target-service/src/test`
- 不允许修改 `agent-platform`
- 不允许修改根配置、密钥配置或任意脚本
- 测试命令固定为目标服务测试

## 扩展点

当前代码已预留：

- `RepairTrigger`：后续可扩展日志轮询、GitHub Webhook、CI 失败触发。
- `TestRunner`：后续可扩展 Gradle、pytest、npm test。
- `PullRequestProvider`：默认实现 `GitHubRestPullRequestProvider`，后续可加 GitLab/Gitee 等 provider。
- `ReviewPolicy`：后续可拆分安全审查、回归风险审查、测试覆盖审查。
- `RepairRecord`：后续可接入 RAG/Milvus，沉淀历史修复经验。
