# Agent AI Ops

基于 Java 的服务自动化修复 Agent 演示项目。

## 项目模块

- `agent-platform`：自动修复 Agent 平台，负责读取异常、规划修复、修改代码、运行测试、创建 PR、发送飞书通知和生成修复记录。
- `target-service`：被监控和被修复的 Spring Boot 示例服务，内置一个参数校验类 bug，用于比赛演示。

## 核心流程

```text
服务报错
-> 读取 Traceback
-> 生成修复计划
-> 读取代码
-> 修改代码
-> 运行测试
-> 审查 diff
-> 创建 GitHub PR
-> 发送飞书通知
-> 反思沉淀
-> 生成修复记录
```

第一版采用手动按钮/API 触发，GitHub PR 和飞书通知默认关闭。录制比赛演示时，可以通过环境变量开启真实 PR 和真实飞书卡片。

## 启动服务

所有命令都建议在项目根目录运行：

```powershell
cd D:\java_web_project\agent-aiOps
```

需要开两个终端窗口。

终端 1：启动被修复服务：

```powershell
mvn -pl target-service spring-boot:run
```

终端 2：启动 Agent 平台：

```powershell
mvn -pl agent-platform spring-boot:run
```

默认端口：

- `target-service`：`http://localhost:9910`
- `agent-platform`：`http://localhost:9901`

## 触发演示 Bug

这个命令在 **新的 PowerShell 终端** 里运行即可。

运行前需要确保：

- 终端 1 里的 `target-service` 已经启动成功。
- 能访问 `http://localhost:9910`。

命令本身是 HTTP 请求，所以理论上在哪个目录运行都可以；为了统一，建议仍然先进入项目根目录：

```powershell
cd D:\java_web_project\agent-aiOps
```

调用下面的接口会触发 `quantity=0` 参数校验缺失问题，当前版本会产生 `/ by zero` 异常：

```powershell
Invoke-WebRequest "http://localhost:9910/api/orders/quote?totalCents=100&quantity=0"
```

异常日志会写入：

```text
target-service/logs/target-service.log
```

## 触发自动修复

这个命令也在 **新的 PowerShell 终端** 里运行。

运行前需要确保：

- 终端 1：`target-service` 正在运行。
- 终端 2：`agent-platform` 正在运行。
- 已经调用过上面的 Bug 触发接口，或 `target-service/logs/target-service.log` 中已经有异常日志。

```powershell
$body = @{ sessionId = "demo-001" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/repair/run" -ContentType "application/json" -Body $body
```

查看 SSE 流式事件：

```text
GET http://localhost:9901/api/repair/stream/demo-001
```

事件阶段包括：

```text
detecting
planning
executing
patching
testing
reviewing
committing
pr_created
notified
reflecting
completed
error
```

## 环境变量

```text
DASHSCOPE_API_KEY=
DASHSCOPE_MODEL=qwen-max

REPAIR_GIT_ENABLED=false
REPAIR_GITHUB_ENABLED=false
REPAIR_GIT_REMOTE=origin
REPAIR_BASE_BRANCH=main

FEISHU_ENABLED=false
FEISHU_WEBHOOK_URL=
```

说明：

- `REPAIR_GIT_ENABLED=false` 时，不会创建分支、commit 或 push。
- `REPAIR_GITHUB_ENABLED=false` 时，不会调用 `gh CLI` 创建 PR。
- `FEISHU_ENABLED=false` 时，不会发送飞书卡片。
- 开启真实 PR 前，需要先在本机完成 `gh auth login`。

## 测试命令

Agent 平台测试：

```powershell
mvn -pl agent-platform test
```

被修复服务测试：

```powershell
mvn -pl target-service test
```

注意：`target-service` 的测试在修复前会失败，这是刻意设计的演示 bug。Agent 修复 `OrderService` 后，该测试应通过。

如果只想确认 `target-service` 能编译打包，可以跳过测试：

```powershell
mvn -pl target-service -DskipTests package
```

## 修复记录

每次自动修复都会生成记录：

```text
repair-records/{sessionId}.json
repair-records/{sessionId}.md
```

记录内容包括：

- 异常摘要
- 修复计划
- 工具执行步骤
- diff 摘要
- 测试结果
- Review 结论
- PR 结果
- 飞书通知结果
- Agent 反思沉淀

## 新终端快速恢复上下文

如果以后重新打开终端或重新开始对话，先看这两个文件：

```text
README.zh-CN.md
AGENTS.md
```

最短操作顺序：

```powershell
cd D:\java_web_project\agent-aiOps

# 终端 1
mvn -pl target-service spring-boot:run

# 终端 2
mvn -pl agent-platform spring-boot:run

# 终端 3：触发 bug
Invoke-WebRequest "http://localhost:9910/api/orders/quote?totalCents=100&quantity=0"

# 终端 3：触发修复
$body = @{ sessionId = "demo-001" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:9901/api/repair/run" -ContentType "application/json" -Body $body
```

后续如果接入 GitHub PR 和飞书，把环境变量改成：

```powershell
$env:REPAIR_GIT_ENABLED="true"
$env:REPAIR_GITHUB_ENABLED="true"
$env:FEISHU_ENABLED="true"
$env:FEISHU_WEBHOOK_URL="你的飞书 webhook"
```

## 安全边界

自动修复工具采用强白名单：

- 只读取 `target-service/src` 和 `target-service/logs`
- 只允许修改 `target-service/src/main` 和 `target-service/src/test`
- 不允许修改 `agent-platform`
- 不允许修改根配置、密钥配置或任意脚本
- 测试命令固定为目标服务测试

## 扩展点

当前代码已预留：

- `RepairTrigger`：后续可扩展日志轮询、GitHub Webhook、CI 失败触发。
- `TestRunner`：后续可扩展 Gradle、pytest、npm test。
- `PullRequestProvider`：后续可替换为 GitHub REST API。
- `ReviewPolicy`：后续可拆分安全审查、回归风险审查、测试覆盖审查。
- `RepairRecord`：后续可接入 RAG/Milvus，沉淀历史修复经验。
