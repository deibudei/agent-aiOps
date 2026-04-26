# Agent AI Ops

基于 Java 的服务自动化修复 Agent 演示项目。

## 项目模块

- `agent-platform`：自动修复 Agent 平台，负责读取异常、规划修复、修改代码、运行测试、创建 PR、发送飞书通知和生成修复记录。
- `target-service`：被监控和被修复的 Spring Boot 示例服务，内置一个参数校验类 bug，用于比赛演示。

## 核心流程

```text
服务报错
-> 读取 Traceback
-> 生成修复计划
-> 读取代码
-> 修改代码
-> 运行测试
-> 审查 diff
-> 创建 GitHub PR
-> 发送飞书通知
-> 反思沉淀
-> 生成修复记录
```

第一版采用手动按钮/API 触发，GitHub PR 和飞书通知默认关闭。录制比赛演示时，可以通过环境变量开启真实 PR 和真实飞书卡片。

## 当前 Agent 成熟度

当前代码已经打通后端闭环，并新增可选的 LangChain4j Agentic Supervisor 路径：

- 已实现：异常读取、计划阶段、代码读取、代码修改、测试执行、diff review、GitHub PR 工具、飞书通知工具、修复记录和反思沉淀。
- 当前进展：已接入 `langchain4j-agentic`，可通过 `REPAIR_AGENTIC_ENABLED=true` 启用 Supervisor 自主编排；OpenAI-compatible/Qwen 是本地默认模型形态，DashScope provider 保留。
- 当前模式：AI 子 Agent 负责诊断、计划和补丁 proposal；non-AI Agent 负责收集证据、应用补丁、跑测试、审查、commit、PR、飞书、反思和记录。
- 当前限制：Agentic 路径默认关闭，并保留旧的稳定 fallback；写文件仍只能通过 `PatchTools + ToolPolicy`，不能让模型直接落盘。

## 真实 Agent 修复实施计划

下一步先做一个可验收的纵向 MVP，不急着扩前端和复杂触发源。目标是把当前硬编码修复替换成真实的 LLM + 工具驱动修复闭环。

MVP 验收标准：

- `RepairPlannerAgent` 不再写死 `OrderService`。
- `RepairExecutorAgent` 不再写死固定 buggy/fixed 方法字符串。
- Agent 能通过 LangChain4j + OpenAI 根据 traceback、测试输出和源码上下文生成结构化修复计划。
- Agent 能生成结构化补丁 proposal，并且只能通过 `ToolPolicy` 白名单写入。
- 测试失败、diff 为空、路径越界、模型 JSON 无法解析时，都不能进入 commit/PR/飞书阶段。
- 修复记录能保存模型输入摘要、模型输出、补丁 proposal、测试结果、diff 和反思。

建议实施顺序：

1. 定义 Agent 数据契约：`EvidenceBundle`、`RepairAnalysis`、`RepairPlan`、`PatchProposal`，并要求 LLM 输出严格 JSON。（已开始）
2. 引入 LangChain4j OpenAI 集成，支持关闭开关、模型参数和 JSON 解析失败处理。（已开始）
3. 新增证据收集层，把 traceback、baseline 测试输出、候选源码文件和关键片段整理成 `EvidenceBundle`。（已开始）
4. 改造 `RepairPlannerAgent`，用 LangChain4j + OpenAI 产出根因分析、怀疑文件、修复步骤和测试策略。（已开始，保留 fallback）
5. 改造 `RepairExecutorAgent`，用 LangChain4j + OpenAI 产出 `PatchProposal`，再由 `PatchTools` 做安全落盘。（已开始，保留 fallback）
6. 强化 `RepairReviewerAgent`，把模型输出合法性、路径白名单、diff、测试结果都变成硬门禁。（已开始）
7. 扩展 `RepairRecord`，记录模型交互、重试历史、补丁 proposal 和最终反思。（已开始）
8. 准备可重复演示方式，优先使用 `demo-bug` 分支或明确的手动 reset 步骤恢复故障态。

## LangChain4j 集成说明

当前支持两条路径：

```text
默认稳定路径：
LangChain4j ChatModel + 严格 JSON planner/proposal + Java 工具执行

可选 Agentic 路径：
LangChain4j Agentic Supervisor + @Tool 只读工具 + non-AI Agent 安全执行
```

关键类：

- `RepairChatModelProvider`：按 `REPAIR_LLM_PROVIDER` 延迟创建 OpenAI 或 DashScope `ChatModel`。
- `LangChainRepairPlanner`：生成严格 JSON 格式的 `RepairPlan`。
- `LangChainPatchPlanner`：生成严格 JSON 格式的 `PatchProposal`。
- `AgenticRepairRunner`：启用 `REPAIR_AGENTIC_ENABLED=true` 时构建 Supervisor 和子 Agent。
- `StructuredJsonParser`：抽取和解析模型 JSON，解析失败则拒绝结果。
- `PatchTools`：唯一允许写入文件的工具，模型不能直接写文件。

启用 OpenAI LLM：

```powershell
$env:REPAIR_LLM_ENABLED="true"
$env:REPAIR_LLM_PROVIDER="openai"
$env:OPENAI_API_KEY="你的 OpenAI API Key"
$env:OPENAI_MODEL="gpt-4o-mini"
$env:OPENAI_BASE_URL="https://api.openai.com/v1"
$env:REPAIR_LLM_TIMEOUT_SECONDS="90"
$env:REPAIR_LLM_MAX_RETRIES="1"
```

启用 LangChain4j Agentic Supervisor：

```powershell
$env:REPAIR_AGENTIC_ENABLED="true"
$env:REPAIR_AGENTIC_MAX_SUPERVISOR_INVOCATIONS="24"
```

如果使用 OpenAI-compatible 的 Qwen 模型时出现 `request timed out`，优先调大 `REPAIR_LLM_TIMEOUT_SECONDS`，并保持 `REPAIR_LLM_MAX_RETRIES` 较小，避免一次修复被多轮重试拖得太久。Agentic 路径会对 traceback、源码片段和 SSE 工具事件做截断，防止把完整堆栈和文件内容反复送进模型。

默认 `REPAIR_LLM_ENABLED=false`，系统继续走稳定 fallback，便于比赛演示和本地调试。

暂缓内容：

- 前端工作台。
- RAG/Milvus 经验库。
- Sentry/GitHub Webhook 自动触发。
- 多语言、多项目泛化。

这些能力等真实 Agent 纵向链路稳定后再接入。

## 文档维护约定

为了重新打开终端或新开 Codex 会话时能快速恢复上下文，后续凡是发生以下变化，都要同步更新 `README.zh-CN.md`、`README.md` 和 `AGENTS.md`：

- 模块职责、架构边界或安全白名单变化。
- 启动命令、测试命令、环境变量变化。
- 演示流程、比赛口径或当前 Agent 能力状态变化。
- GitHub、飞书、Sentry、LLM、MCP 或其他外部集成方式变化。

## 本机 Skills 准备

本项目下一阶段要实现真实 Agent 修复，当前本机已有或已安装的相关 Codex skills：

- 已可用：`openai-docs`、`skill-creator`、`mcp-builder`、GitHub 插件 skills、`webapp-testing`。
- 2026-04-25 从 `openai/skills` curated 安装：`gh-address-comments`、`gh-fix-ci`、`yeet`、`sentry`、`security-threat-model`。
- `skills/.experimental` 本次查询返回不存在，暂未安装 experimental skills。
- 2026-04-25 检查 `superpowers`：本机路径是指向 `C:\Users\wuyib\.codex\superpowers` 的 junction，但目标目录不存在，当前不可用。

安装新 skills 后需要重启 Codex 才能在新会话中自动触发。

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

调用下面的接口用于复现 `quantity=0` 参数校验缺失问题。注意：当前主线代码已经加入校验，因此会返回 400；只有切到后续准备的 `demo-bug` 分支或手动恢复故障代码时，才会产生 `/ by zero` 异常：

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

如果要通过 HTTP 触发运行时异常，需要重启 `target-service` 后再请求：

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
REPAIR_LLM_MAX_TOKENS=2048
REPAIR_AGENTIC_ENABLED=false
REPAIR_AGENTIC_MAX_SUPERVISOR_INVOCATIONS=24

REPAIR_TARGET_LOG=target-service/logs
TARGET_SERVICE_TRACEBACK_LOG_DIR=logs/tracebacks

OPENAI_API_KEY=
OPENAI_MODEL=gpt-4o-mini
OPENAI_BASE_URL=https://api.openai.com/v1

DASHSCOPE_API_KEY=
DASHSCOPE_MODEL=qwen-max
DASHSCOPE_BASE_URL=

REPAIR_GIT_ENABLED=false
REPAIR_GITHUB_ENABLED=false
REPAIR_GIT_REMOTE=origin
REPAIR_BASE_BRANCH=main

FEISHU_ENABLED=false
FEISHU_WEBHOOK_URL=
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

`application-local.yml` 已加入 `.gitignore`，可以在里面填写真实 key，不要上传。启动本地 profile：

```powershell
cd D:\java_web_project\agent-aiOps
mvn -pl agent-platform spring-boot:run -Dspring-boot.run.profiles=local
```

如果使用 OpenAI 兼容的 DashScope/Qwen 接口，`application-local.yml` 中保持：

```yaml
openai:
  api-key: "你的 key"
  model: qwen3.6-plus
  base-url: https://dashscope.aliyuncs.com/compatible-mode/v1

repair:
  llm:
    enabled: true
    provider: openai
  agentic:
    enabled: true
```

隐私注意：

- 不要把真实 `OPENAI_API_KEY`、`DASHSCOPE_API_KEY`、`FEISHU_WEBHOOK_URL` 写入 README 或提交到 Git。
- 建议只在 PowerShell 环境变量里配置 key。
- `.gitignore` 已忽略 `.env`、`.env.*`、`*.secret`、`local-secrets/`、`local-reports/`。
- `agent-platform/src/main/resources/application.yml` 当前只保留环境变量占位，不应写入真实 key。

说明：

- `REPAIR_GIT_ENABLED=false` 时，不会创建分支、commit 或 push。
- `REPAIR_GITHUB_ENABLED=false` 时，不会调用 `gh CLI` 创建 PR。
- `FEISHU_ENABLED=false` 时，不会发送飞书卡片。
- 开启真实 PR 前，需要先在本机完成 `gh auth login`。

## 测试命令

Agent 平台测试：

```powershell
mvn -pl agent-platform test
```

被修复服务测试：

```powershell
mvn -pl target-service test
```

注意：当前主线 `target-service` 已处于修复后状态，测试应通过。演示故障态需要使用后续准备的 `demo-bug` 分支或手动 reset 步骤。

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

# 终端 3：触发 bug
Invoke-WebRequest "http://localhost:9910/api/orders/quote?totalCents=100&quantity=0"

# 终端 3：触发修复
$body = @{ sessionId = "demo-001" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/repair/run" -ContentType "application/json" -Body $body
```

后续如果接入 GitHub PR 和飞书，把环境变量改成：

```powershell
$env:REPAIR_GIT_ENABLED="true"
$env:REPAIR_GITHUB_ENABLED="true"
$env:FEISHU_ENABLED="true"
$env:FEISHU_WEBHOOK_URL="你的飞书 webhook"
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
- `PullRequestProvider`：后续可替换为 GitHub REST API。
- `ReviewPolicy`：后续可拆分安全审查、回归风险审查、测试覆盖审查。
- `RepairRecord`：后续可接入 RAG/Milvus，沉淀历史修复经验。
