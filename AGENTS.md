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
- Frontend is now a Vue 3 + Vite + TypeScript judge demo console under `frontend/`; production assets are built into `agent-platform/src/main/resources/static` and served at `http://localhost:9901/`. Current UI is a professional repair-session workbench with Run, Tool Trace, Artifacts, PR Diff, and Judge Evidence views.
- LLM repair, GitHub PR, and Feishu are implemented but disabled by default in upload-safe config.
- Demo fault injection is available under `POST /api/demo/faults/{faultType}/inject`; it writes only fixed demo files under `target-service/src/main`.
- One-click demo scenario orchestration is available under `POST /api/demo/scenarios/start` and `POST /api/demo/scenarios/{sessionId}/confirm-target-restarted`; it injects a fault, waits for manual `target-service` restart, prepares fresh traceback/test evidence, and starts the repair workflow.
- Source-injection demo scenarios require `REPAIR_GIT_ENABLED=false`; they intentionally dirty the working tree. For real GitHub PR demos, use the committed `demo/fault/...` base branch and the PR-safe scenario API.
- PR-safe one-click demo scenarios are available under `POST /api/demo/pr-scenarios/start`, `POST /api/demo/pr-scenarios/{sessionId}/restart-target-service`, and `POST /api/demo/pr-scenarios/{sessionId}/confirm-target-restarted`; they require `REPAIR_GIT_ENABLED=true`, `REPAIR_GITHUB_ENABLED=true`, and `REPAIR_BASE_BRANCH=demo/fault/{faultType}`. They prepare `repair/{sessionId}` in an isolated git worktree under `REPAIR_WORKTREE_ROOT`, so the main checkout stays on `main`. The restart endpoint starts target-service from the worktree for local demos and the frontend falls back to manual restart if it fails.
- PR-safe demo fault branch mapping: `quantity-division-by-zero -> demo/fault/quantity-division-by-zero`, `wrong-quote-route -> demo/fault/wrong-quote-route`, `wrong-error-status -> demo/fault/wrong-error-status`.
- `TARGET_SERVICE_BASE_URL` configures the running target-service URL used by scenario orchestration; default is `http://localhost:9910`.
- Runtime 500 traceback logs are written as separate files under `target-service/logs/tracebacks/traceback-{timestamp}-{traceId}.log`, with traceback filenames and file `timestamp=` values formatted in Asia/Shanghai time.
- `agent-platform` should read traceback evidence from the `target-service/logs` directory, not only from one monolithic log file.
- For `wrong-quote-route` and `wrong-error-status` scenarios, `agent-platform` runs target-service tests once and writes a latest test-failure evidence log under `target-service/logs/tracebacks/` to avoid stale runtime tracebacks misleading diagnosis.
- Repair records should be written under repo-root `repair-records/`.
- Repair records now preserve pre-commit code review evidence as both `diffSummary` and structured `diffFiles[]` (`filePath`, status, additions/deletions, hunks, lines) so the frontend can render a GitHub-like Files changed view even after commit/push cleans the worktree.
- Repair record summaries are exposed through `GET /api/repair/records`, which aggregates `repair-records/*.json` into outcome/timing/token/test/PR/notification summaries for frontend and experiment views. Full records are exposed through `GET /api/repair/records/{sessionId}`.
- PR-safe demo readiness is exposed through `GET /api/demo/pr-scenarios/readiness?faultType=...`; it returns LLM/Git/GitHub/Feishu/base-branch/worktree booleans and warnings without exposing secrets.
- If records appear under `agent-platform/repair-records/`, the running backend is stale or the workspace root was misdetected; restart `agent-platform` from repo root.
- Keep `README.zh-CN.md`, `README.md`, and this `AGENTS.md` updated whenever project architecture, commands, environment variables, demo flow, or Agent capability status changes.
- User preference: whenever answering about plans, planning, or roadmap decisions, search the web first and ground the answer in current sources when practical.
- Keep local cycle reports and private review notes under repo-root `local-reports/`; this directory is gitignored and must not be uploaded to GitHub.
- Upload-safe config belongs in `agent-platform/src/main/resources/application.yml`; local secrets and model choices belong in gitignored `agent-platform/src/main/resources/application-local.yml`, which is imported automatically through the default included `local` profile and `optional:classpath:application-local.yml`.
- Current Agent maturity: the workflow is a deterministic Java DAG (`AgenticRepairRunner`) that calls four LangChain4j AI sub-agents (`AgenticDiagnosisAgent`, `AgenticPlanAgent`, `AgenticPatchAgent`, `AgenticReflectionAgent`) for language tasks plus non-AI components for evidence, patch apply, tests, review, commit, PR, Feishu, and records. There is no `SupervisorAgent` or orchestration LLM call. Missing LLM configuration or invalid typed model output after one retry publishes a repair `ERROR` and writes a minimal error record. Current mainline `target-service` is in the repaired state.
- Reflexion loop on test failure: `AgenticPatchAgent.regeneratePatchFromTestFailure` rewrites the patch using the failing test stderr; `PatchApplyOperator` snapshots target files before each apply and rolls them back between attempts. Bounded by `repair.workflow.max-patch-attempts` (env `REPAIR_MAX_PATCH_ATTEMPTS`, default 2).
- Real GitHub PR is wired through `GitHubRestPullRequestProvider` (REST API). `repair.github.client=rest|cli` selects the client (default `rest`); `gh CLI` is no longer required. Owner/repo come from `repair.github.owner` / `repair.github.repo` first, falling back to parsing `git remote get-url origin` via `GitRepoLocator`. PR-safe scenarios call `GitTools.prepareRepairWorktreeFromBase`, which fetches `repair.git.base-branch` (default `demo/fault/quantity-division-by-zero`) and creates `repair/{sessionId}` in `repair.git.worktree-root` (env `REPAIR_WORKTREE_ROOT`, default `../agent-aiOps-worktrees`) before repair starts. `GitTools.commitAndPush` then commits only target-service changes with `fix(repair): {plan.repairTarget()}` and pushes the repair branch from that worktree.
- Feishu v2 interactive card: `FeishuTools.sendRepairCard` builds a v2.0 schema with header title from repair outcome, summary text (outcome / root cause / review reason / PR URL), a separate timing/token block, "View PR" button, and session-id footer; supports optional `FEISHU_SIGNING_SECRET` HMAC-SHA256 signing; `NotifyOperator` passes `RepairOutcome`, `RepairTiming`, and `PullRequestResult` into the card. Failed repairs use failure copy and must not claim "fixed". If model token usage is unavailable, the card says usage was not returned/collected instead of showing 0.
- Prompt/SSE payloads are intentionally bounded: traceback, read-file results, source context, and tool event messages are trimmed before they are passed to the AI sub-agents. The reflexion path also trims test stderr (~2000 chars) before feeding it to `AgenticPatchAgent`.
- Repair timing and token observability is implemented: `AgenticRepairRunner` times Java DAG steps directly; `ObservedChatModel` wraps each role-specific `ChatModel` and records model usage from `ChatResponse.modelName()` / `tokenUsage()` at the model boundary; completed SSE events include `stepName=repairWorkflow`, `mode=java-dag`, `patchAttempts`, `durationMillis`, and `modelUsage`; repair record JSON includes `timing.modelUsage`; repair record Markdown includes `Timing` and `Model Usage` tables.
- Latest real E2E: session `pr-quantity-002` ran through the PR-safe one-click API on `demo/fault/quantity-division-by-zero`, completed the `quantity-division-by-zero` repair, patched `OrderService.java`, passed all 5 target-service tests, pushed `repair/pr-quantity-002`, created GitHub PR https://github.com/deibudei/agent-aiOps/pull/3, sent the Feishu success card, wrote `repair-records/pr-quantity-002.json` / `.md`, and completed with `outcome=FIXED`.
- All AI sub-agents return strongly typed records (`DiagnosisResult`, `RepairPlan`, `PatchProposal`, `RepairReflection`) with LangChain4j `@Description` field annotations; Java parser/operators validate typed objects and retry once before surfacing `ERROR`.
- Role-specific model routing is configured locally with `repair.llm.diagnosis-model`, `repair.llm.plan-model`, `repair.llm.patch-model`, and `repair.llm.reflection-model` in `application-local.yml` or equivalent environment variables. The supervisor model override has been removed. If only one role gets a stronger model, use `patch-model` first since it both generates and rewrites patches.
- Repair outcomes are explicit: completed SSE events and repair records include `RepairOutcome` (`FIXED`, `FAILED`, `ERROR`) and `outcomeReason`. Patch/test/review/PR failures are controlled `COMPLETED + FAILED`; workflow exceptions are `ERROR` with a minimal error record.
- For real PR demos, do not inject a temporary fault on `main` and expect a useful PR diff. `main` stays repaired. Use `demo/fault/quantity-division-by-zero` as a committed faulty base branch (current `main` plus only the division-by-zero fault), and set `REPAIR_BASE_BRANCH=demo/fault/quantity-division-by-zero`.
- PR-safe demo mental model: start `agent-platform` from `main`; `POST /api/demo/pr-scenarios/start` creates `repair/{sessionId}` in `REPAIR_WORKTREE_ROOT/{sessionId}` from `demo/fault/{faultType}`; restart only `target-service` from that worktree (the Vue console attempts `restart-target-service` automatically); `agent-platform` keeps running with the main-compiled JVM code while repair tools read/write `target-service` in the active worktree context; wait for SSE `completed` and repair records before deleting worktrees/branches or editing docs/platform files.
- Do ongoing project work on `main` or normal feature branches targeting `main`. Do not modify docs or platform code on `demo/fault/...` or `repair/{sessionId}` branches. After `main` changes, refresh the demo fault branch from latest `main` and keep only the intentional fault commit on top (`git checkout -B demo/fault/quantity-division-by-zero main`, inject the fault, commit it, then `git push --force-with-lease origin demo/fault/quantity-division-by-zero`). Treat `repair/{sessionId}` branches as disposable demo outputs.
- Real GitHub PR token requirement: if `GITHUB_TOKEN` is a fine-grained personal access token, Repository access must include `deibudei/agent-aiOps`, with `Contents: Read and write` and `Pull requests: Read and write`. Read-only code/PR permission causes GitHub HTTP 403 during PR creation even when local `git push` succeeds through separate Git credentials.

## Local Skill Setup

Useful local skills for the next phase:

- Preinstalled/available: `openai-docs`, `skill-creator`, `mcp-builder`, GitHub plugin skills, `webapp-testing`.
- Installed on 2026-04-25 from `openai/skills` curated: `gh-address-comments`, `gh-fix-ci`, `yeet`, `sentry`, `security-threat-model`.
- `superpowers` was requested on 2026-04-25, but the local path is a broken junction to `C:\Users\wuyib\.codex\superpowers`; do not rely on it until that target is restored.
- Restart Codex after installing new skills so newly installed skills are picked up by the session.

## Current Repair Architecture

Repair is implemented as a deterministic Java DAG with four LangChain4j AI sub-agents:

- Read traceback/log evidence and failing tests via Java operators.
- Use LangChain4j typed AI agents (`AgenticDiagnosisAgent`, `AgenticPlanAgent`, `AgenticPatchAgent`, `AgenticReflectionAgent`) for diagnosis, planning, patch generation/regeneration, and reflection.
- Java DAG enforces ordering; reflexion loop rewrites the patch from test stderr and rolls back via `PatchApplyOperator` snapshots.
- Generate structured patch proposals, validate paths through `ToolPolicy`, apply them, and run tests.
- Review diff/test output with policy checks before GitHub REST PR and Feishu v2 card.
- Persist repair records and reflection for future retrieval/RAG.

Current implementation status:

1. Structured evidence, typed diagnoses, typed repair plans, typed patch proposals, typed reflections, atomic safe patch application, tests, review gates, outcomes, and repair records are implemented.
2. LangChain4j OpenAI-compatible model integration is implemented with configurable timeout and retry behavior.
3. Deterministic Java DAG implementation lives in `repair/agentic` with AI sub-agents under `repair/agentic/agents` and non-AI operators under `repair/agentic/operators`.
4. Reflexion (apply -> test -> rollback -> regenerate) bounded by `repair.workflow.max-patch-attempts`.
5. Real GitHub PR via REST API and real Feishu v2 card with timing/token block are implemented, gated by enable flags, and validated in `pr-quantity-002`.
6. Demo fault injection and one-click demo scenario orchestration are available for local replay.
7. Repair record indexing and detail APIs are available through `GET /api/repair/records` and `GET /api/repair/records/{sessionId}`.
8. Vue judge demo console is implemented in `frontend/`, uses the PR-safe scenario API and SSE stream, auto-attempts target-service restart, persists live events across refresh, and is served by `agent-platform` after `npm --prefix frontend run build`; V2 presents a single repair-session workbench with ChatOps, Tool Trace, Artifacts, PR Diff, and Judge Evidence views.
9. Structured SSE tool events now include `eventType`, `toolName`, `target`, `status`, and `success` for `ReadLog`, `ReadCode`, `RunTest`, and `GitCommit`, so the frontend can show the required Tool Use evidence explicitly.
10. Next work: validate each supported fault type once through the PR-safe scenario API, keep one real PR + Feishu golden path, and record the official demo from the Vue console.

## LangChain4j Integration Notes

- Dependencies are in `agent-platform/pom.xml`: `dev.langchain4j:langchain4j`, `dev.langchain4j:langchain4j-open-ai`, `dev.langchain4j:langchain4j-community-dashscope`, and `dev.langchain4j:langchain4j-agentic`.
- `RepairChatModelProvider` lazily builds the configured OpenAI or DashScope `ChatModel`, with overrides for `diagnosis-model`, `plan-model`, `patch-model`, and `reflection-model`.
- OpenAI-compatible model calls use configurable `repair.llm.timeout-seconds` and `repair.llm.max-retries`; use `REPAIR_LLM_TIMEOUT_SECONDS` and `REPAIR_LLM_MAX_RETRIES` locally.
- `AgenticRepairRunner` instantiates each AI sub-agent through `AgenticServices.agentBuilder(...)` and calls them sequentially in Java; AI agent interfaces live in `repair/agentic/agents`, non-AI execution nodes live in `repair/agentic/operators`, and shared state/tools/listeners/helpers live directly under `repair/agentic`.
- AI sub-agents get read-only `@Tool` methods for logs/code. Patch apply, Git, GitHub REST, Feishu, and records remain non-AI components.
- Typed AI outputs are validated by Java gates in diagnosis, plan, patch, and reflection paths. Invalid typed output gets one retry.
- `RepairTimingCollector` records Agentic step timing with monotonic durations. LangChain4j model usage is collected separately by `ObservedChatModel` from `ChatResponse.modelName()` and `tokenUsage()` when available, then stored in repair records.
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

One-click demo scenario:

```powershell
$body = @{ sessionId = "scenario-quantity-001"; faultType = "quantity-division-by-zero" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/demo/scenarios/start" -ContentType "application/json" -Body $body
# Restart target-service after WAITING_FOR_TARGET_RESTART.
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/demo/scenarios/scenario-quantity-001/confirm-target-restarted"
Invoke-RestMethod -Uri "http://localhost:9901/api/repair/records"
```

One-click demo + PR scenario:

```powershell
$env:REPAIR_GIT_ENABLED="true"
$env:REPAIR_GITHUB_ENABLED="true"
$env:REPAIR_BASE_BRANCH="demo/fault/quantity-division-by-zero"
$body = @{ sessionId = "pr-quantity-001"; faultType = "quantity-division-by-zero" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/demo/pr-scenarios/start" -ContentType "application/json" -Body $body
# Optional manual equivalent; the Vue console calls this restart endpoint automatically.
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/demo/pr-scenarios/pr-quantity-001/restart-target-service"
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/demo/pr-scenarios/pr-quantity-001/confirm-target-restarted"
```

Frontend demo console:

```powershell
npm --prefix frontend install
npm --prefix frontend run build
mvn -pl agent-platform spring-boot:run
# Open http://localhost:9901/
```

Frontend development server:

```powershell
npm --prefix frontend run dev
```

For `wrong-quote-route` and `wrong-error-status`, create committed base branches `demo/fault/wrong-quote-route` and `demo/fault/wrong-error-status` first, then restart `agent-platform` with the matching `REPAIR_BASE_BRANCH`.

Enable repair locally:

```powershell
$env:REPAIR_LLM_ENABLED="true"
$env:REPAIR_LLM_PROVIDER="openai"
$env:OPENAI_API_KEY="your OpenAI API key"
$env:REPAIR_LLM_MAX_TOKENS="4096"
$env:REPAIR_LLM_REFLECTION_MODEL=""
$env:REPAIR_MAX_PATCH_ATTEMPTS="2"
$env:TARGET_SERVICE_BASE_URL="http://localhost:9910"
```

Enable real PR + Feishu locally:

```powershell
$env:REPAIR_GIT_ENABLED="true"
$env:REPAIR_GITHUB_ENABLED="true"
$env:REPAIR_GITHUB_CLIENT="rest"
$env:GITHUB_TOKEN="your fine-grained github token"
$env:REPAIR_BASE_BRANCH="demo/fault/quantity-division-by-zero"
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

Frontend:

```powershell
npm --prefix frontend run build
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
