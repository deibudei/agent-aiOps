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
