# Agent AI Ops Project Context

This file is the persistent context for future Codex/terminal sessions.

## User Goal

Build a Java competition demo for:

```text
基于 Agent 的服务自动化修复系统
```

Required demo loop:

```text
服务报错
-> Agent 读取 Traceback
-> 分析根因
-> 自动修改代码
-> 运行测试
-> 创建 GitHub PR
-> 飞书通知开发者 Review
-> 生成修复记录和反思沉淀
```

## Important Decision History

- Work only in `D:\java_web_project\agent-aiOps`.
- Do not write into the older `D:\java_web_project\aiOps` project unless the user explicitly asks.
- Current implementation is a standalone Java multi-module project.
- `target-service` represents a failing validation bug before repair, but current mainline is already in the repaired state.
- `agent-platform` is the Agent backend.
- Frontend is intentionally deferred until backend flow is stable.
- GitHub PR and Feishu are implemented but disabled by default.
- Demo fault injection is available under `POST /api/demo/faults/{faultType}/inject`; it writes only fixed demo files under `target-service/src/main`.
- Runtime 500 traceback logs are written as separate files under `target-service/logs/tracebacks/traceback-{timestamp}-{traceId}.log`, with traceback filenames and file `timestamp=` values formatted in Asia/Shanghai time.
- `agent-platform` should read traceback evidence from the `target-service/logs` directory, not only from one monolithic log file.
- Repair records should be written under repo-root `repair-records/`.
- If records appear under `agent-platform/repair-records/`, the running backend is stale or the workspace root was misdetected; restart `agent-platform` from repo root.
- Keep `README.zh-CN.md`, `README.md`, and this `AGENTS.md` updated whenever project architecture, commands, environment variables, demo flow, or Agent capability status changes.
- User preference: whenever answering about plans, planning, or roadmap decisions, search the web first and ground the answer in current sources when practical.
- Keep local cycle reports and private review notes under repo-root `local-reports/`; this directory is gitignored and must not be uploaded to GitHub.
- Upload-safe config belongs in `agent-platform/src/main/resources/application.yml`; local secrets and model choices belong in gitignored `agent-platform/src/main/resources/application-local.yml`, which is imported automatically through the default included `local` profile and `optional:classpath:application-local.yml`.
- Current Agent maturity: the workflow is a deterministic Java DAG (`AgenticRepairRunner`) that calls three LangChain4j AI sub-agents (`AgenticDiagnosisAgent`, `AgenticPlanAgent`, `AgenticPatchAgent`) for the language tasks plus non-AI components for evidence, patch apply, tests, review, commit, PR, Feishu, reflection, and records. There is no `SupervisorAgent` or orchestration LLM call. Missing LLM configuration or invalid typed model output publishes a repair `ERROR`. Current mainline `target-service` is in the repaired state.
- Reflexion loop on test failure: `AgenticPatchAgent.regeneratePatchFromTestFailure` rewrites the patch using the failing test stderr; `PatchApplyOperator` snapshots target files before each apply and rolls them back between attempts. Bounded by `repair.workflow.max-patch-attempts` (env `REPAIR_MAX_PATCH_ATTEMPTS`, default 2).
- Real GitHub PR is wired through `GitHubRestPullRequestProvider` (REST API). `repair.github.client=rest|cli` selects the client (default `rest`); `gh CLI` is no longer required. Owner/repo come from `repair.github.owner` / `repair.github.repo` first, falling back to parsing `git remote get-url origin` via `GitRepoLocator`. `GitTools.commitAndPush` fetches and checks out `repair.git.base-branch` (default `repair-demo-target`) before creating the `repair/{sessionId}` head branch, and uses `fix(repair): {plan.repairTarget()}` as the commit message.
- Feishu v2 interactive card: `FeishuTools.sendRepairCard` builds a v2.0 schema with header title from repair target, summary text (root cause / review reason / PR URL), a separate timing/token block, "View PR" button, and session-id footer; supports optional `FEISHU_SIGNING_SECRET` HMAC-SHA256 signing; `NotifyOperator` passes `RepairTiming` and `PullRequestResult` into the card.
- Prompt/SSE payloads are intentionally bounded: traceback, read-file results, source context, and tool event messages are trimmed before they are passed to the AI sub-agents. The reflexion path also trims test stderr (~2000 chars) before feeding it to `AgenticPatchAgent`.
- Repair timing and token observability is implemented: completed SSE events include `stepName=repairWorkflow`, `mode=java-dag`, `patchAttempts`, `durationMillis`, and `modelUsage`; AI-agent completion events include per-agent model usage; repair record JSON includes `timing.modelUsage`; repair record Markdown includes `Timing` and `Model Usage` tables.
- Latest rollback E2E: session `rollback-e2e-001` used `deepseek-v4-flash` through the OpenAI-compatible provider, completed the `quantity-division-by-zero` repair in about 93 seconds, patched `OrderService.java`, passed all 5 target-service tests, and skipped Git/GitHub/Feishu because they were disabled.
- All three AI sub-agents return strongly typed records (`DiagnosisResult`, `RepairPlan`, `PatchProposal`) with LangChain4j `@Description` field annotations; Java parser operators validate typed objects instead of parsing raw JSON.
- Role-specific model routing is configured locally with `repair.llm.diagnosis-model`, `repair.llm.plan-model`, and `repair.llm.patch-model` in `application-local.yml` or equivalent environment variables. The supervisor model override has been removed. If only one role gets a stronger model, use `patch-model` first since it both generates and rewrites patches.

## Local Skill Setup

Useful local skills for the next phase:

- Preinstalled/available: `openai-docs`, `skill-creator`, `mcp-builder`, GitHub plugin skills, `webapp-testing`.
- Installed on 2026-04-25 from `openai/skills` curated: `gh-address-comments`, `gh-fix-ci`, `yeet`, `sentry`, `security-threat-model`.
- `superpowers` was requested on 2026-04-25, but the local path is a broken junction to `C:\Users\wuyib\.codex\superpowers`; do not rely on it until that target is restored.
- Restart Codex after installing new skills so newly installed skills are picked up by the session.

## Next Phase: Real Agent Repair

Repair is implemented as a deterministic Java DAG with three LangChain4j AI sub-agents:

- Read traceback/log evidence and failing tests via Java operators.
- Use LangChain4j typed AI agents (`AgenticDiagnosisAgent`, `AgenticPlanAgent`, `AgenticPatchAgent`) for diagnosis, planning, and patch generation/regeneration.
- Java DAG enforces ordering; reflexion loop rewrites the patch from test stderr and rolls back via `PatchApplyOperator` snapshots.
- Generate structured patch proposals, validate paths through `ToolPolicy`, apply them, and run tests.
- Review diff/test output with policy checks before GitHub REST PR and Feishu v2 card.
- Persist repair records and reflection for future retrieval/RAG.

Current implementation status:

1. Structured evidence, typed diagnoses, typed repair plans, typed patch proposals, safe patch application, tests, review gates, and repair records are implemented.
2. LangChain4j OpenAI-compatible model integration is implemented with configurable timeout and retry behavior.
3. Deterministic Java DAG implementation lives in `repair/agentic` with AI sub-agents under `repair/agentic/agents` and non-AI operators under `repair/agentic/operators`.
4. Reflexion (apply -> test -> rollback -> regenerate) bounded by `repair.workflow.max-patch-attempts`.
5. Real GitHub PR via REST API and real Feishu v2 card with timing/token block are implemented and gated by enable flags.
6. Demo fault injection is available for local replay.
7. Next work: validate more fault types end to end, add more orchestration-level tests, and improve repair record retrieval/knowledge reuse.

## LangChain4j Integration Notes

- Dependencies are in `agent-platform/pom.xml`: `dev.langchain4j:langchain4j`, `dev.langchain4j:langchain4j-open-ai`, `dev.langchain4j:langchain4j-community-dashscope`, and `dev.langchain4j:langchain4j-agentic`.
- `RepairChatModelProvider` lazily builds the configured OpenAI or DashScope `ChatModel`, with overrides for `diagnosis-model`, `plan-model`, and `patch-model`.
- OpenAI-compatible model calls use configurable `repair.llm.timeout-seconds` and `repair.llm.max-retries`; use `REPAIR_LLM_TIMEOUT_SECONDS` and `REPAIR_LLM_MAX_RETRIES` locally.
- `AgenticRepairRunner` instantiates each AI sub-agent through `AgenticServices.agentBuilder(...)` and calls them sequentially in Java; AI agent interfaces live in `repair/agentic/agents`, non-AI execution nodes live in `repair/agentic/operators`, and shared state/tools/listeners/helpers live directly under `repair/agentic`.
- AI sub-agents get read-only `@Tool` methods for logs/code. Patch apply, Git, GitHub REST, Feishu, reflection, and records remain non-AI components.
- Typed AI outputs are validated by Java gates in `PlanParserOperator` and `PatchParserOperator`.
- `RepairTimingCollector` records Agentic step timing with monotonic durations plus LangChain4j model usage from `AgentResponse.chatResponse().modelName()` and `tokenUsage()`, then stores it in repair records.
- LangChain4j does not write files directly. `PatchTools` and `ToolPolicy` remain the only write path.

## Modules

- `agent-platform`
  - Spring Boot Agent backend.
  - Exposes `POST /api/repair/run`.
  - Exposes `GET /api/repair/stream/{sessionId}` for SSE.
  - Implements Plan-Execute-Review-Reflect workflow.

- `target-service`
  - Spring Boot service under repair.
  - Demo bug scenario: `quantity=0` causes `/ by zero` in `OrderService` before repair.
  - Current mainline is repaired, so tests should pass. Use the demo fault injection API to replay the failure.

## Commands

Always start from repo root:

```powershell
cd D:\java_web_project\agent-aiOps
```

Terminal 1:

```powershell
mvn -pl target-service spring-boot:run
```

Terminal 2:

```powershell
mvn -pl agent-platform spring-boot:run
```

Terminal 3, trigger bug:

```powershell
Invoke-WebRequest "http://localhost:9910/api/orders/quote?totalCents=100&quantity=0"
```

Terminal 3, trigger repair:

```powershell
$body = @{ sessionId = "demo-001" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/repair/run" -ContentType "application/json" -Body $body
```

Demo fault injection:

```powershell
Invoke-RestMethod -Uri "http://localhost:9901/api/demo/faults"
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/demo/faults/quantity-division-by-zero/inject"
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/demo/faults/reset"
```

After injecting or resetting a demo fault, restart `target-service` before triggering HTTP behavior. The injection API edits source files only and does not hot reload the running Spring Boot process.

Enable repair locally:

```powershell
$env:REPAIR_LLM_ENABLED="true"
$env:REPAIR_LLM_PROVIDER="openai"
$env:OPENAI_API_KEY="your OpenAI API key"
$env:REPAIR_LLM_MAX_TOKENS="4096"
$env:REPAIR_MAX_PATCH_ATTEMPTS="2"
```

Enable real PR + Feishu locally:

```powershell
$env:REPAIR_GIT_ENABLED="true"
$env:REPAIR_GITHUB_ENABLED="true"
$env:REPAIR_GITHUB_CLIENT="rest"
$env:GITHUB_TOKEN="your fine-grained github token"
$env:REPAIR_BASE_BRANCH="repair-demo-target"
$env:FEISHU_ENABLED="true"
$env:FEISHU_WEBHOOK_URL="your feishu webhook"
$env:FEISHU_SIGNING_SECRET="your feishu signing secret if signing is enforced"
```

SSE stream:

```text
GET http://localhost:9901/api/repair/stream/demo-001
```

## Verification

Agent backend:

```powershell
mvn -pl agent-platform test
```

Target service current mainline:

```powershell
mvn -pl target-service test
```

This should pass on current mainline. Use `POST /api/demo/faults/{faultType}/inject` to replay a pre-repair failure state.

Compile target service without running the intentional failing tests:

```powershell
mvn -pl target-service -DskipTests package
```

## Safety Rules

- Repair tools should only read `target-service/src` and `target-service/logs`.
- Repair tools should only write `target-service/src/main` and `target-service/src/test`.
- Do not let repair tools modify `agent-platform`, root configs, secrets, or scripts.
- Keep GitHub and Feishu disabled by default unless demo credentials are configured.
- Keep real API keys and webhooks out of tracked files; use environment variables or ignored local files only.
