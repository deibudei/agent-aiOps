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

The repair flow is:

```text
detect -> plan -> execute -> patch -> test -> review -> commit -> PR -> Feishu -> reflect -> record
```

GitHub PR and Feishu delivery are implemented but disabled by default. Enable them with environment variables when recording the competition demo.

The target service keeps the normal application log at `target-service/logs/target-service.log`. Unexpected runtime failures are also written as separate files under `target-service/logs/tracebacks/traceback-{timestamp}-{traceId}.log`, and the Agent reads the `target-service/logs` directory to pick the latest traceback.

## Current Agent Maturity

The backend loop is wired and now includes an optional LangChain4j Agentic Supervisor path. When `REPAIR_AGENTIC_ENABLED=true`, a Supervisor coordinates AI agents for diagnosis, planning, and patch proposal plus non-AI agents for evidence collection, patch application, tests, review, commit, PR, notification, reflection, and repair records.

The Agentic flow has been verified end to end on the `quantity-division-by-zero` demo fault: it read a standalone traceback, diagnosed `OrderService.calculateUnitPrice`, generated and applied a patch, ran `mvn -pl target-service test`, passed review, skipped disabled GitHub/Feishu actions safely, and wrote a repair record. The old stable path remains as fallback.

## Current Implementation Status

- Done: structured evidence, repair plan, patch proposal, controlled patch application, tests, review gates, repair records, demo fault injection, OpenAI-compatible/Qwen configuration, and optional LangChain4j Agentic orchestration.
- Done: Agentic implementation split into `repair/agentic`, `repair/agentic/agents`, and `repair/agentic/operators` so the runner only assembles the supervisor.
- Still intentionally disabled by default: Git commit/push, GitHub PR creation, and Feishu notification.
- Next: add repair timing observability without shortening the current Agentic workflow, then broaden repair scenarios beyond the first validation bug, add more Agentic-level tests, improve repair record retrieval/knowledge reuse, and add a frontend workbench after the backend flow stays stable.

The current mainline target service is repaired. Use the demo fault injection endpoints to switch it into a known failure state before testing automatic repair.

## Next Plan: Repair Timing Observability

The next implementation step is to measure how long one bug repair takes while keeping the full workflow intact. This means the Agentic Supervisor, diagnosis, planning, patch proposal, patch application, tests, review, commit, PR, Feishu notification, reflection, and record writing will still run; the change only adds timing data.

Planned changes:

- Add `RepairTiming` and `RepairStepTiming` for total duration and per-step timing.
- Add an internal `RepairTimingCollector` that records display timestamps with `Instant.now()` and calculates durations with `System.nanoTime()`.
- Time the Agentic operators for evidence, diagnosis, plan, patch, test, review, commit, PR, notification, reflection, and record writing.
- Add coarse-grained timing to the legacy fallback workflow so both paths write the same repair record shape.
- Add `durationMillis` and `stepName` to completed SSE event details.
- Add `timing` to `repair-records/{sessionId}.json` and a `Timing` table to `repair-records/{sessionId}.md`.

Validation:

- Run `mvn -pl agent-platform test`.
- Inject `quantity-division-by-zero`, run one Agentic repair, and confirm SSE, JSON, and Markdown records all include total and per-step duration.

## LangChain4j

LangChain4j is used for model reasoning and optional agentic orchestration, not for unrestricted file writes:

- `RepairChatModelProvider`: lazily builds the configured OpenAI or DashScope `ChatModel`.
- `LangChainRepairPlanner`: requests strict JSON `RepairPlan`.
- `LangChainPatchPlanner`: requests strict JSON `PatchProposal`.
- `AgenticRepairRunner`: builds the LangChain4j Agentic Supervisor when `REPAIR_AGENTIC_ENABLED=true`; implementation details are split under `repair/agentic/agents` and `repair/agentic/operators`.
- `StructuredJsonParser`: extracts and rejects invalid model JSON.
- `PatchTools` and `ToolPolicy`: remain the only controlled file-write path.
- Agentic AI agents only receive read-only `@Tool` methods; patching, Git, GitHub, and Feishu are non-AI agents guarded by existing policies and disabled-mode config.

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

Trigger the demo bug:

```powershell
Invoke-WebRequest "http://localhost:9910/api/orders/quote?totalCents=100&quantity=0"
```

Useful log settings:

```text
REPAIR_TARGET_LOG=target-service/logs
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
REPAIR_LLM_TIMEOUT_SECONDS=90
REPAIR_LLM_MAX_RETRIES=1
REPAIR_AGENTIC_ENABLED=false
REPAIR_AGENTIC_MAX_SUPERVISOR_INVOCATIONS=24
OPENAI_API_KEY=
OPENAI_MODEL=gpt-4o-mini
OPENAI_BASE_URL=https://api.openai.com/v1
REPAIR_GIT_ENABLED=false
REPAIR_GITHUB_ENABLED=false
FEISHU_ENABLED=false
FEISHU_WEBHOOK_URL=
```

For OpenAI-compatible Qwen endpoints, increase `REPAIR_LLM_TIMEOUT_SECONDS` if provider calls time out. Keep `REPAIR_LLM_MAX_RETRIES` low during demos so a slow model call does not stall the whole repair loop. For more complex faults, keep `REPAIR_LLM_MAX_TOKENS` at `4096` or higher so patch JSON is not truncated. The Agentic path trims traceback, source context, and SSE tool events before sending them through the supervisor.

## Local Profile

Commit-safe config stays in:

```text
agent-platform/src/main/resources/application.yml
```

Local secrets go in the gitignored file:

```text
agent-platform/src/main/resources/application-local.yml
```

Run with:

```powershell
mvn -pl agent-platform spring-boot:run "-Dspring-boot.run.profiles=local"
```

To test the Agentic Supervisor locally, keep the key only in `application-local.yml` or environment variables and set:

```yaml
repair:
  llm:
    enabled: true
    provider: openai
  agentic:
    enabled: true
```

## Demo Fault Injection

The Agent platform can inject local demo faults into fixed target-service source files:

```powershell
Invoke-RestMethod -Uri "http://localhost:9901/api/demo/faults"
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/demo/faults/quantity-division-by-zero/inject"
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/demo/faults/reset"
```

Supported fault types:

- `quantity-division-by-zero`
- `wrong-quote-route`
- `wrong-error-status`

Keep real API keys in environment variables only. `.env`, `.env.*`, `*.secret`, `local-secrets/`, and `local-reports/` are ignored.
