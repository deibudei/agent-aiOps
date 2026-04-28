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
- Current Agent maturity: the workflow is LangChain4j Agentic-only. A Supervisor coordinates AI agents for diagnosis/planning/patch proposal and non-AI agents for evidence, patch apply, tests, review, commit, PR, Feishu, reflection, and records. There is no non-Agentic fallback; missing LLM configuration or invalid model JSON publishes a repair `ERROR`. Current mainline `target-service` is in the repaired state.
- Agentic prompt/SSE payloads are intentionally bounded: traceback, read-file results, source context, and tool event messages are trimmed before they are passed through the Supervisor. This keeps OpenAI-compatible model calls from timing out on oversized evidence.
- Repair timing and token observability is implemented: completed SSE events include `stepName=repairWorkflow`, `durationMillis`, and `modelUsage`; AI-agent completion events include per-agent model usage; repair record JSON includes `timing.modelUsage`; repair record Markdown includes `Timing` and `Model Usage` tables.
- Latest rollback E2E: session `rollback-e2e-001` used `deepseek-v4-flash` through the OpenAI-compatible provider, completed the `quantity-division-by-zero` repair in about 93 seconds, patched `OrderService.java`, passed all 5 target-service tests, and skipped Git/GitHub/Feishu because they were disabled.
- `AgenticPlanAgent` now returns a typed `RepairPlan` instead of a raw JSON string; `RepairPlan` fields have LangChain4j `@Description` annotations and the planning prompt includes two few-shot examples.
- `AgenticDiagnosisAgent` and `AgenticPatchAgent` also return typed records (`DiagnosisResult`, `PatchProposal`) with LangChain4j `@Description` field annotations. Java parser operators now validate typed objects instead of parsing raw JSON strings.
- Role-specific model routing is configured locally with `repair.llm.supervisor-model`, `repair.llm.patch-model`, `repair.llm.diagnosis-model`, and `repair.llm.plan-model` in `application-local.yml` or equivalent environment variables. If only one role gets a stronger model, use it for the Supervisor first; if two roles do, use Supervisor and Patch.
- Agentic structured invocation/output compliance remains model-sensitive and will be handled separately; do not reintroduce rolled-back repair experiments unless explicitly requested.

## Local Skill Setup

Useful local skills for the next phase:

- Preinstalled/available: `openai-docs`, `skill-creator`, `mcp-builder`, GitHub plugin skills, `webapp-testing`.
- Installed on 2026-04-25 from `openai/skills` curated: `gh-address-comments`, `gh-fix-ci`, `yeet`, `sentry`, `security-threat-model`.
- `superpowers` was requested on 2026-04-25, but the local path is a broken junction to `C:\Users\wuyib\.codex\superpowers`; do not rely on it until that target is restored.
- Restart Codex after installing new skills so newly installed skills are picked up by the session.

## Next Phase: Real Agent Repair

Use LangChain4j Agentic Supervisor orchestration while keeping Java tools as the controlled execution layer:

- Read traceback/log evidence and failing tests.
- Ask OpenAI-compatible models or DashScope/OpenAI through LangChain4j to produce structured root-cause analysis and repair plans.
- Let the Agentic Supervisor choose sub-agents while only exposing read-only `@Tool` methods to AI agents.
- Generate a structured patch proposal, validate paths through `ToolPolicy`, apply it, and run tests.
- Review diff/test output with policy checks before GitHub PR and Feishu notification.
- Persist repair records and reflection for future retrieval/RAG.

Current implementation status:

1. Structured evidence, typed diagnoses, typed repair plans, typed patch proposals, safe patch application, tests, review gates, and repair records are implemented.
2. LangChain4j OpenAI-compatible model integration is implemented with configurable timeout and retry behavior.
3. `langchain4j-agentic` Supervisor orchestration is implemented and split across `repair/agentic`, `repair/agentic/agents`, and `repair/agentic/operators`.
4. Demo fault injection is available and should be used instead of relying on a future `demo-bug` branch for local replay.
5. Next work: validate more fault types in Agentic mode, add orchestration-level tests, and improve repair record retrieval/knowledge reuse.

## LangChain4j Integration Notes

- Dependencies are in `agent-platform/pom.xml`: `dev.langchain4j:langchain4j`, `dev.langchain4j:langchain4j-open-ai`, `dev.langchain4j:langchain4j-community-dashscope`, and `dev.langchain4j:langchain4j-agentic`.
- `RepairChatModelProvider` lazily builds the configured OpenAI or DashScope `ChatModel`.
- OpenAI-compatible model calls use configurable `repair.llm.timeout-seconds` and `repair.llm.max-retries`; use `REPAIR_LLM_TIMEOUT_SECONDS` and `REPAIR_LLM_MAX_RETRIES` locally.
- Role-specific model overrides are configured under `repair.llm.*-model`; the default model still comes from the provider config when an override is blank.
- `AgenticRepairRunner` builds the LangChain4j Agentic Supervisor; AI agent interfaces live in `repair/agentic/agents`, non-AI execution nodes live in `repair/agentic/operators`, and shared state/tools/listeners/helpers live directly under `repair/agentic`.
- Agentic AI agents get read-only `@Tool` methods for logs/code. Patch, Git, GitHub, Feishu, reflection, and records remain non-AI agents.
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

Enable LangChain4j Agentic repair locally:

```powershell
$env:REPAIR_LLM_ENABLED="true"
$env:REPAIR_LLM_PROVIDER="openai"
$env:OPENAI_API_KEY="your OpenAI API key"
$env:REPAIR_LLM_MAX_TOKENS="4096"
$env:REPAIR_AGENTIC_MAX_SUPERVISOR_INVOCATIONS="24"
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
