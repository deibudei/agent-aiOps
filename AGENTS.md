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
- `target-service` intentionally contains a failing validation bug before repair.
- `agent-platform` is the Agent backend.
- Frontend is intentionally deferred until backend flow is stable.
- GitHub PR and Feishu are implemented but disabled by default.
- Repair records should be written under repo-root `repair-records/`.
- If records appear under `agent-platform/repair-records/`, the running backend is stale or the workspace root was misdetected; restart `agent-platform` from repo root.
- Keep `README.zh-CN.md`, `README.md`, and this `AGENTS.md` updated whenever project architecture, commands, environment variables, demo flow, or Agent capability status changes.
- Current Agent maturity: the workflow is wired, but planning/execution is still scenario-specific and should be upgraded into a real LLM/tool-driven repair Agent.

## Local Skill Setup

Useful local skills for the next phase:

- Preinstalled/available: `openai-docs`, `skill-creator`, `mcp-builder`, GitHub plugin skills, `webapp-testing`.
- Installed on 2026-04-25 from `openai/skills` curated: `gh-address-comments`, `gh-fix-ci`, `yeet`, `sentry`, `security-threat-model`.
- `superpowers` was requested on 2026-04-25, but the local path is a broken junction to `C:\Users\wuyib\.codex\superpowers`; do not rely on it until that target is restored.
- Restart Codex after installing new skills so newly installed skills are picked up by the session.

## Next Phase: Real Agent Repair

Replace the hard-coded repair path with a true Agent loop:

- Read traceback/log evidence and failing tests.
- Ask an LLM to produce a structured root-cause analysis and repair plan.
- Let the Agent choose tools from a strict registry instead of hard-coding `OrderService`.
- Generate a structured patch proposal, validate paths through `ToolPolicy`, apply it, and run tests.
- Review diff/test output with policy checks before GitHub PR and Feishu notification.
- Persist repair records and reflection for future retrieval/RAG.

Implementation order:

1. Define the Agent contract: `EvidenceBundle`, `RepairAnalysis`, `RepairPlan`, `PatchProposal`, and strict JSON schemas.
2. Add `LlmClient` for DashScope/Qwen HTTP calls, with timeout, retry, disabled-mode behavior, and structured JSON parsing.
3. Replace `RepairPlannerAgent` hard-coded logic with LLM analysis based on traceback, failing tests, and selected source snippets.
4. Replace `RepairExecutorAgent` hard-coded string replacement with patch proposal application through `PatchTools` and `ToolPolicy`.
5. Strengthen review gates: reject unparseable model output, empty diff, out-of-whitelist paths, failing tests, and changes outside `target-service/src/main` or `target-service/src/test`.
6. Update repair records to include model input summary, model output, patch proposal, retry history, final diff, and reflection.
7. Keep a deterministic demo reset path, preferably a `demo-bug` branch or documented manual reset, so the competition demo can be replayed.

## Modules

- `agent-platform`
  - Spring Boot Agent backend.
  - Exposes `POST /api/repair/run`.
  - Exposes `GET /api/repair/stream/{sessionId}` for SSE.
  - Implements Plan-Execute-Review-Reflect workflow.

- `target-service`
  - Spring Boot service under repair.
  - Demo bug: `quantity=0` causes `/ by zero` in `OrderService`.
  - Tests are expected to fail before repair and pass after repair.

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

SSE stream:

```text
GET http://localhost:9901/api/repair/stream/demo-001
```

## Verification

Agent backend:

```powershell
mvn -pl agent-platform test
```

Target service before repair:

```powershell
mvn -pl target-service test
```

This should fail before repair because the bug is intentional.

Compile target service without running the intentional failing tests:

```powershell
mvn -pl target-service -DskipTests package
```

## Safety Rules

- Repair tools should only read `target-service/src` and `target-service/logs`.
- Repair tools should only write `target-service/src/main` and `target-service/src/test`.
- Do not let repair tools modify `agent-platform`, root configs, secrets, or scripts.
- Keep GitHub and Feishu disabled by default unless demo credentials are configured.
