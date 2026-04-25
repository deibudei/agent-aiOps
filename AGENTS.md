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
