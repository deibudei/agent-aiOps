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

The backend loop is wired and now includes an optional LangChain4j Agentic Supervisor path. When `REPAIR_AGENTIC_ENABLED=true`, a Supervisor coordinates AI agents for diagnosis, planning, and patch proposal plus non-AI agents for evidence collection, patch application, tests, review, commit, PR, notification, reflection, and repair records. The old stable path remains as fallback.

## Next Implementation Plan

Build the real Agent as a vertical MVP before adding frontend or new triggers:

1. Define structured contracts: `EvidenceBundle`, `RepairAnalysis`, `RepairPlan`, and `PatchProposal`.
2. Use LangChain4j OpenAI integration with disabled-mode behavior and strict JSON parsing.
3. Collect traceback, failing tests, candidate files, and source snippets into the evidence bundle.
4. Replace hard-coded planner logic with OpenAI-generated root cause, suspected files, steps, and test strategy.
5. Replace hard-coded executor string replacement with OpenAI-generated patch proposals applied only through `PatchTools` and `ToolPolicy`.
6. Enforce hard review gates: invalid JSON, empty diff, out-of-whitelist paths, and failing tests must block commit/PR/notification. Started.
7. Extend repair records with model input summary, model output, patch proposal, retry history, final diff, and reflection. Started.
8. Keep a repeatable demo reset path, preferably a `demo-bug` branch.

## LangChain4j

LangChain4j is used for model reasoning and optional agentic orchestration, not for unrestricted file writes:

- `RepairChatModelProvider`: lazily builds the configured OpenAI or DashScope `ChatModel`.
- `LangChainRepairPlanner`: requests strict JSON `RepairPlan`.
- `LangChainPatchPlanner`: requests strict JSON `PatchProposal`.
- `AgenticRepairRunner`: builds the LangChain4j Agentic Supervisor when `REPAIR_AGENTIC_ENABLED=true`.
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

The current mainline target service is already repaired, so this should pass. Use the future `demo-bug` branch or reset step to reproduce the pre-repair failure:

```powershell
mvn -pl target-service test
```

## Environment

```text
REPAIR_LLM_ENABLED=false
REPAIR_LLM_PROVIDER=openai
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

For OpenAI-compatible Qwen endpoints, increase `REPAIR_LLM_TIMEOUT_SECONDS` if provider calls time out. Keep `REPAIR_LLM_MAX_RETRIES` low during demos so a slow model call does not stall the whole repair loop. The Agentic path trims traceback, source context, and SSE tool events before sending them through the supervisor.

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
mvn -pl agent-platform spring-boot:run -Dspring-boot.run.profiles=local
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
