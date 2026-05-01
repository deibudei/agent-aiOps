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

The repair flow is a deterministic Java DAG with four LangChain4j AI sub-agents:

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
-> reflect (AI)
-> record
```

GitHub PR and Feishu delivery are implemented but disabled by default. Enable them with environment variables when recording the competition demo. PR creation defaults to the GitHub REST API (`REPAIR_GITHUB_CLIENT=rest`), so no `gh CLI` install is required; setting it to `cli` falls back to the legacy `gh` path.

The target service keeps the normal application log at `target-service/logs/target-service.log`. Unexpected runtime failures are also written as separate files under `target-service/logs/tracebacks/traceback-{timestamp}-{traceId}.log`, and the Agent reads the `target-service/logs` directory to pick the latest traceback.

## Current Agent Maturity

The backend loop is now a deterministic Java DAG (`AgenticRepairRunner`) coordinating four LangChain4j AI sub-agents (`AgenticDiagnosisAgent`, `AgenticPlanAgent`, `AgenticPatchAgent`, `AgenticReflectionAgent`) plus non-AI components for evidence collection, patch application, tests, review, commit, PR, notification, and repair records. There is no `SupervisorAgent` and no orchestration LLM call.

If a patched run fails the target tests, `AgenticPatchAgent.regeneratePatchFromTestFailure` receives the test stderr and rewrites the patch; `PatchApplyOperator` rolls back to the pre-apply file snapshot before the new patch is applied. The number of attempts is bounded by `REPAIR_MAX_PATCH_ATTEMPTS` (default 2).

The full competition loop has been verified end to end on the committed `demo/fault/quantity-division-by-zero` base branch. Latest real E2E session `pr-quantity-002` used the PR-safe one-click scenario API, read the standalone traceback, diagnosed `OrderService.calculateUnitPrice`, generated and applied a patch, passed all 5 target-service tests, passed review, pushed `repair/pr-quantity-002`, created [GitHub PR #3](https://github.com/deibudei/agent-aiOps/pull/3), sent the Feishu "fixed, please review" card, and wrote `repair-records/pr-quantity-002.json` / `.md` with `outcome=FIXED`. There is no non-LLM fallback; model configuration or invalid typed model output surfaces as repair `ERROR` events and writes a minimal error record.

## Current Implementation Status

- Done: structured evidence, typed `DiagnosisResult`, typed `RepairPlan`, typed `PatchProposal`, typed `RepairReflection`, LangChain4j `@Description` field hints, controlled atomic patch application, tests, review gates, repair records, repair timing and token observability, demo fault injection, one-click demo scenario orchestration, repair record indexing, role-specific model routing, OpenAI-compatible model configuration, DashScope configuration.
- Done: deterministic Java DAG implementation under `repair/agentic` with AI sub-agents in `repair/agentic/agents` and non-AI operators in `repair/agentic/operators`.
- Done: reflexion loop on test failure (rollback + regeneratePatchFromTestFailure with bounded attempts).
- Done: real GitHub PR creation through REST API (`GitHubRestPullRequestProvider`), with auto-detected owner/repo from the git origin remote.
- Done: Feishu v2 interactive card with title/summary/timing/token block, "View PR" button, and optional signing-secret support. Unknown token usage is shown as unknown instead of zero.
- Disabled by default: LLM repair, Git commit/push, GitHub PR creation, and Feishu notification (toggle with `REPAIR_LLM_ENABLED`, `REPAIR_GIT_ENABLED`, `REPAIR_GITHUB_ENABLED`, `FEISHU_ENABLED`).
- Next: run one validation per fault type through the scenario API, keep one real PR + Feishu golden path, and defer the frontend until the scenario API, SSE payloads, and repair-record index are stable.

The current mainline target service is repaired. Use the demo fault injection endpoints for local replay, or use the committed demo base branch described below for real PR demos.

## Demo PR Branch Model

`main` must stay in the repaired state. For a real PR demo, use a committed faulty base branch instead of injecting a temporary fault on `main`:

```text
main                                  repaired project state
demo/fault/quantity-division-by-zero  current main plus the committed division-by-zero fault
repair/{sessionId}                    Agent-created fix branch targeting the demo fault branch
```

Set `REPAIR_BASE_BRANCH=demo/fault/quantity-division-by-zero` when recording the PR demo. This makes the PR diff show only the Agent's repair from the faulty baseline back to the fixed code. The older `repair-demo-target` branch is no longer the primary competition-demo base because it may lag behind `main`.
PR-safe scenarios create the repair branch in a separate git worktree under `REPAIR_WORKTREE_ROOT`, so the main checkout can stay on `main` while the repair branch is patched, tested, committed, and pushed.

Runtime worktree model for PR-safe scenarios:

```text
1. Start agent-platform from main.
2. POST /api/demo/pr-scenarios/start creates repair/{sessionId}
   in REPAIR_WORKTREE_ROOT/{sessionId} from demo/fault/{faultType}.
3. Restart only target-service from that worktree path so it loads the faulty code.
4. POST /api/demo/pr-scenarios/{sessionId}/confirm-target-restarted.
5. The already-running agent-platform JVM continues using the main-compiled
   orchestration code while repair tools read/write the isolated worktree.
6. Tests pass -> commit -> push repair/{sessionId} -> create PR -> send Feishu.
```

Do not delete the active worktree, remove `repair/{sessionId}`, restart `agent-platform`, or edit docs/platform files while the repair workflow is running. Wait for the SSE `completed` event and the `repair-records/{sessionId}.json` / `.md` files before cleaning up disposable `repair/*` branches or worktrees.

Project work continues on `main` or on normal feature branches that merge back to `main`. Do not develop documentation or platform changes on `demo/fault/...` or `repair/{sessionId}` branches. After `main` changes, refresh the demo fault branch from the latest `main` and keep only the intentional fault commit on top; repair branches are disposable outputs from demo runs.

When intentionally refreshing the demo fault branch after `main` changes:

```powershell
git checkout main
git pull
git checkout -B demo/fault/quantity-division-by-zero main
# inject only the quantity-division-by-zero fault, then commit it
git push --force-with-lease origin demo/fault/quantity-division-by-zero
```

## Repair Timing And Token Observability

Repair timing and model usage are implemented without shortening the Agentic workflow. Diagnosis, planning, patch proposal, patch application, tests, review, commit, PR, Feishu notification, reflection, and record writing still run; timing and token data are collected alongside the workflow.

Implemented behavior:

- `RepairTiming` and `RepairStepTiming` capture total duration, per-step timing, model names, and token counts when a step calls a model.
- `RepairModelUsage` aggregates per-step model role, configured model, response model, call count, input tokens, output tokens, and total tokens.
- `RepairTimingCollector` records display timestamps with `Instant.now()` and calculates durations with `System.nanoTime()`.
- `AgenticRepairRunner` times Java DAG steps directly, so timing does not depend on LangChain4j listener delivery.
- `ObservedChatModel` wraps each role-specific `ChatModel` and records `ChatResponse.modelName()` plus `tokenUsage()` at the model boundary, before Agentic typed-output handling can drop response metadata.
- `RepairAgenticListener` only publishes Agentic lifecycle and tool-call events.
- Agentic agent/operator calls are timed, including evidence, diagnosis, plan, patch, test, review, commit, PR, notification, reflection, and record writing.
- Completed SSE events include `durationMillis`, `stepName`, and `modelUsage`.
- `repair-records/{sessionId}.json` includes a `timing` field with `modelUsage`, and `repair-records/{sessionId}.md` includes `Timing` and `Model Usage` tables. Some OpenAI-compatible providers may omit token usage; in that case token fields stay empty and the Feishu token line says the provider did not return token usage, instead of showing zero.

Validation:

- Run `mvn -pl agent-platform test`.
- Latest validation: `pr-quantity-002` on `demo/fault/quantity-division-by-zero` confirmed GitHub PR, Feishu card, SSE completion payload, JSON record, and Markdown record all show `outcome=FIXED`.

## LangChain4j

LangChain4j is used only for the AI sub-agents, not for orchestration or file writes:

- `RepairChatModelProvider`: lazily builds the configured OpenAI or DashScope `ChatModel`, with role-specific overrides for `diagnosis-model`, `plan-model`, `patch-model`, and `reflection-model`.
- `AgenticRepairRunner`: deterministic Java DAG; instantiates each AI sub-agent through `AgenticServices.agentBuilder(...)` and calls them sequentially, no `SupervisorAgent`.
- `AgenticDiagnosisAgent`, `AgenticPlanAgent`, `AgenticPatchAgent`, `AgenticReflectionAgent`: return typed records with LangChain4j `@Description` annotations. Diagnosis, plan, patch, and reflection outputs are validated by Java gates with one retry for invalid typed output.
- `PatchApplyOperator`: snapshots target files before each apply so the loop can roll back cleanly between attempts.
- `GitHubRestPullRequestProvider` + `GitRepoLocator`: create the PR via the GitHub REST API and resolve owner/repo from `git remote get-url origin`.
- `FeishuTools`: builds a Feishu v2 interactive card (header, summary, timing/token block, "View PR" button, footer) with optional HMAC-SHA256 signing. Successful and failed repair outcomes use different card titles/copy so failed attempts do not claim a fix was completed.
- `PatchTools` and `ToolPolicy`: remain the only controlled file-write path. Patch application is atomic: every operation is preflighted and every `oldText` must match exactly once before any file is written.
- AI sub-agents only receive read-only `@Tool` methods; patching, Git, GitHub, and Feishu are non-AI components guarded by existing policies and disabled-mode config.

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
REPAIR_LLM_ENABLED=false
REPAIR_LLM_PROVIDER=openai
REPAIR_LLM_MAX_TOKENS=4096
REPAIR_LLM_TIMEOUT_SECONDS=180
REPAIR_LLM_MAX_RETRIES=1
REPAIR_LLM_REFLECTION_MODEL=
REPAIR_MAX_PATCH_ATTEMPTS=2
TARGET_SERVICE_BASE_URL=http://localhost:9910
OPENAI_API_KEY=
OPENAI_BASE_URL=https://api.openai.com/v1
REPAIR_GIT_ENABLED=false
REPAIR_GIT_REMOTE=origin
REPAIR_BASE_BRANCH=demo/fault/quantity-division-by-zero
REPAIR_WORKTREE_ROOT=../agent-aiOps-worktrees
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

For repair runs, `REPAIR_LLM_ENABLED=true`, either `OPENAI_API_KEY` or `DASHSCOPE_API_KEY`, and a model in local config or environment variables are required. If the model is unavailable or returns invalid typed `DiagnosisResult`/`RepairPlan`/`PatchProposal`/`RepairReflection` objects twice, the workflow publishes an `ERROR` event and writes a minimal error record instead of using a deterministic fallback. For OpenAI-compatible endpoints such as DeepSeek or Qwen, increase `REPAIR_LLM_TIMEOUT_SECONDS` if provider calls time out. Keep `REPAIR_LLM_MAX_RETRIES` low during demos so a slow model call does not stall the whole repair loop. For complex faults, keep `REPAIR_LLM_MAX_TOKENS` at `4096` or higher. The DAG trims traceback, source context, and tool event payloads before sending them to the AI sub-agents. The highest-value role-specific override is usually `repair.llm.patch-model`, since the patch agent both generates and rewrites code; `diagnosis-model`, `plan-model`, and `reflection-model` can stay on the default unless faults are ambiguous.

For real PR creation, set `REPAIR_GITHUB_ENABLED=true` and provide `GITHUB_TOKEN`. For a fine-grained personal access token, grant repository access to `deibudei/agent-aiOps` and set `Contents: Read and write` plus `Pull requests: Read and write`; read-only PR permission causes GitHub HTTP 403 during PR creation. Owner/repo are auto-detected from `git remote get-url origin`; override them with `REPAIR_GITHUB_OWNER` / `REPAIR_GITHUB_REPO` when running outside a checkout. The PR base branch defaults to `demo/fault/quantity-division-by-zero`; create/push that branch from current `main` with only the demo fault committed so each repair PR opens against a clean faulty baseline instead of `main`. `REPAIR_WORKTREE_ROOT` must point outside the main checkout.

For real Feishu delivery, set `FEISHU_ENABLED=true`, `FEISHU_WEBHOOK_URL`, and (if the bot enforces signature verification) `FEISHU_SIGNING_SECRET`. The card embeds the PR URL as a button and shows total duration plus token usage when the provider returns usage metadata; if usage is unavailable, the card explicitly says so.

Repair records and completed SSE events include `outcome=FIXED|FAILED|ERROR` and `outcomeReason`. Controlled failures such as patch/test/review/PR failure complete with `outcome=FAILED`; runtime workflow exceptions publish `ERROR` and write a minimal error record.

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

To test the Agentic workflow locally, keep the key only in `application-local.yml` or environment variables and set:

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
    reflection-model: deepseek-v4-flash
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

## One-Click Demo Scenario API

The scenario API turns the manual competition flow into a backend state machine while keeping source-level fault injection. It still pauses for an operator restart because injected Java source is not hot-reloaded by the running `target-service` process.

Source-injection scenarios intentionally dirty the local working tree, so they require `REPAIR_GIT_ENABLED=false`. Use them for local repair/test/record/Feishu validation. For a real GitHub PR demo, use the committed `demo/fault/...` base branch and the PR-safe scenario API below.

```powershell
$body = @{ sessionId = "scenario-quantity-001"; faultType = "quantity-division-by-zero" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/demo/scenarios/start" -ContentType "application/json" -Body $body

# Restart target-service after the response says WAITING_FOR_TARGET_RESTART.
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/demo/scenarios/scenario-quantity-001/confirm-target-restarted"

Invoke-RestMethod -Uri "http://localhost:9901/api/demo/scenarios/scenario-quantity-001"
```

`quantity-division-by-zero` triggers the running target service through `TARGET_SERVICE_BASE_URL` and expects a fresh HTTP 500 traceback. `wrong-quote-route` and `wrong-error-status` run one target-service test pass and write the failing output as the latest evidence log so stale traceback files do not mislead diagnosis. The confirm call starts the repair workflow and returns the SSE URL.

### One-Click Demo + PR

Use the PR-safe scenario API when the demo must also create a GitHub PR. This path does not inject source into the current working tree and does not switch the main checkout. It prepares `repair/{sessionId}` in an isolated worktree from the configured committed fault base branch, waits for a manual target-service restart from that worktree, then starts the normal repair workflow inside that worktree.

Requirements:

- `REPAIR_GIT_ENABLED=true`
- `REPAIR_GITHUB_ENABLED=true`
- `REPAIR_BASE_BRANCH=demo/fault/{faultType}`
- `REPAIR_WORKTREE_ROOT` points outside the main checkout.
- A committed fault base branch exists locally or on the configured remote.

Fault-to-branch mapping:

```text
quantity-division-by-zero -> demo/fault/quantity-division-by-zero
wrong-quote-route         -> demo/fault/wrong-quote-route
wrong-error-status        -> demo/fault/wrong-error-status
```

```powershell
$env:REPAIR_GIT_ENABLED="true"
$env:REPAIR_GITHUB_ENABLED="true"
$env:REPAIR_BASE_BRANCH="demo/fault/quantity-division-by-zero"

$body = @{ sessionId = "pr-quantity-001"; faultType = "quantity-division-by-zero" } | ConvertTo-Json
$scenario = Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/demo/pr-scenarios/start" -ContentType "application/json" -Body $body

# Restart target-service from $scenario.worktreePath after WAITING_FOR_TARGET_RESTART.
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/demo/pr-scenarios/pr-quantity-001/confirm-target-restarted"
```

The target-service terminal should run from the returned worktree path:

```powershell
Push-Location $scenario.worktreePath
mvn -pl target-service spring-boot:run
```

Watch the repair stream from another terminal:

```powershell
curl.exe -N "http://localhost:9901/api/repair/stream/pr-quantity-001"
```

The successful completion should report `outcome=FIXED`, a pushed `repair/pr-quantity-001` branch, a GitHub PR URL, a Feishu success card, and `repair-records/pr-quantity-001.json` / `.md`.

For the other two faults, first create and push `demo/fault/wrong-quote-route` and `demo/fault/wrong-error-status` with only that fault committed. Then restart `agent-platform` with the matching `REPAIR_BASE_BRANCH` before calling `/api/demo/pr-scenarios/start`.

Repair record summaries are exposed for future frontend and experiment views:

```powershell
Invoke-RestMethod -Uri "http://localhost:9901/api/repair/records"
```

Keep real API keys in environment variables only. `.env`, `.env.*`, `*.secret`, `local-secrets/`, and `local-reports/` are ignored.
