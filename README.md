# Agent AI Ops

[中文说明](README.zh-CN.md)

Java implementation of a service auto-repair Agent demo.

## Modules

- `agent-platform`: Spring Boot repair Agent platform.
- `target-service`: Spring Boot service under repair.

## Demo Flow

1. Start the target service.
2. Trigger the validation bug so the service writes a standalone Java traceback under `target-service/logs/tracebacks`.
3. Start the Agent platform.
4. Call `POST /api/repair/run`.
5. Watch repair events from `GET /api/repair/stream/{sessionId}`.

The repair flow is a deterministic Java DAG with three LangChain4j AI sub-agents:

```text
detect
-> diagnose (AI)
-> plan (AI)
-> source-context
-> [ patch (AI) -> apply -> test ]  reflexion loop, up to REPAIR_MAX_PATCH_ATTEMPTS times
-> review
-> commit
-> PR (GitHub REST API)
-> Feishu v2 card
-> reflect
-> record
```

GitHub PR and Feishu delivery are implemented but disabled by default. Enable them with environment variables when recording the competition demo. PR creation defaults to the GitHub REST API (`REPAIR_GITHUB_CLIENT=rest`), so no `gh CLI` install is required; setting it to `cli` falls back to the legacy `gh` path.

The target service keeps the normal application log at `target-service/logs/target-service.log`. Unexpected runtime failures are also written as separate files under `target-service/logs/tracebacks/traceback-{timestamp}-{traceId}.log`, and the Agent reads the `target-service/logs` directory to pick the latest traceback.

## Current Agent Maturity

The backend loop is now a deterministic Java DAG (`AgenticRepairRunner`) coordinating three LangChain4j AI sub-agents (`AgenticDiagnosisAgent`, `AgenticPlanAgent`, `AgenticPatchAgent`) plus non-AI components for evidence collection, patch application, tests, review, commit, PR, notification, reflection, and repair records. There is no `SupervisorAgent` and no orchestration LLM call.

If a patched run fails the target tests, `AgenticPatchAgent.regeneratePatchFromTestFailure` receives the test stderr and rewrites the patch; `PatchApplyOperator` rolls back to the pre-apply file snapshot before the new patch is applied. The number of attempts is bounded by `REPAIR_MAX_PATCH_ATTEMPTS` (default 2).

The flow has been verified end to end on the `quantity-division-by-zero` demo fault: it read a standalone traceback, diagnosed `OrderService.calculateUnitPrice`, generated and applied a patch, ran `mvn -pl target-service test`, passed review, skipped disabled GitHub/Feishu actions safely, and wrote a repair record. A recent local run with `deepseek-v4-flash` completed the full repair loop in about 93 seconds. There is no non-LLM fallback; model configuration or invalid typed model output surfaces as repair `ERROR` events.

## Current Implementation Status

- Done: structured evidence, typed `DiagnosisResult`, typed `RepairPlan`, typed `PatchProposal`, LangChain4j `@Description` field hints, controlled patch application, tests, review gates, repair records, repair timing and token observability, demo fault injection, role-specific model routing, OpenAI-compatible model configuration, DashScope configuration.
- Done: deterministic Java DAG implementation under `repair/agentic` with AI sub-agents in `repair/agentic/agents` and non-AI operators in `repair/agentic/operators`.
- Done: reflexion loop on test failure (rollback + regeneratePatchFromTestFailure with bounded attempts).
- Done: real GitHub PR creation through REST API (`GitHubRestPullRequestProvider`), with auto-detected owner/repo from the git origin remote.
- Done: Feishu v2 interactive card with title/summary/timing/token block, "View PR" button, and optional signing-secret support.
- Disabled by default: Git commit/push, GitHub PR creation, and Feishu notification (toggle with `REPAIR_GIT_ENABLED`, `REPAIR_GITHUB_ENABLED`, `FEISHU_ENABLED`).
- Next: broaden repair scenarios beyond the first validation bug, add more orchestration-level tests, improve repair record retrieval/knowledge reuse, and add a frontend workbench after the backend flow stays stable.

The current mainline target service is repaired. Use the demo fault injection endpoints to switch it into a known failure state before testing automatic repair.

## Repair Timing And Token Observability

Repair timing and model usage are implemented without shortening the Agentic workflow. The Supervisor, diagnosis, planning, patch proposal, patch application, tests, review, commit, PR, Feishu notification, reflection, and record writing still run; timing and token data are collected alongside the workflow.

Implemented behavior:

- `RepairTiming` and `RepairStepTiming` capture total duration, per-step timing, model names, and token counts when a step calls a model.
- `RepairModelUsage` aggregates per-step model role, configured model, response model, call count, input tokens, output tokens, and total tokens.
- `RepairTimingCollector` records display timestamps with `Instant.now()` and calculates durations with `System.nanoTime()`.
- `RepairAgenticListener` reads LangChain4j `AgentResponse.chatResponse().modelName()` and `tokenUsage()` after each AI agent invocation.
- Agentic agent/operator calls are timed, including evidence, diagnosis, plan, patch, test, review, commit, PR, notification, reflection, and record writing.
- Completed SSE events include `durationMillis`, `stepName`, and `modelUsage`; AI-agent completion events include the model usage for that agent.
- `repair-records/{sessionId}.json` includes a `timing` field with `modelUsage`, and `repair-records/{sessionId}.md` includes `Timing` and `Model Usage` tables.

Validation:

- Run `mvn -pl agent-platform test`.
- Inject `quantity-division-by-zero`, run one Agentic repair, and confirm SSE, JSON, and Markdown records all include total duration, per-step duration, model names, and token counts.

## LangChain4j

LangChain4j is used only for the three AI sub-agents, not for orchestration or file writes:

- `RepairChatModelProvider`: lazily builds the configured OpenAI or DashScope `ChatModel`, with role-specific overrides for `diagnosis-model`, `plan-model`, and `patch-model`.
- `AgenticRepairRunner`: deterministic Java DAG; instantiates each AI sub-agent through `AgenticServices.agentBuilder(...)` and calls them sequentially, no `SupervisorAgent`.
- `AgenticDiagnosisAgent`, `AgenticPlanAgent`, `AgenticPatchAgent`: return typed records with LangChain4j `@Description` annotations. `AgenticPatchAgent.regeneratePatchFromTestFailure` is the entry point used by the reflexion loop.
- `PatchApplyOperator`: snapshots target files before each apply so the loop can roll back cleanly between attempts.
- `GitHubRestPullRequestProvider` + `GitRepoLocator`: create the PR via the GitHub REST API and resolve owner/repo from `git remote get-url origin`.
- `FeishuTools`: builds a Feishu v2 interactive card (header, summary, timing/token block, "View PR" button, footer) with optional HMAC-SHA256 signing.
- `PatchTools` and `ToolPolicy`: remain the only controlled file-write path.
- AI sub-agents only receive read-only `@Tool` methods; patching, Git, GitHub, and Feishu are non-AI components guarded by existing policies and disabled-mode config.

## Context Maintenance

Keep `README.zh-CN.md`, `README.md`, and `AGENTS.md` updated whenever architecture, commands, environment variables, demo flow, integrations, or Agent capability status changes.

Relevant local skills for the next phase:

- Available: `openai-docs`, `skill-creator`, `mcp-builder`, GitHub plugin skills, `webapp-testing`.
- Installed from `openai/skills` curated on 2026-04-25: `gh-address-comments`, `gh-fix-ci`, `yeet`, `sentry`, `security-threat-model`.
- `superpowers` was checked on 2026-04-25 but is currently a broken junction to `C:\Users\wuyib\.codex\superpowers`.
- Restart Codex after installing new skills.

## Useful Commands

```powershell
mvn -pl target-service spring-boot:run
mvn -pl agent-platform spring-boot:run
```

After injecting a demo fault and restarting `target-service`, trigger the demo bug:

```powershell
Invoke-WebRequest "http://localhost:9910/api/orders/quote?totalCents=100&quantity=0"
```

Useful log settings:

```text
REPAIR_TARGET_LOG=target-service/logs
TARGET_SERVICE_LOG_FILE=logs/target-service.log
TARGET_SERVICE_TRACEBACK_LOG_DIR=logs/tracebacks
```

Run the repair:

```powershell
$body = @{ sessionId = "demo-001" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/repair/run" -ContentType "application/json" -Body $body
```

The current mainline target service is already repaired, so this should pass. Use the demo fault injection endpoints to reproduce a pre-repair failure:

```powershell
mvn -pl target-service test
```

## Environment

```text
REPAIR_LLM_ENABLED=true
REPAIR_LLM_PROVIDER=openai
REPAIR_LLM_MAX_TOKENS=4096
REPAIR_LLM_TIMEOUT_SECONDS=90
REPAIR_LLM_MAX_RETRIES=1
REPAIR_MAX_PATCH_ATTEMPTS=2
OPENAI_API_KEY=
OPENAI_BASE_URL=https://api.openai.com/v1
REPAIR_GIT_ENABLED=false
REPAIR_GIT_REMOTE=origin
REPAIR_BASE_BRANCH=repair-demo-target
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

For repair runs, `REPAIR_LLM_ENABLED=true`, either `OPENAI_API_KEY` or `DASHSCOPE_API_KEY`, and a model in local config or environment variables are required. If the model is unavailable or returns invalid typed `DiagnosisResult`/`RepairPlan`/`PatchProposal` objects, the workflow publishes an `ERROR` event instead of using a deterministic fallback. For OpenAI-compatible endpoints such as DeepSeek or Qwen, increase `REPAIR_LLM_TIMEOUT_SECONDS` if provider calls time out. Keep `REPAIR_LLM_MAX_RETRIES` low during demos so a slow model call does not stall the whole repair loop. For complex faults, keep `REPAIR_LLM_MAX_TOKENS` at `4096` or higher. The DAG trims traceback, source context, and tool event payloads before sending them to the AI sub-agents. The highest-value role-specific override is usually `repair.llm.patch-model`, since the patch agent both generates and rewrites code; `diagnosis-model` and `plan-model` can stay on the default unless faults are ambiguous.

For real PR creation, set `REPAIR_GITHUB_ENABLED=true` and provide `GITHUB_TOKEN` (a fine-grained token with `repo` write scope is enough). Owner/repo are auto-detected from `git remote get-url origin`; override them with `REPAIR_GITHUB_OWNER` / `REPAIR_GITHUB_REPO` when running outside a checkout. The PR base branch defaults to `repair-demo-target`; create that branch on the remote first so each repair PR opens against a stable demo target instead of `main`.

For real Feishu delivery, set `FEISHU_ENABLED=true`, `FEISHU_WEBHOOK_URL`, and (if the bot enforces signature verification) `FEISHU_SIGNING_SECRET`. The card embeds the PR URL as a button and shows total duration plus token usage in a dedicated content block.

## Local Profile

Commit-safe config stays in:

```text
agent-platform/src/main/resources/application.yml
```

Local secrets go in the gitignored file:

```text
agent-platform/src/main/resources/application-local.yml
```

`application.yml` includes the `local` profile and imports this file with `optional:classpath:application-local.yml`, so the normal start command reads it automatically when it exists. The tracked `application.yml` intentionally does not choose OpenAI/DashScope model names or role-specific model overrides:

```powershell
mvn -pl agent-platform spring-boot:run
```

To test the Agentic Supervisor locally, keep the key only in `application-local.yml` or environment variables and set:

```yaml
repair:
  llm:
    enabled: true
    provider: openai
```

For an OpenAI-compatible local/demo provider, set the compatible base URL and model in `application-local.yml`, for example:

```yaml
openai:
  api-key: "your provider key"
  model: deepseek-v4-flash
  base-url: "your OpenAI-compatible /v1 endpoint"

repair:
  llm:
    enabled: true
    provider: openai
    patch-model: deepseek-v4-flash
```

## Demo Fault Injection

The Agent platform can inject local demo faults into fixed target-service source files:

```powershell
Invoke-RestMethod -Uri "http://localhost:9901/api/demo/faults"
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/demo/faults/quantity-division-by-zero/inject"
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/demo/faults/reset"
```

Fault injection edits source files only. Restart `target-service` after injecting or resetting a fault so the running Spring Boot process loads the changed code.

Supported fault types:

- `quantity-division-by-zero`
- `wrong-quote-route`
- `wrong-error-status`

Keep real API keys in environment variables only. `.env`, `.env.*`, `*.secret`, `local-secrets/`, and `local-reports/` are ignored.
