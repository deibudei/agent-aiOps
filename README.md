# Agent AI Ops

[中文说明](README.zh-CN.md)

Java implementation of a service auto-repair Agent demo.

## Modules

- `agent-platform`: Spring Boot repair Agent platform.
- `target-service`: Spring Boot service under repair.

## Demo Flow

1. Start the target service.
2. Trigger the validation bug so the service writes a Java traceback.
3. Start the Agent platform.
4. Call `POST /api/repair/run`.
5. Watch repair events from `GET /api/repair/stream/{sessionId}`.

The repair flow is:

```text
detect -> plan -> execute -> patch -> test -> review -> commit -> PR -> Feishu -> reflect -> record
```

GitHub PR and Feishu delivery are implemented but disabled by default. Enable them with environment variables when recording the competition demo.

## Current Agent Maturity

The backend loop is wired, but the current planner/executor is still scenario-specific for the `OrderService` validation bug. The next phase is to replace that hard-coded path with an LLM + tool-registry repair loop that derives the root cause, proposes a structured patch, applies it through `ToolPolicy`, runs tests, reviews the diff, and then creates the PR/notification/record.

## Next Implementation Plan

Build the real Agent as a vertical MVP before adding frontend or new triggers:

1. Define structured contracts: `EvidenceBundle`, `RepairAnalysis`, `RepairPlan`, and `PatchProposal`.
2. Add a DashScope/Qwen `LlmClient` with timeout, retry, disabled-mode behavior, and strict JSON parsing.
3. Collect traceback, failing tests, candidate files, and source snippets into the evidence bundle.
4. Replace hard-coded planner logic with LLM-generated root cause, suspected files, steps, and test strategy.
5. Replace hard-coded executor string replacement with LLM-generated patch proposals applied only through `PatchTools` and `ToolPolicy`.
6. Enforce hard review gates: invalid JSON, empty diff, out-of-whitelist paths, and failing tests must block commit/PR/notification.
7. Extend repair records with model input summary, model output, patch proposal, retry history, final diff, and reflection.
8. Keep a repeatable demo reset path, preferably a `demo-bug` branch.

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

Run the repair:

```powershell
$body = @{ sessionId = "demo-001" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/repair/run" -ContentType "application/json" -Body $body
```

The target service test is intentionally red before the repair. After the repair patches `OrderService`, this should pass:

```powershell
mvn -pl target-service test
```

## Environment

```text
DASHSCOPE_API_KEY=
REPAIR_GIT_ENABLED=false
REPAIR_GITHUB_ENABLED=false
FEISHU_ENABLED=false
FEISHU_WEBHOOK_URL=
```
