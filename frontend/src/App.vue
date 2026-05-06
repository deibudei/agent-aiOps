<script setup lang="ts">
import type { Component } from 'vue';
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import {
  Activity,
  Bell,
  BrainCircuit,
  ChevronDown,
  ChevronRight,
  CheckCircle2,
  CircleAlert,
  ClipboardCheck,
  Code2,
  ExternalLink,
  FileDiff,
  FileSearch,
  FileText,
  FlaskConical,
  GitBranch,
  GitPullRequest,
  History,
  ListChecks,
  MessagesSquare,
  Moon,
  Play,
  Radio,
  RefreshCw,
  RotateCw,
  ShieldCheck,
  Sun,
  TerminalSquare,
  Wrench,
} from 'lucide-vue-next';

import { useRepairApi } from '@/composables/useApi';
import { repairStages, useRepairStream } from '@/composables/useRepairStream';
import type {
  DemoFaultResult,
  DemoPrScenarioReadiness,
  DemoScenarioResult,
  DemoTargetRestartResult,
  FaultType,
  RepairEvent,
  RepairDiffFile,
  RepairDiffHunk,
  RepairDiffLine,
  RepairRecord,
  RepairRecordSummary,
  RepairStage,
} from '@/types';

type ActiveView = 'run' | 'tools' | 'artifacts' | 'records' | 'score';
type ChatRole = 'system' | 'agent' | 'tool';
type DiffViewMode = 'unified' | 'split';

interface FaultMeta {
  type: FaultType;
  label: string;
  shortLabel: string;
  priority: 'P1' | 'P2';
  description: string;
  evidence: string;
  baseBranch: string;
}

interface StageDef {
  stage: RepairStage;
  label: string;
  description: string;
  icon: Component;
}

interface NavItem {
  id: ActiveView;
  label: string;
  description: string;
  icon: Component;
}

interface ToolEventView {
  id: string;
  event: RepairEvent;
  order: number;
  toolName: string;
  action: string;
  target: string;
  status: string;
  success: boolean | null;
  summary: string;
  durationMillis: number | null;
}

interface SplitDiffRow {
  left: RepairDiffLine | null;
  right: RepairDiffLine | null;
}

interface ChatItem {
  id: string;
  role: ChatRole;
  badgeLabel: string;
  title: string;
  body: string;
  meta: string;
  status: string;
  order?: number;
  durationMillis?: number | null;
  event?: RepairEvent;
  tool?: ToolEventView;
  plan?: DisplayPlan;
  patch?: DisplayPatch;
}

interface WorkbenchStage {
  id: string;
  label: string;
  stages: RepairStage[];
  icon: Component;
}

interface ReadinessCheck {
  label: string;
  ok: boolean;
}

interface ServiceStatusRow {
  service: string;
  instance: string;
  status: string;
  lastRestart: string;
}

interface SseRawLine {
  number: number;
  text: string;
}

interface TokenTotals {
  input: number | null;
  output: number | null;
  total: number | null;
}

interface DisplayPlan {
  rootCause: string;
  repairTarget: string;
  files: string[];
  steps: string[];
  testCommand: string;
}

interface DisplayPatch {
  summary: string;
  operations: DisplayPatchOperation[];
}

interface DisplayPatchOperation {
  filePath: string;
  reason: string;
  lineSummary: string;
}

interface PersistedState {
  activeView?: ActiveView;
  selectedFault?: FaultType;
  sessionId?: string;
  darkMode?: boolean;
  runToolTraceCollapsed?: boolean;
  scenario?: DemoScenarioResult | null;
  targetRestart?: DemoTargetRestartResult | null;
  currentRecord?: RepairRecord | null;
  selectedRecord?: RepairRecord | null;
  events?: RepairEvent[];
}

const STORAGE_KEY = 'agent-aiops-console-state-v2';

const faultMetas: FaultMeta[] = [
  {
    type: 'quantity-division-by-zero',
    label: '除零运行时异常',
    shortLabel: '除零',
    priority: 'P1',
    description: 'quantity=0 绕过校验，目标服务产生 / by zero Traceback。',
    evidence: 'HTTP 500 + traceback 文件',
    baseBranch: 'demo/fault/quantity-division-by-zero',
  },
  {
    type: 'wrong-quote-route',
    label: '报价路由漂移',
    shortLabel: '路由',
    priority: 'P2',
    description: 'Controller 路径变更导致 quote 接口测试返回 404。',
    evidence: '失败测试输出写入 tracebacks',
    baseBranch: 'demo/fault/wrong-quote-route',
  },
  {
    type: 'wrong-error-status',
    label: '错误状态码回归',
    shortLabel: '状态码',
    priority: 'P2',
    description: '参数校验错误被错误包装为 HTTP 500，而不是 HTTP 400。',
    evidence: '失败测试输出写入 tracebacks',
    baseBranch: 'demo/fault/wrong-error-status',
  },
  {
    type: 'precision-loss',
    label: '折扣金额精度丢失',
    shortLabel: '精度',
    priority: 'P1',
    description: 'double 计算折扣导致浮点精度误差，预期 199.96 得到 199.96000000000004。',
    evidence: '失败测试输出写入 tracebacks',
    baseBranch: 'demo/fault/precision-loss',
  },
  {
    type: 'race-condition',
    label: '库存并发竞态超卖',
    shortLabel: '并发',
    priority: 'P1',
    description: '库存扣减无锁保护，并发请求导致超卖（预期扣 10 剩 0，实际剩 2-3）。',
    evidence: '失败测试输出写入 tracebacks',
    baseBranch: 'demo/fault/race-condition',
  },
  {
    type: 'path-traversal',
    label: '文件下载路径遍历',
    shortLabel: '安全',
    priority: 'P1',
    description: '未校验文件名中的 ../ 序列，攻击者可读取任意文件。',
    evidence: '失败测试输出写入 tracebacks',
    baseBranch: 'demo/fault/path-traversal',
  },
];

const stageDefs: StageDef[] = [
  { stage: 'detecting', label: 'Traceback', description: 'ReadLog 收集日志证据', icon: Radio },
  { stage: 'planning', label: 'AI 根因与计划', description: 'Diagnosis / Plan Agent', icon: BrainCircuit },
  { stage: 'executing', label: 'Agent 编排', description: 'Java DAG + AI 子 Agent', icon: Activity },
  { stage: 'patching', label: '自动补丁', description: 'Patch Agent 生成或重写', icon: Code2 },
  { stage: 'testing', label: 'Run Test', description: 'target-service regression tests', icon: FlaskConical },
  { stage: 'reviewing', label: '安全审查', description: '路径、diff、测试门禁', icon: ShieldCheck },
  { stage: 'committing', label: 'Git Commit', description: 'repair/{sessionId}', icon: GitBranch },
  { stage: 'pr_created', label: '创建 PR', description: 'GitHub REST API', icon: GitPullRequest },
  { stage: 'notified', label: '飞书通知', description: 'v2 interactive card', icon: Bell },
  { stage: 'reflecting', label: '反思沉淀', description: 'Reflection Agent', icon: FileText },
  { stage: 'completed', label: '闭环完成', description: '记录 outcome 与指标', icon: CheckCircle2 },
  { stage: 'error', label: '异常终止', description: '写入 ERROR 记录', icon: CircleAlert },
];

const navItems: NavItem[] = [
  { id: 'run', label: 'Run', description: '会话与 ChatOps', icon: MessagesSquare },
  { id: 'tools', label: 'Tool Trace', description: '工具审计日志', icon: Wrench },
  { id: 'artifacts', label: 'Artifacts', description: '根因、测试、反思', icon: FileText },
  { id: 'records', label: 'Records', description: '修复记录归档', icon: History },
];

const workbenchStages: WorkbenchStage[] = [
  { id: 'locate', label: '定位', stages: ['detecting'], icon: Radio },
  { id: 'analyze', label: '分析', stages: ['planning'], icon: BrainCircuit },
  { id: 'repair', label: '修复', stages: ['executing', 'patching'], icon: Code2 },
  { id: 'verify', label: '验证', stages: ['testing', 'reviewing'], icon: FlaskConical },
  { id: 'submit-pr', label: '提交 PR', stages: ['committing', 'pr_created', 'notified'], icon: GitPullRequest },
  { id: 'done', label: '完成', stages: ['reflecting', 'completed'], icon: CheckCircle2 },
];

const fallbackFaults: DemoFaultResult[] = faultMetas.map((fault) => ({
  faultType: fault.type,
  success: true,
  message: fault.description,
  changedFiles: [],
  nextSteps: [],
}));

const api = useRepairApi();
const stream = useRepairStream();

const activeView = ref<ActiveView>('run');
const selectedFault = ref<FaultType>('quantity-division-by-zero');
const sessionId = ref(generateSessionId(selectedFault.value));
const faults = ref<DemoFaultResult[]>(fallbackFaults);
const readiness = ref<DemoPrScenarioReadiness | null>(null);
const scenario = ref<DemoScenarioResult | null>(null);
const targetRestart = ref<DemoTargetRestartResult | null>(null);
const records = ref<RepairRecordSummary[]>([]);
const currentRecord = ref<RepairRecord | null>(null);
const selectedRecord = ref<RepairRecord | null>(null);
const errorMessage = ref('');
const copyStatus = ref('');
const loadingFaults = ref(false);
const loadingReadiness = ref(false);
const loadingRecords = ref(false);
const runningAction = ref(false);
const autoRestartStatus = ref<'idle' | 'running' | 'done' | 'failed'>('idle');
const selectedChatItemId = ref('');
const collapsedDiffFiles = ref<Record<string, boolean>>({});
const selectedDiffFilePath = ref('');
const diffViewMode = ref<DiffViewMode>('split');
const sseCopyStatus = ref('');
const nowMillis = ref(Date.now());
const darkMode = ref(false);
const runToolTraceCollapsed = ref(false);
const chatFeedRef = ref<HTMLDivElement | null>(null);
const planTransitionReady = ref(false);
const visiblePlanSegments = ref(0);
const activePlanRevealKey = ref('');

let restoringPersistedState = true;
let skipNextFaultReset = false;
let clockTimer: number | undefined;
let planRevealTimer: number | undefined;

const activeFault = computed(() => faultMetas.find((fault) => fault.type === selectedFault.value) ?? faultMetas[0]);
const currentEvents = computed(() =>
  stream.events.value
    .filter((event) => !event.sessionId || event.sessionId === sessionId.value)
    .slice()
);
const latestEvent = computed(() => {
  const allEvents = currentEvents.value;
  return allEvents.length === 0 ? null : allEvents[allEvents.length - 1];
});
const visibleRecord = computed(() =>
  activeView.value === 'records'
    ? selectedRecord.value ?? currentRecord.value
    : currentRecord.value ?? selectedRecord.value,
);
const completedEvent = computed(() => [...currentEvents.value].reverse().find((event) => event.stage === 'completed'));
const notifiedTimingEvent = computed(() =>
  [...currentEvents.value]
    .reverse()
    .find((event) => event.stage === 'notified' && numberDetail(event, 'durationMillis') !== null),
);
const topMetricEvent = computed(() => notifiedTimingEvent.value ?? completedEvent.value);
const currentDuration = computed(() => {
  const eventDuration = numberDetail(topMetricEvent.value, 'durationMillis');
  if (eventDuration !== null) {
    return eventDuration;
  }
  const record = currentRecord.value;
  if (record?.timing?.durationMillis !== undefined) {
    return record.timing.durationMillis;
  }
  if (record) {
    return recordDuration(record);
  }
  const firstEvent = currentEvents.value[0];
  if (firstEvent && (stream.connected.value || scenario.value?.stage === 'RUNNING')) {
    return Math.max(0, nowMillis.value - new Date(firstEvent.timestamp).getTime());
  }
  return null;
});
const waitingForRestart = computed(() => scenario.value?.stage === 'WAITING_FOR_TARGET_RESTART');
const canStart = computed(() => Boolean(readiness.value?.ready) && !runningAction.value);
const terminalOutcome = computed(() => {
  const detailsOutcome = latestEvent.value?.details?.outcome;
  if (typeof detailsOutcome === 'string') {
    return detailsOutcome;
  }
  return currentRecord.value?.outcome ?? scenario.value?.stage ?? 'READY';
});

const restartCommand = computed(() => {
  const path = scenario.value?.worktreePath;
  if (!path) {
    return '等待 PR-safe scenario 返回 worktreePath';
  }
  return `Push-Location "${path}"\nmvn -pl target-service spring-boot:run`;
});

const readinessItems = computed(() => [
  { label: 'LLM', enabled: readiness.value?.llmEnabled ?? false },
  { label: 'Git', enabled: readiness.value?.gitEnabled ?? false },
  { label: 'GitHub PR', enabled: readiness.value?.githubEnabled ?? false },
  { label: 'Feishu', enabled: readiness.value?.feishuEnabled ?? false },
  { label: 'Base Branch', enabled: readiness.value?.baseBranchMatches ?? false },
]);

const allToolEvents = computed<ToolEventView[]>(() =>
  currentEvents.value
    .map((event, index) => ({ event, index }))
    .filter(({ event }) => isToolEvent(event))
    .map(({ event, index }) => toToolEvent(event, index)),
);

const primaryToolEvents = computed<ToolEventView[]>(() =>
  allToolEvents.value.filter((event) => isPrimaryTool(event.toolName)),
);

const activeTool = computed(() => {
  const events = primaryToolEvents.value;
  return events.length === 0 ? null : events[events.length - 1];
});

const requiredToolCoverage = computed(() =>
  ['ReadLog', 'ReadCode', 'PatchAgent', 'RunTest', 'GitCommit', 'GitHub PR', 'Feishu'].map((toolName) => ({
    toolName,
    present: primaryToolEvents.value.some((event) => event.toolName === toolName),
  })),
);

const chatItems = computed<ChatItem[]>(() => {
  const items: ChatItem[] = [];
  if (currentEvents.value.length === 0) {
    items.push({
      id: 'session-open',
      role: 'system',
      badgeLabel: 'System',
      title: '演示会话已就绪',
      body: `${activeFault.value.label} / ${sessionId.value}`,
      meta: 'operator',
      status: scenario.value?.stage ?? 'READY',
      order: 0,
    });
  }

  const mergedTools = new Map<string, ToolEventView>();
  for (const tool of primaryToolEvents.value) {
    const key = `${tool.toolName}|${tool.target}`;
    const existing = mergedTools.get(key);
    if (!existing || toolStatusRank(tool.status) >= toolStatusRank(existing.status)) {
      mergedTools.set(key, existing ? { ...tool, order: existing.order } : tool);
    }
  }
  for (const tool of mergedTools.values()) {
    items.push(chatItemFromTool(tool));
  }

  const patchEntry = [...currentEvents.value.entries()]
    .reverse()
    .find(([, event]) => isPatchTimelineEvent(event));
  if (patchEntry && displayPatch.value.operations.length > 0) {
    const [index, event] = patchEntry;
    items.push(chatItemFromPatch(event, index, displayPatch.value));
  }

  for (const [index, event] of currentEvents.value.entries()) {
    if (event.stage === 'planning' || isPatchTimelineEvent(event) || isToolEvent(event) || isNoisyAgentEvent(event)) {
      continue;
    }
    const item = chatItemFromEvent(event, index);
    if (item) {
      items.push(item);
    }
  }
  return items.sort((left, right) => (left.order ?? 0) - (right.order ?? 0));
});

const latestChatItem = computed(() => {
  const items = chatItems.value;
  return items.length === 0 ? null : items[items.length - 1];
});
const selectedToolEvent = computed(() => allToolEvents.value.find((tool) => tool.id === selectedChatItemId.value) ?? null);
const selectedChatItem = computed(() => {
  if (pinnedPlanItem.value?.id === selectedChatItemId.value) {
    return pinnedPlanItem.value;
  }
  const items = chatItems.value;
  const selectedItem = items.find((item) => item.id === selectedChatItemId.value);
  if (selectedItem) {
    return selectedItem;
  }
  return selectedToolEvent.value ? chatItemFromTool(selectedToolEvent.value) : latestChatItem.value;
});
const inspectorEvent = computed(() => selectedChatItem.value?.event ?? latestEvent.value);
const selectedPrUrl = computed(() => visibleRecord.value?.pullRequestResult?.url || scenario.value?.prUrl || '');
const currentPrUrl = computed(() => currentRecord.value?.pullRequestResult?.url || scenario.value?.prUrl || '');
const reviewDiffFiles = computed(() => buildReviewDiffFiles(visibleRecord.value));
const hasRawDiffFallback = computed(() => Boolean(visibleRecord.value?.diffSummary && reviewDiffFiles.value.length === 0));
const selectedDiffFile = computed(() =>
  reviewDiffFiles.value.find((file) => file.filePath === selectedDiffFilePath.value) ?? reviewDiffFiles.value[0] ?? null,
);
const tokenUsage = computed<TokenTotals>(() =>
  collectTokenUsage(unknownArrayDetail(topMetricEvent.value, 'modelUsage'), currentRecord.value?.timing?.modelUsage),
);
const prStatusLabel = computed(() => (currentPrUrl.value ? '已创建' : '未创建'));
const feishuStatusLabel = computed(() => {
  const event = [...currentEvents.value].reverse().find((item) => item.stage === 'notified');
  if (event) {
    const notificationSuccess = nestedBooleanDetail(event, 'notification', 'success') ?? booleanDetail(event, 'success');
    if (stringDetail(event, 'status') === 'failed' || notificationSuccess === false) {
      return '失败';
    }
    if (event.message.includes('Sending')) {
      return '发送中';
    }
    return '已发送';
  }
  const sent = currentRecord.value?.notificationResult?.success ?? scenario.value?.notificationSuccess;
  if (sent === false) {
    return '失败';
  }
  return sent ? '已发送' : '未发送';
});
const feishuStatusClass = computed(() =>
  feishuStatusLabel.value === '已发送'
    ? 'success'
    : feishuStatusLabel.value === '失败'
      ? 'danger'
      : feishuStatusLabel.value === '发送中'
        ? 'warning'
        : 'neutral',
);
const preflightChecks = computed<ReadinessCheck[]>(() => [
  { label: '代码仓库可访问', ok: readiness.value?.gitEnabled ?? false },
  { label: 'LLM 修复引擎可用', ok: readiness.value?.llmEnabled ?? false },
  { label: '基础分支匹配', ok: readiness.value?.baseBranchMatches ?? false },
  { label: 'Git 凭证可用', ok: readiness.value?.gitEnabled ?? false },
  { label: '飞书 Webhook 可用', ok: readiness.value?.feishuEnabled ?? false },
  { label: 'GitHub Token 可用', ok: readiness.value?.githubEnabled ?? false },
]);
const serviceRows = computed<ServiceStatusRow[]>(() => [
  {
    service: 'target-service',
    instance: targetRestart.value?.pid ? '1/1' : waitingForRestart.value ? '0/1' : '1/1',
    status: waitingForRestart.value ? '待重启' : scenario.value || stream.connected.value ? '运行中' : '待命',
    lastRestart: targetRestart.value?.pid ? `pid ${targetRestart.value.pid}` : latestEvent.value ? formatTime(latestEvent.value.timestamp) : '-',
  },
]);
const activeWorkbenchStage = computed(() =>
  workbenchStages.find((stage) => stageStateForWorkbench(stage) === 'active')
    ?? [...workbenchStages].reverse().find((stage) => stageStateForWorkbench(stage) === 'done')
    ?? workbenchStages[0],
);
const sseRawRows = computed<SseRawLine[]>(() => {
  const sourceEvent = inspectorEvent.value;
  const lines = sourceEvent
    ? formatSseEvent(sourceEvent)
    : ['event: idle', `data: ${JSON.stringify({ sessionId: sessionId.value, stage: 'READY' })}`];
  return lines.map((text, index) => ({ number: index + 1, text }));
});
const sseRawText = computed(() => sseRawRows.value.map((row) => row.text).join('\n'));
const displayPlan = computed<DisplayPlan>(() => buildDisplayPlan(visibleRecord.value, currentEvents.value));
const displayPatch = computed<DisplayPatch>(() => buildDisplayPatch(visibleRecord.value, currentEvents.value));
const latestPlanEvent = computed(() => [...currentEvents.value].reverse().find((event) => event.stage === 'planning') ?? null);
const pinnedPlanItem = computed<ChatItem | null>(() => {
  const planEvent = latestPlanEvent.value;
  if (!planEvent && !visibleRecord.value?.plan) {
    return null;
  }
  return chatItemFromPlan(planEvent, planEvent ? currentEvents.value.indexOf(planEvent) : -1, displayPlan.value);
});
const planRevealTotalSegments = computed(() => {
  const plan = pinnedPlanItem.value?.plan;
  if (!plan) {
    return 0;
  }
  return 1 + plan.steps.length + (plan.files.length > 0 ? 1 : 0);
});
const planRevealKey = computed(() => {
  const item = pinnedPlanItem.value;
  if (!item?.plan) {
    return '';
  }
  if (item.event) {
    return `${item.event.sessionId || sessionId.value}|${item.event.timestamp}|planning`;
  }
  return `${visibleRecord.value?.sessionId || sessionId.value}|record-plan`;
});
const visiblePlanStepCount = computed(() => {
  if (!pinnedPlanItem.value?.plan) {
    return 0;
  }
  return Math.max(0, Math.min(pinnedPlanItem.value.plan.steps.length, visiblePlanSegments.value - 1));
});
const shouldShowPlanRoot = computed(() => visiblePlanSegments.value >= 1);
const shouldShowPlanFiles = computed(() => {
  const plan = pinnedPlanItem.value?.plan;
  return Boolean(plan?.files.length) && visiblePlanSegments.value >= planRevealTotalSegments.value;
});
const nextTimelineHint = computed(() => {
  if (latestEvent.value?.stage === 'completed' || latestEvent.value?.stage === 'error') {
    return '';
  }
  if (waitingForRestart.value) {
    return '下一步将重启 target-service，并确认故障复现环境。';
  }
  const stage = latestEvent.value?.stage;
  if (!stage) {
    return '下一步将启动修复流程并读取 Traceback。';
  }
  const hints: Partial<Record<RepairStage, string>> = {
    detecting: '下一步将进行根因分析，并生成修复计划。',
    planning: '下一步将根据计划生成自动补丁。',
    executing: '下一步将读取目标代码并准备补丁变更。',
    patching: '下一步将应用补丁并运行回归测试。',
    testing: '下一步将审查 diff 和测试结果。',
    reviewing: '下一步将提交修复分支。',
    committing: '下一步将创建 GitHub Pull Request。',
    pr_created: '下一步将发送飞书通知给开发者 Review。',
    notified: '下一步将生成修复记录和反思沉淀。',
    reflecting: '下一步将完成闭环并刷新修复记录。',
  };
  return hints[stage] ?? '下一步将继续推进自动修复流程。';
});

const scoreEvidence = computed(() => {
  const record = visibleRecord.value;
  const eventStages = new Set(currentEvents.value.map((event) => event.stage));
  const coverage = requiredToolCoverage.value
    .map((item) => `${item.toolName}=${item.present ? 'yes' : 'no'}`)
    .join(' / ');
  return [
    {
      title: '完整性与价值',
      body: record
        ? `${record.outcome} / ${formatDuration(record.timing?.durationMillis ?? recordDuration(record))} / tests=${formatBoolean(record.testResult?.success)}`
        : `${eventStages.size}/${repairStages.length} 个阶段已出现；页面保留刷新后的 SSE 历史。`,
    },
    {
      title: '创新性',
      body: 'Java DAG + 4 个 LangChain4j 子 Agent + Reflexion 测试失败重写 + PR-safe worktree + ChatOps 证据流。',
    },
    {
      title: '技术实现性',
      body: record
        ? `patchAttempts=${patchAttempts(record)} / tokens=${formatNumber(totalTokens(record))} / PR=${formatBoolean(record.pullRequestResult?.success)} / ${coverage}`
        : `ToolPolicy 写入边界、RunTest 门禁、GitCommit、GitHub REST PR、Feishu v2 卡片；${coverage}`,
    },
  ];
});

watch(selectedFault, async () => {
  if (restoringPersistedState || skipNextFaultReset) {
    skipNextFaultReset = false;
    return;
  }
  sessionId.value = generateSessionId(selectedFault.value);
  scenario.value = null;
  targetRestart.value = null;
  currentRecord.value = null;
  selectedRecord.value = null;
  autoRestartStatus.value = 'idle';
  stream.reset();
  await refreshReadiness();
});

watch(
  () => ({
    activeView: activeView.value,
    selectedFault: selectedFault.value,
    sessionId: sessionId.value,
    darkMode: darkMode.value,
    runToolTraceCollapsed: runToolTraceCollapsed.value,
    scenario: scenario.value,
    targetRestart: targetRestart.value,
    currentRecord: currentRecord.value,
    selectedRecord: selectedRecord.value,
    events: stream.events.value,
  }),
  (state) => persistState(state),
  { deep: true },
);

watch(
  () => [chatItems.value.length, latestChatItem.value?.id, pinnedPlanItem.value?.id, latestEvent.value?.timestamp],
  () => {
    if (restoringPersistedState || activeView.value !== 'run' || !isChatFeedNearBottom()) {
      return;
    }
    nextTick(() => scrollChatFeedToBottom());
  },
);

watch(
  () => planRevealKey.value,
  (planKey) => {
    schedulePlanReveal(planKey, planRevealTotalSegments.value);
  },
  { immediate: true },
);

watch(
  () => planRevealTotalSegments.value,
  (totalSegments) => {
    if (
      !planRevealKey.value
      || activePlanRevealKey.value !== planRevealKey.value
      || planRevealTimer !== undefined
      || visiblePlanSegments.value >= totalSegments
    ) {
      return;
    }
    visiblePlanSegments.value = totalSegments;
  },
);

onMounted(async () => {
  restoreState();
  await nextTick();
  planTransitionReady.value = true;
  restoringPersistedState = false;
  clockTimer = window.setInterval(() => {
    nowMillis.value = Date.now();
  }, 1000);
  await Promise.all([loadFaults(), refreshReadiness(), loadRecords()]);
  if (scenario.value?.repairStreamUrl && scenario.value.stage === 'RUNNING') {
    connectStream(scenario.value.repairStreamUrl);
  }
});

onUnmounted(() => {
  stream.close();
  clearPlanRevealTimer();
  if (clockTimer !== undefined) {
    window.clearInterval(clockTimer);
    clockTimer = undefined;
  }
});

async function loadFaults() {
  loadingFaults.value = true;
  try {
    faults.value = await api.listFaults();
  } catch {
    faults.value = fallbackFaults;
  } finally {
    loadingFaults.value = false;
  }
}

async function refreshReadiness() {
  loadingReadiness.value = true;
  errorMessage.value = '';
  try {
    readiness.value = await api.readiness(selectedFault.value);
  } catch (error) {
    readiness.value = null;
    errorMessage.value = normalizeError(error);
  } finally {
    loadingReadiness.value = false;
  }
}

async function loadRecords() {
  loadingRecords.value = true;
  try {
    const index = await api.listRecords();
    records.value = index.records ?? [];
  } catch (error) {
    errorMessage.value = normalizeError(error);
  } finally {
    loadingRecords.value = false;
  }
}

async function startDemo() {
  if (!readiness.value?.ready) {
    errorMessage.value = '演示预检未通过，请先处理 readiness warnings。';
    return;
  }
  runningAction.value = true;
  errorMessage.value = '';
  currentRecord.value = null;
  selectedRecord.value = null;
  targetRestart.value = null;
  autoRestartStatus.value = 'idle';
  stream.reset();
  try {
    scenario.value = await api.startPrScenario(sessionId.value.trim(), selectedFault.value);
    if (scenario.value.stage === 'WAITING_FOR_TARGET_RESTART') {
      await autoRestartAndConfirm();
      return;
    }
    if (scenario.value.repairStreamUrl) {
      connectStream(scenario.value.repairStreamUrl);
    }
  } catch (error) {
    errorMessage.value = normalizeError(error);
  } finally {
    runningAction.value = false;
  }
}

async function autoRestartAndConfirm() {
  if (!scenario.value) {
    return;
  }
  autoRestartStatus.value = 'running';
  targetRestart.value = await api.restartTargetService(scenario.value.sessionId);
  if (!targetRestart.value.success) {
    autoRestartStatus.value = 'failed';
    errorMessage.value = targetRestart.value.message;
    return;
  }
  autoRestartStatus.value = 'done';
  scenario.value = await api.confirmPrScenario(scenario.value.sessionId);
  if (scenario.value.repairStreamUrl) {
    connectStream(scenario.value.repairStreamUrl);
  }
}

async function confirmRestart() {
  if (!scenario.value) {
    return;
  }
  runningAction.value = true;
  errorMessage.value = '';
  try {
    scenario.value = await api.confirmPrScenario(scenario.value.sessionId);
    if (scenario.value.repairStreamUrl) {
      connectStream(scenario.value.repairStreamUrl);
    }
  } catch (error) {
    errorMessage.value = normalizeError(error);
  } finally {
    runningAction.value = false;
  }
}

function connectStream(streamUrl: string) {
  stream.connect(streamUrl, async (event) => {
    await Promise.all([loadRecords(), refreshScenario()]);
    if (event.stage === 'completed' || event.stage === 'error') {
      await loadCurrentRecord();
    }
  });
}

async function refreshScenario() {
  if (!scenario.value?.sessionId) {
    return;
  }
  try {
    scenario.value = await api.getScenario(scenario.value.sessionId);
  } catch {
    return;
  }
}

async function loadCurrentRecord() {
  if (!scenario.value?.sessionId) {
    return;
  }
  try {
    currentRecord.value = await api.getRecord(scenario.value.sessionId);
  } catch {
    currentRecord.value = null;
  }
}

async function openRecord(summary: RepairRecordSummary) {
  errorMessage.value = '';
  activeView.value = 'records';
  try {
    selectedRecord.value = await api.getRecord(summary.sessionId);
  } catch (error) {
    errorMessage.value = normalizeError(error);
  }
}

function selectChatItem(item: ChatItem) {
  selectedChatItemId.value = item.id;
}

function selectToolEvent(tool: ToolEventView) {
  selectedChatItemId.value = tool.id;
}

async function copyRestartCommand() {
  if (!navigator.clipboard) {
    copyStatus.value = '当前浏览器不支持复制，请手动选中命令。';
    return;
  }
  await navigator.clipboard.writeText(restartCommand.value);
  copyStatus.value = '已复制重启命令';
  window.setTimeout(() => {
    copyStatus.value = '';
  }, 1800);
}

async function copySseRaw() {
  if (!navigator.clipboard) {
    sseCopyStatus.value = '当前浏览器不支持复制。';
    return;
  }
  await navigator.clipboard.writeText(sseRawText.value);
  sseCopyStatus.value = '已复制';
  window.setTimeout(() => {
    sseCopyStatus.value = '';
  }, 1600);
}

function newSessionId() {
  sessionId.value = generateSessionId(selectedFault.value);
}

function eventForStage(stage: RepairStage) {
  return [...currentEvents.value].reverse().find((event) => event.stage === stage);
}

function stageStateForWorkbench(stage: WorkbenchStage) {
  if (latestEvent.value?.stage === 'error') {
    return stage.stages.some((item) => eventForStage(item)) ? 'failed' : 'pending';
  }
  if (latestEvent.value && stage.stages.includes(latestEvent.value.stage) && latestEvent.value.stage !== 'completed') {
    return 'active';
  }
  if (stage.stages.some((item) => eventForStage(item))) {
    return 'done';
  }
  return 'pending';
}

function isToolEvent(event: RepairEvent) {
  const eventType = stringDetail(event, 'eventType');
  return eventType.includes('tool')
    || eventType.includes('model')
    || ['RunTest', 'GitCommit', 'ReadLog', 'ReadCode', 'SearchCode'].includes(stringDetail(event, 'toolName'))
    || stringDetail(event, 'toolName') === 'PatchAgent'
    || isEvidenceReadLogEvent(event)
    || event.stage === 'pr_created'
    || event.stage === 'notified';
}

function isPrimaryTool(toolName: string) {
  return ['ReadLog', 'ReadCode', 'SearchCode', 'PatchAgent', 'RunTest', 'GitCommit', 'GitHub PR', 'Feishu'].includes(toolName);
}

function isNoisyAgentEvent(event: RepairEvent) {
  const eventType = stringDetail(event, 'eventType');
  if (eventType === 'agent_tool_started' || eventType === 'agent_tool_completed') {
    return true;
  }
  return stringDetail(event, 'toolName') === 'AgentTool';
}

function isPatchTimelineEvent(event: RepairEvent) {
  return Boolean(asRecord(event.details?.patchProposal))
    || event.message.includes('Patch proposal validated')
    || event.message.includes('Patch proposal applied');
}

function isEvidenceReadLogEvent(event: RepairEvent) {
  if (event.stage !== 'detecting') {
    return false;
  }
  return event.message.includes('EvidenceAgent collecting')
    || event.message.includes('Evidence collected')
    || Boolean(asRecord(event.details?.evidence));
}

function eventToolName(event: RepairEvent) {
  return stringDetail(event, 'toolName') || (isEvidenceReadLogEvent(event) ? 'ReadLog' : stageToolName(event.stage));
}

function eventToolTarget(event: RepairEvent, toolName = eventToolName(event)) {
  return stringDetail(event, 'target')
    || stringDetail(event, 'branchName')
    || (toolName === 'ReadLog' ? 'target-service/logs' : event.stage);
}

function explicitDurationMillis(event?: RepairEvent | null) {
  return numberDetail(event, 'durationMillis') ?? numberDetail(event, 'durationMs') ?? numberDetail(event, 'elapsedMillis');
}

function inferToolDurationMillis(event: RepairEvent, index: number, toolName: string, target: string, status: string) {
  if (status === 'running') {
    return Math.max(0, nowMillis.value - new Date(event.timestamp).getTime());
  }
  for (let cursor = index - 1; cursor >= 0; cursor -= 1) {
    const previous = currentEvents.value[cursor];
    if (!previous || !isToolEvent(previous)) {
      continue;
    }
    const previousToolName = eventToolName(previous);
    const previousTarget = eventToolTarget(previous, previousToolName);
    if (previousToolName !== toolName || previousTarget !== target) {
      continue;
    }
    const previousStatus = stringDetail(previous, 'status');
    const previousEventType = stringDetail(previous, 'eventType');
    if (previousStatus === 'running' || previousEventType.includes('started')) {
      return Math.max(0, new Date(event.timestamp).getTime() - new Date(previous.timestamp).getTime());
    }
  }
  return null;
}

function chatEventDurationMillis(event: RepairEvent, index: number) {
  const explicit = explicitDurationMillis(event);
  if (explicit !== null) {
    return explicit;
  }
  const current = new Date(event.timestamp).getTime();
  const previous = currentEvents.value[index - 1];
  if (previous) {
    return Math.max(0, current - new Date(previous.timestamp).getTime());
  }
  const next = currentEvents.value[index + 1];
  if (next) {
    return Math.max(0, new Date(next.timestamp).getTime() - current);
  }
  return null;
}

function toToolEvent(event: RepairEvent, index: number): ToolEventView {
  const toolName = eventToolName(event);
  const inferredSuccess = event.stage === 'pr_created'
    ? nestedBooleanDetail(event, 'pullRequest', 'success')
    : event.stage === 'notified'
      ? nestedBooleanDetail(event, 'notification', 'success')
      : null;
  const status = stringDetail(event, 'status')
    || (event.message.includes('Creating') || event.message.includes('Sending') || event.message.includes('collecting') ? 'running' : '')
    || (booleanDetail(event, 'success') === false || inferredSuccess === false ? 'failed' : 'completed');
  const success = booleanDetail(event, 'success') ?? inferredSuccess;
  const target = eventToolTarget(event, toolName);
  const summary = stringDetail(event, 'summary') || event.message;
  const durationMillis = explicitDurationMillis(event) ?? inferToolDurationMillis(event, index, toolName, target, status);
  return {
    id: `${event.timestamp}-${index}`,
    event,
    order: index,
    toolName,
    action: stringDetail(event, 'eventType') || 'tool_event',
    target,
    status,
    success,
    summary,
    durationMillis,
  };
}

function chatItemFromTool(tool: ToolEventView): ChatItem {
  return {
    id: `tool-row-${tool.toolName}-${tool.target}`,
    role: 'tool',
    badgeLabel: 'Tool',
    title: toolTitle(tool.toolName),
    body: toolSentence(tool),
    meta: `${tool.action} / ${formatDate(tool.event.timestamp)}`,
    status: tool.status,
    order: tool.order,
    durationMillis: tool.durationMillis,
    event: tool.event,
    tool,
  };
}

function chatItemFromPlan(event: RepairEvent | null, index: number, plan: DisplayPlan): ChatItem {
  return {
    id: event ? `${event.timestamp}-${index}-plan` : 'record-plan',
    role: 'agent',
    badgeLabel: event ? stageLabel(event.stage) : 'AI 根因与计划',
    title: 'AI 根因与计划',
    body: '根因分析和修复计划已生成',
    meta: event ? `${event.stage} / ${formatDate(event.timestamp)}` : 'record / completed',
    status: event ? stringDetail(event, 'status') || event.stage : 'completed',
    order: index,
    durationMillis: event ? chatEventDurationMillis(event, index) : null,
    event: event ?? undefined,
    plan,
  };
}

function chatItemFromPatch(event: RepairEvent, index: number, patch: DisplayPatch): ChatItem {
  return {
    id: `${event.timestamp}-${index}-patch`,
    role: 'agent',
    badgeLabel: stageLabel(event.stage),
    title: 'Patch 代码变更',
    body: patch.summary || event.message,
    meta: `${event.stage} / ${formatDate(event.timestamp)}`,
    status: stringDetail(event, 'status') || event.stage,
    order: index,
    durationMillis: chatEventDurationMillis(event, index),
    event,
    patch,
  };
}

function chatItemFromEvent(event: RepairEvent, index: number): ChatItem | null {
  const eventType = stringDetail(event, 'eventType');
  if (eventType === 'agent_started') {
    return null;
  }
  const label = stageLabel(event.stage);
  const title = eventType.startsWith('agent_')
    ? `AI ${stringDetail(event, 'agentName') || event.stage}`
    : label;
  const meta = eventType.startsWith('agent_')
    ? `${stringDetail(event, 'role') || 'AGENT'} / ${stringDetail(event, 'model') || 'model n/a'}`
    : `${event.stage} / ${formatDate(event.timestamp)}`;
  return {
    id: `${event.timestamp}-${index}`,
    role: eventType.startsWith('agent_') ? 'agent' : 'system',
    badgeLabel: label,
    title,
    body: event.message,
    meta,
    status: stringDetail(event, 'status') || event.stage,
    order: index,
    durationMillis: chatEventDurationMillis(event, index),
    event,
  };
}

function toolSentence(tool: ToolEventView) {
  const target = tool.target ? `：${shortTarget(tool.target)}` : '';
  if (tool.toolName === 'ReadLog') {
    return `${toolActionText(tool, '读取 Traceback')}${target}`;
  }
  if (tool.toolName === 'ReadCode') {
    return `${toolActionText(tool, '读取代码')}${target}`;
  }
  if (tool.toolName === 'SearchCode') {
    return `${toolActionText(tool, '搜索代码')}${target}`;
  }
  if (tool.toolName === 'PatchAgent') {
    return `${toolActionText(tool, '生成补丁')}${target}`;
  }
  if (tool.toolName === 'RunTest') {
    return `${toolActionText(tool, '运行测试')}${target}`;
  }
  if (tool.toolName === 'GitCommit') {
    return `${toolActionText(tool, '提交修复分支')}${target}`;
  }
  if (tool.toolName === 'GitHub PR') {
    return `${toolActionText(tool, '创建 Pull Request')}${target}`;
  }
  if (tool.toolName === 'Feishu') {
    return `${toolActionText(tool, '发送修复通知')}${target}`;
  }
  return `${toolActionText(tool, '调用工具')}${target}`;
}

function toolActionText(tool: ToolEventView, action: string) {
  if (tool.status === 'running') {
    return `正在${action}`;
  }
  if (tool.success === false || tool.status === 'failed') {
    return `${action}失败`;
  }
  if (tool.status === 'cached') {
    return `已从缓存${action}`;
  }
  return `已完成${action}`;
}

function toolTitle(toolName: string) {
  const titles: Record<string, string> = {
    ReadLog: 'ReadLog / 读取日志',
    ReadCode: 'ReadCode / 读取代码',
    SearchCode: 'SearchCode / 搜索代码',
    PatchAgent: 'PatchAgent / 生成补丁',
    RunTest: 'Run Test / 运行测试',
    GitCommit: 'Git Commit / 提交分支',
    'GitHub PR': 'GitHub PR / 创建 PR',
    Feishu: 'Feishu / 飞书通知',
    AgentTool: 'Agent Tool / LangChain4j',
  };
  return titles[toolName] ?? toolName;
}

function toolIcon(toolName: string) {
  if (toolName === 'ReadLog') {
    return FileSearch;
  }
  if (toolName === 'ReadCode' || toolName === 'SearchCode') {
    return Code2;
  }
  if (toolName === 'PatchAgent') {
    return BrainCircuit;
  }
  if (toolName === 'RunTest') {
    return FlaskConical;
  }
  if (toolName === 'GitCommit') {
    return GitBranch;
  }
  if (toolName === 'GitHub PR') {
    return GitPullRequest;
  }
  if (toolName === 'Feishu') {
    return Bell;
  }
  return Wrench;
}

function stageToolName(stage: RepairStage) {
  if (stage === 'testing') {
    return 'RunTest';
  }
  if (stage === 'committing') {
    return 'GitCommit';
  }
  if (stage === 'pr_created') {
    return 'GitHub PR';
  }
  if (stage === 'notified') {
    return 'Feishu';
  }
  if (stage === 'detecting') {
    return 'ReadLog';
  }
  return 'AgentTool';
}

function stageLabel(stage: RepairStage) {
  return stageDefs.find((item) => item.stage === stage)?.label ?? 'System';
}

function stringDetail(event: RepairEvent, key: string) {
  const value = event.details?.[key];
  return typeof value === 'string' ? value : '';
}

function booleanDetail(event: RepairEvent, key: string) {
  const value = event.details?.[key];
  return typeof value === 'boolean' ? value : null;
}

function numberDetail(event: RepairEvent | null | undefined, key: string) {
  const value = event?.details?.[key];
  return typeof value === 'number' ? value : null;
}

function unknownArrayDetail(event: RepairEvent | null | undefined, key: string) {
  const value = event?.details?.[key];
  return Array.isArray(value) ? value : [];
}

function nestedBooleanDetail(event: RepairEvent, objectKey: string, fieldKey: string) {
  const nested = asRecord(event.details?.[objectKey]);
  const value = nested?.[fieldKey];
  return typeof value === 'boolean' ? value : null;
}

function displayChatBody(item: ChatItem) {
  return item.body;
}

function persistState(state: PersistedState) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    return;
  }
}

function restoreState() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return;
    }
    const parsed = JSON.parse(raw) as PersistedState;
    if (parsed.activeView && navItems.some((item) => item.id === parsed.activeView)) {
      activeView.value = parsed.activeView;
    }
    if (parsed.selectedFault && faultMetas.some((fault) => fault.type === parsed.selectedFault)) {
      skipNextFaultReset = selectedFault.value !== parsed.selectedFault;
      selectedFault.value = parsed.selectedFault;
    }
    if (parsed.sessionId) {
      sessionId.value = parsed.sessionId;
    }
    darkMode.value = parsed.darkMode ?? false;
    runToolTraceCollapsed.value = parsed.runToolTraceCollapsed ?? false;
    scenario.value = parsed.scenario ?? null;
    targetRestart.value = parsed.targetRestart ?? null;
    currentRecord.value = parsed.currentRecord ?? null;
    selectedRecord.value = parsed.selectedRecord ?? null;
    stream.setEvents(parsed.events ?? []);
  } catch {
    return;
  }
}

function generateSessionId(type: FaultType) {
  const prefix: Record<FaultType, string> = {
    'quantity-division-by-zero': 'pr-quantity',
    'wrong-quote-route': 'pr-route',
    'wrong-error-status': 'pr-status',
    'precision-loss': 'pr-precision',
    'race-condition': 'pr-race',
    'path-traversal': 'pr-traversal',
  };
  const now = new Date();
  const stamp = [
    now.getFullYear(),
    pad(now.getMonth() + 1),
    pad(now.getDate()),
    '-',
    pad(now.getHours()),
    pad(now.getMinutes()),
    pad(now.getSeconds()),
  ].join('');
  return `${prefix[type]}-${stamp}`;
}

function pad(value: number) {
  return String(value).padStart(2, '0');
}

function normalizeError(error: unknown) {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

function formatDate(value?: string) {
  if (!value) {
    return '-';
  }
  return new Date(value).toLocaleString('zh-CN', {
    hour12: false,
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

function formatTime(value?: string) {
  if (!value) {
    return '-';
  }
  return new Date(value).toLocaleTimeString('zh-CN', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

function formatDuration(value?: number | null) {
  if (value === null || value === undefined) {
    return '-';
  }
  if (value < 1000) {
    return `${value} ms`;
  }
  return `${(value / 1000).toFixed(1)} s`;
}

function formatBoolean(value?: boolean | null) {
  if (value === true) {
    return 'yes';
  }
  if (value === false) {
    return 'no';
  }
  return 'n/a';
}

function formatNumber(value?: number | null) {
  return value === null || value === undefined ? '-' : new Intl.NumberFormat('zh-CN').format(value);
}

function collectTokenUsage(eventUsage: unknown[], recordUsage?: unknown[]): TokenTotals {
  const usage = eventUsage.length > 0 ? eventUsage : recordUsage ?? [];
  let input = 0;
  let output = 0;
  let total = 0;
  let found = false;
  for (const item of usage) {
    const record = asRecord(item);
    if (!record) {
      continue;
    }
    const inputValue = numberField(record, 'inputTokenCount');
    const outputValue = numberField(record, 'outputTokenCount');
    const totalValue = numberField(record, 'totalTokenCount');
    if (inputValue !== null || outputValue !== null || totalValue !== null) {
      found = true;
    }
    input += inputValue ?? 0;
    output += outputValue ?? 0;
    total += totalValue ?? 0;
  }
  return found
    ? { input, output, total: total > 0 ? total : input + output }
    : { input: null, output: null, total: null };
}

function buildDisplayPlan(record: RepairRecord | null, events: RepairEvent[]): DisplayPlan {
  const livePlan = [...events]
    .reverse()
    .map((event) => asRecord(event.details?.plan))
    .find((plan): plan is Record<string, unknown> => Boolean(plan));
  const recordPlan = asRecord(record?.plan);
  const reflection = asRecord(record?.reflection);
  const review = asRecord(record?.reviewDecision);
  const patchApplication = asRecord(record?.patchApplicationResult);
  const rootCause = stringField(livePlan, 'rootCauseHypothesis')
    || stringField(livePlan, 'rootCause')
    || stringField(recordPlan, 'rootCauseHypothesis')
    || stringField(recordPlan, 'rootCause')
    || stringField(reflection, 'rootCause')
    || record?.tracebackSummary
    || '等待 Agent 输出根因分析。';
  const repairTarget = stringField(livePlan, 'repairTarget')
    || stringField(recordPlan, 'repairTarget')
    || record?.patchProposal?.summary
    || '等待 Repair Plan。';
  const files = stringArrayField(livePlan, 'suspectedFiles')
    .concat(stringArrayField(recordPlan, 'suspectedFiles'))
    .concat(stringArrayField(recordPlan, 'targetFiles'))
    .concat(stringArrayField(review, 'touchedFiles'))
    .concat(stringArrayField(patchApplication, 'changedFiles'))
    .filter(uniqueString);
  const steps = stringArrayField(livePlan, 'steps')
    .concat(stringArrayField(recordPlan, 'steps'))
    .concat(stringArrayField(recordPlan, 'patchStrategy'))
    .filter(uniqueString);
  const testCommand = stringField(livePlan, 'testCommand')
    || stringField(recordPlan, 'testCommand')
    || 'mvn -pl target-service test';
  return { rootCause, repairTarget, files, steps, testCommand };
}

function buildDisplayPatch(record: RepairRecord | null, events: RepairEvent[]): DisplayPatch {
  const livePatch = [...events]
    .reverse()
    .map((event) => asRecord(event.details?.patchProposal))
    .find((proposal): proposal is Record<string, unknown> => Boolean(proposal));
  const recordPatch = asRecord(record?.patchProposal);
  const proposal = livePatch ?? recordPatch;
  if (!proposal) {
    return { summary: '', operations: [] };
  }
  const rawOperations = proposal.operations;
  const operations = Array.isArray(rawOperations)
    ? rawOperations
        .map((item) => asRecord(item))
        .filter((item): item is Record<string, unknown> => Boolean(item))
        .map((operation, index) => {
          const oldText = stringField(operation, 'oldText');
          const newText = stringField(operation, 'newText');
          return {
            filePath: stringField(operation, 'filePath') || stringField(operation, 'path') || `patch-operation-${index + 1}`,
            reason: stringField(operation, 'reason') || '更新目标代码以修复当前故障。',
            lineSummary: patchLineSummary(oldText, newText),
          };
        })
        .filter((operation) => operation.reason || operation.lineSummary)
    : [];
  const summary = stringField(proposal, 'summary')
    || stringField(proposal, 'repairTarget')
    || record?.patchApplicationResult?.message
    || 'Patch Agent 已生成代码变更。';
  return { summary, operations };
}

function patchLineSummary(oldText: string, newText: string) {
  const oldLines = countTextLines(oldText);
  const newLines = countTextLines(newText);
  if (oldLines > 0 && newLines > 0) {
    return `替换 ${oldLines} 行为 ${newLines} 行`;
  }
  if (newLines > 0) {
    return `新增 ${newLines} 行`;
  }
  if (oldLines > 0) {
    return `删除 ${oldLines} 行`;
  }
  return '';
}

function countTextLines(text: string) {
  if (!text) {
    return 0;
  }
  return text.split(/\r\n|\r|\n/).length;
}

function recordDuration(record: RepairRecord) {
  if (!record.startedAt || !record.completedAt) {
    return null;
  }
  return new Date(record.completedAt).getTime() - new Date(record.startedAt).getTime();
}

function toolStatusRank(status: string) {
  if (status === 'running') {
    return 1;
  }
  if (status === 'failed') {
    return 3;
  }
  return 2;
}

function shortTarget(value: string) {
  return value
    .replace(/^target-service\/src\/main\/java\/com\/example\/targetservice\//, './')
    .replace(/^target-service\/src\/test\/java\/com\/example\/targetservice\//, './test/')
    .replace(/^target-service\/src\/main\/java\//, './src/')
    .replace(/^target-service\/src\/test\/java\//, './test-src/');
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

function stringField(record: Record<string, unknown> | null | undefined, key: string) {
  const value = record?.[key];
  return typeof value === 'string' && value.trim() ? value : '';
}

function numberField(record: Record<string, unknown>, key: string) {
  const value = record[key];
  return typeof value === 'number' ? value : null;
}

function stringArrayField(record: Record<string, unknown> | null | undefined, key: string) {
  const value = record?.[key];
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string' && item.trim().length > 0) : [];
}

function uniqueString(value: string, index: number, values: string[]) {
  return values.indexOf(value) === index;
}

function totalTokens(record: RepairRecord) {
  const usage = record.timing?.modelUsage ?? [];
  const totals = usage
    .map((item) => item.totalTokenCount)
    .filter((value): value is number => typeof value === 'number');
  if (totals.length === 0) {
    return null;
  }
  return totals.reduce((sum, value) => sum + value, 0);
}

function patchAttempts(record: RepairRecord) {
  const parserAttempts = record.stepResults?.filter((step) => step.toolName === 'PatchParser').length ?? 0;
  if (parserAttempts > 0) {
    return parserAttempts;
  }
  return record.patchProposal ? 1 : 0;
}

function buildReviewDiffFiles(record: RepairRecord | null): RepairDiffFile[] {
  if (!record) {
    return [];
  }
  if (record.diffFiles?.length) {
    return record.diffFiles;
  }
  const operations = record.patchProposal?.operations ?? [];
  return operations
    .filter((operation) => operation.oldText || operation.newText)
    .map((operation, index) => {
      const oldLines = splitPatchText(operation.oldText);
      const newLines = splitPatchText(operation.newText);
      return {
        filePath: operation.filePath || operation.path || `patch-operation-${index + 1}`,
        oldPath: operation.filePath || operation.path || '',
        newPath: operation.filePath || operation.path || '',
        status: 'modified',
        additions: newLines.length,
        deletions: oldLines.length,
        hunks: [
          {
            header: operation.reason ? `Patch proposal: ${operation.reason}` : 'Patch proposal operation',
            oldStart: 1,
            oldLines: oldLines.length,
            newStart: 1,
            newLines: newLines.length,
            lines: [
              ...oldLines.map((content, lineIndex) => ({
                type: 'delete',
                oldLineNumber: lineIndex + 1,
                newLineNumber: null,
                content,
              })),
              ...newLines.map((content, lineIndex) => ({
                type: 'add',
                oldLineNumber: null,
                newLineNumber: lineIndex + 1,
                content,
              })),
            ],
          },
        ],
      };
    });
}

function splitPatchText(value?: string) {
  if (!value) {
    return [];
  }
  return value.replace(/\r\n/g, '\n').split('\n');
}

function toggleDiffFile(filePath: string) {
  collapsedDiffFiles.value = {
    ...collapsedDiffFiles.value,
    [filePath]: !collapsedDiffFiles.value[filePath],
  };
}

function isDiffFileCollapsed(filePath: string) {
  return collapsedDiffFiles.value[filePath] === true;
}

function selectDiffFile(filePath: string) {
  selectedDiffFilePath.value = filePath;
}

function diffLineClass(type: string) {
  if (type === 'add') {
    return 'line-add';
  }
  if (type === 'delete') {
    return 'line-delete';
  }
  if (type === 'meta') {
    return 'line-meta';
  }
  if (type === 'empty') {
    return 'line-empty';
  }
  return 'line-context';
}

function diffLinePrefix(type: string) {
  if (type === 'add') {
    return '+';
  }
  if (type === 'delete') {
    return '-';
  }
  return ' ';
}

function highlightCode(content: string) {
  const escaped = escapeHtml(content);
  const tokens = escaped.match(/(&quot;(?:\\.|[^&])*?&quot;|'(?:\\.|[^'])*?'|\/\/.*|\/\*.*?\*\/|@\w+|[A-Za-z_$][A-Za-z0-9_$]*|\b\d+(?:\.\d+)?\b|\s+|.)/g) ?? [];
  return tokens.map((token) => {
    if (/^\s+$/.test(token)) {
      return token;
    }
    if (/^(\/\/|\/\*)/.test(token)) {
      return `<span class="code-token comment">${token}</span>`;
    }
    if (/^(&quot;|')/.test(token)) {
      return `<span class="code-token string">${token}</span>`;
    }
    if (/^@\w+/.test(token)) {
      return `<span class="code-token annotation">${token}</span>`;
    }
    if (/^(public|private|protected|class|interface|enum|record|extends|implements|static|final|void|int|long|double|float|boolean|char|byte|short|new|return|if|else|for|while|switch|case|break|continue|throw|throws|try|catch|finally|import|package|null|true|false|this|super)$/.test(token)) {
      return `<span class="code-token keyword">${token}</span>`;
    }
    if (/^\d+(?:\.\d+)?$/.test(token)) {
      return `<span class="code-token number">${token}</span>`;
    }
    if (/^[A-Z][A-Za-z0-9_$]*$/.test(token)) {
      return `<span class="code-token type">${token}</span>`;
    }
    return token;
  }).join('');
}

function escapeHtml(content: string) {
  return content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function toolDuration(tool: ToolEventView) {
  return formatDuration(tool.durationMillis ?? explicitDurationMillis(tool.event));
}

function chatItemDuration(item: ChatItem) {
  if (item.tool) {
    return toolDuration(item.tool);
  }
  return formatDuration(item.durationMillis ?? explicitDurationMillis(item.event));
}

function detailJson(event?: RepairEvent | null) {
  if (!event) {
    return '{}';
  }
  return JSON.stringify(event.details ?? {}, null, 2);
}

function formatSseEvent(event: RepairEvent) {
  const payload = {
    sessionId: event.sessionId,
    stage: event.stage,
    message: event.message,
    timestamp: event.timestamp,
    details: event.details,
  };
  return [
    `event: ${event.stage}`,
    ...JSON.stringify(payload, null, 2).split('\n').map((line, index) => (index === 0 ? `data: ${line}` : `  ${line}`)),
    '',
  ];
}

function splitDiffRows(hunk: RepairDiffHunk): SplitDiffRow[] {
  const rows: SplitDiffRow[] = [];
  let index = 0;
  while (index < hunk.lines.length) {
    const line = hunk.lines[index];
    if (line.type === 'delete') {
      const deletes: RepairDiffLine[] = [];
      const adds: RepairDiffLine[] = [];
      while (index < hunk.lines.length && hunk.lines[index]?.type === 'delete') {
        deletes.push(hunk.lines[index]);
        index += 1;
      }
      while (index < hunk.lines.length && hunk.lines[index]?.type === 'add') {
        adds.push(hunk.lines[index]);
        index += 1;
      }
      const pairCount = Math.max(deletes.length, adds.length);
      for (let pairIndex = 0; pairIndex < pairCount; pairIndex += 1) {
        rows.push({
          left: deletes[pairIndex] ?? null,
          right: adds[pairIndex] ?? null,
        });
      }
      continue;
    }
    if (line.type === 'add') {
      rows.push({ left: null, right: line });
      index += 1;
      continue;
    }
    rows.push({ left: line, right: line });
    index += 1;
  }
  return rows;
}

function outcomeClass(value?: string | null) {
  if (value === 'FIXED' || value === 'fixed' || value === 'COMPLETED' || value === 'done') {
    return 'success';
  }
  if (value === 'FAILED' || value === 'ERROR' || value === 'error' || value === 'failed') {
    return 'danger';
  }
  if (value === 'WAITING_FOR_TARGET_RESTART' || value === 'RUNNING' || value === 'running') {
    return 'warning';
  }
  return 'neutral';
}

function toggleTheme() {
  darkMode.value = !darkMode.value;
}

function toggleRunToolTrace() {
  runToolTraceCollapsed.value = !runToolTraceCollapsed.value;
}

function schedulePlanReveal(planKey: string, totalSegments: number) {
  if (
    planKey
    && activePlanRevealKey.value === planKey
    && (planRevealTimer !== undefined || visiblePlanSegments.value >= totalSegments)
  ) {
    return;
  }
  clearPlanRevealTimer();
  activePlanRevealKey.value = planKey;
  if (!planKey || totalSegments <= 0) {
    visiblePlanSegments.value = 0;
    return;
  }
  if (restoringPersistedState || !planTransitionReady.value || prefersReducedMotion()) {
    visiblePlanSegments.value = totalSegments;
    return;
  }
  visiblePlanSegments.value = 1;
  if (totalSegments <= 1) {
    return;
  }
  planRevealTimer = window.setInterval(() => {
    if (activePlanRevealKey.value !== planKey) {
      clearPlanRevealTimer();
      return;
    }
    const currentTotalSegments = planRevealTotalSegments.value || totalSegments;
    visiblePlanSegments.value += 1;
    if (visiblePlanSegments.value >= currentTotalSegments) {
      visiblePlanSegments.value = currentTotalSegments;
      clearPlanRevealTimer();
    }
  }, 280);
}

function clearPlanRevealTimer() {
  if (planRevealTimer !== undefined) {
    window.clearInterval(planRevealTimer);
    planRevealTimer = undefined;
  }
}

function isChatFeedNearBottom() {
  const feed = chatFeedRef.value;
  if (!feed) {
    return true;
  }
  return feed.scrollHeight - feed.scrollTop - feed.clientHeight <= 48;
}

function scrollChatFeedToBottom() {
  const feed = chatFeedRef.value;
  if (!feed) {
    return;
  }
  feed.scrollTo({
    top: feed.scrollHeight,
    behavior: prefersReducedMotion() ? 'auto' : 'smooth',
  });
}

function prefersReducedMotion() {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
}

</script>

<template>
  <main class="app-shell" :class="{ 'theme-dark': darkMode }">
    <header class="topbar" aria-label="Agent AI Ops summary">
      <div class="topbar-title">
        <h1>Agent 自动修复工作台</h1>
        <button class="theme-toggle" type="button" :aria-label="darkMode ? '切换浅色模式' : '切换深色模式'" @click="toggleTheme">
          <component :is="darkMode ? Sun : Moon" :size="16" />
        </button>
      </div>
      <div class="topbar-metrics" aria-live="polite">
        <span class="metric-cell wide">
          <strong>Session ID</strong>
          <code>{{ sessionId || '-' }}</code>
        </span>
        <span class="metric-cell">
          <strong>结果</strong>
          <em class="status-pill" :class="outcomeClass(terminalOutcome)">{{ terminalOutcome }}</em>
        </span>
        <span class="metric-cell">
          <strong>耗时</strong>
          <code>{{ formatDuration(currentDuration) }}</code>
        </span>
        <span class="metric-cell token-cell">
          <strong>Tokens</strong>
          <code>{{ tokenUsage.total !== null ? formatNumber(tokenUsage.total) : 'n/a' }}</code>
          <span class="token-input">input {{ tokenUsage.input !== null ? formatNumber(tokenUsage.input) : 'n/a' }}</span>
          <span class="token-output">output {{ tokenUsage.output !== null ? formatNumber(tokenUsage.output) : 'n/a' }}</span>
        </span>
        <span class="metric-cell">
          <strong>PR 状态</strong>
          <em class="status-pill" :class="currentPrUrl ? 'success' : 'neutral'">{{ prStatusLabel }}</em>
        </span>
        <span class="metric-cell">
          <strong>飞书状态</strong>
          <em class="status-pill" :class="feishuStatusClass">{{ feishuStatusLabel }}</em>
        </span>
      </div>
    </header>

    <p v-if="errorMessage" class="alert" role="alert">
      <CircleAlert :size="18" />
      <span>{{ errorMessage }}</span>
    </p>

    <div class="workspace">
      <nav class="workspace-nav" aria-label="Console pages">
        <div class="nav-mark" aria-hidden="true">
          <TerminalSquare :size="20" />
        </div>
        <button
          v-for="item in navItems"
          :key="item.id"
          type="button"
          class="nav-button"
          :class="{ selected: activeView === item.id }"
          :aria-current="activeView === item.id ? 'page' : undefined"
          @click="activeView = item.id"
        >
          <component :is="item.icon" :size="18" />
          <span>
            <strong>{{ item.label }}</strong>
            <small>{{ item.description }}</small>
          </span>
        </button>
      </nav>

      <section v-if="activeView === 'run'" class="view-grid run-view" aria-label="Run console">
        <aside class="panel control-panel" aria-labelledby="control-title">
          <div class="panel-heading">
            <div>
              <h2 id="control-title">故障列表</h2>
              <p class="muted-title">选择本次修复目标</p>
            </div>
            <button class="icon-button" type="button" @click="refreshReadiness" :disabled="loadingReadiness">
              <RefreshCw :size="18" />
              <span class="sr-only">刷新预检</span>
            </button>
          </div>

          <div id="fault-type" class="fault-list" role="radiogroup" aria-label="选择故障类型" :aria-busy="loadingFaults">
            <button
              v-for="fault in faultMetas"
              :key="fault.type"
              type="button"
              class="fault-option"
              :class="{ selected: selectedFault === fault.type }"
              :aria-pressed="selectedFault === fault.type"
              @click="selectedFault = fault.type"
            >
              <span class="fault-head">
                <span class="priority-badge" :class="fault.priority.toLowerCase()">{{ fault.priority }}</span>
                <strong>{{ fault.label }}</strong>
                <CheckCircle2 v-if="selectedFault === fault.type" :size="16" />
                <span v-else class="radio-dot"></span>
              </span>
              <span class="fault-copy">{{ fault.description }}</span>
              <span class="fault-evidence">{{ fault.evidence }}</span>
            </button>
          </div>

          <div class="session-row">
            <label class="field-label" for="session-id">Session ID</label>
            <button class="text-button" type="button" @click="newSessionId">
              <RotateCw :size="15" />
              生成
            </button>
          </div>
          <input
            id="session-id"
            v-model.trim="sessionId"
            class="text-input mono"
            autocomplete="off"
            spellcheck="false"
          />

          <div class="readiness-box" :aria-busy="loadingReadiness">
            <div class="readiness-header">
              <strong>就绪检查清单</strong>
              <span class="mini-status" :class="readiness?.ready ? 'success' : 'warning'">
                {{ readiness?.ready ? 'READY' : 'CHECK' }}
              </span>
            </div>
            <ul class="preflight-list">
              <li v-for="item in preflightChecks" :key="item.label" :class="item.ok ? 'ok' : 'bad'">
                <CheckCircle2 :size="14" />
                <span>{{ item.label }}</span>
              </li>
            </ul>
            <div class="readiness-grid">
              <span
                v-for="item in readinessItems"
                :key="item.label"
                class="check-chip"
                :class="item.enabled ? 'success' : 'danger'"
              >
                {{ item.label }}
              </span>
            </div>
            <dl class="branch-facts">
              <div>
                <dt>Expected</dt>
                <dd class="mono">{{ readiness?.expectedBaseBranch ?? activeFault.baseBranch }}</dd>
              </div>
              <div>
                <dt>Configured</dt>
                <dd class="mono">{{ readiness?.configuredBaseBranch ?? '-' }}</dd>
              </div>
            </dl>
            <ul v-if="readiness?.warnings?.length" class="warning-list">
              <li v-for="warning in readiness.warnings" :key="warning">{{ warning }}</li>
            </ul>
          </div>

          <button class="primary-button" type="button" :disabled="!canStart" @click="startDemo">
            <Play :size="18" />
            启动真实 PR Demo
          </button>

          <section class="service-box" aria-label="目标服务重启状态">
            <div class="run-table-heading">
              <strong>目标服务重启状态（只读）</strong>
            </div>
            <div class="service-table" role="table">
              <div class="service-row service-head" role="row">
                <span role="columnheader">服务</span>
                <span role="columnheader">实例</span>
                <span role="columnheader">状态</span>
                <span role="columnheader">最近重启</span>
              </div>
              <div v-for="row in serviceRows" :key="row.service" class="service-row" role="row">
                <span role="cell">{{ row.service }}</span>
                <span role="cell">{{ row.instance }}</span>
                <span role="cell"><i class="status-dot"></i>{{ row.status }}</span>
                <span role="cell" class="mono">{{ row.lastRestart }}</span>
              </div>
            </div>
          </section>

          <div v-if="autoRestartStatus !== 'idle' || waitingForRestart" class="restart-block">
            <div class="restart-heading">
              <TerminalSquare :size="18" />
              <span>target-service 重启</span>
              <span class="mini-status" :class="outcomeClass(autoRestartStatus)">
                {{ autoRestartStatus }}
              </span>
            </div>
            <p class="microcopy">
              {{ targetRestart?.message ?? '启动后会自动从 PR-safe worktree 重启 target-service；失败时可手动执行命令。' }}
            </p>
            <pre class="command-block">{{ targetRestart?.command ?? restartCommand }}</pre>
            <div v-if="waitingForRestart" class="restart-actions">
              <button class="secondary-button" type="button" @click="copyRestartCommand">
                <ClipboardCheck :size="16" />
                复制命令
              </button>
              <button class="primary-button" type="button" :disabled="runningAction" @click="confirmRestart">
                <CheckCircle2 :size="18" />
                确认已重启
              </button>
            </div>
            <p v-if="targetRestart?.logPath" class="microcopy mono">log={{ targetRestart.logPath }}</p>
            <p v-if="copyStatus" class="microcopy" aria-live="polite">{{ copyStatus }}</p>
          </div>
        </aside>

        <section class="panel chat-panel" aria-labelledby="chat-title">
          <div class="panel-heading">
            <div>
              <h2 id="chat-title">ChatOps 时间线</h2>
            </div>
            <span class="status-pill compact" :class="stream.connected.value ? 'success' : 'neutral'">
              <Activity :size="16" />
              {{ stream.connected.value ? 'streaming' : 'idle' }}
            </span>
          </div>

          <Transition name="pinned-plan" :css="planTransitionReady">
            <section
              v-if="pinnedPlanItem?.plan"
              class="timeline-pinned-plan"
              :class="{ selected: selectedChatItem?.id === pinnedPlanItem.id }"
              role="button"
              tabindex="0"
              aria-label="查看 AI 根因与计划详情"
              @click="pinnedPlanItem && selectChatItem(pinnedPlanItem)"
              @keydown.enter.prevent="pinnedPlanItem && selectChatItem(pinnedPlanItem)"
              @keydown.space.prevent="pinnedPlanItem && selectChatItem(pinnedPlanItem)"
            >
              <div class="pinned-plan-head">
                <span class="role-chip agent">AI 根因与计划</span>
                <strong>置顶计划</strong>
              </div>
              <TransitionGroup tag="div" name="plan-reveal" class="pinned-plan-body">
                <article v-if="shouldShowPlanRoot" key="root" class="plan-insight root">
                  <div class="plan-insight-label">
                    <CircleAlert :size="14" />
                    <strong>根因</strong>
                  </div>
                  <p>{{ pinnedPlanItem.plan.rootCause }}</p>
                </article>
                <article v-if="visiblePlanStepCount > 0" key="steps" class="plan-insight steps">
                  <div class="plan-insight-label">
                    <ListChecks :size="14" />
                    <strong>步骤</strong>
                  </div>
                  <TransitionGroup tag="ol" name="plan-reveal" class="pinned-plan-steps">
                    <li
                      v-for="(step, stepIndex) in pinnedPlanItem.plan.steps.slice(0, visiblePlanStepCount)"
                      :key="`${stepIndex}-${step}`"
                    >
                      {{ step }}
                    </li>
                  </TransitionGroup>
                </article>
                <article v-if="shouldShowPlanFiles" key="files" class="plan-insight files">
                  <div class="plan-insight-label">
                    <FileSearch :size="14" />
                    <strong>文件</strong>
                  </div>
                  <div class="plan-file-pills">
                    <code v-for="file in pinnedPlanItem.plan.files" :key="file">{{ shortTarget(file) }}</code>
                  </div>
                </article>
              </TransitionGroup>
            </section>
          </Transition>

          <div ref="chatFeedRef" class="chat-feed" role="log" aria-live="polite" aria-label="Agent repair messages">
            <TransitionGroup tag="div" name="timeline-list" class="timeline-list">
              <article
                v-for="item in chatItems"
                :key="item.id"
                class="timeline-row"
                :class="[item.role, outcomeClass(item.status), { selected: selectedChatItem?.id === item.id }]"
                role="button"
                tabindex="0"
                :aria-label="`查看 ${item.title} 详情`"
                @click="selectChatItem(item)"
                @keydown.enter.prevent="selectChatItem(item)"
                @keydown.space.prevent="selectChatItem(item)"
              >
                <time class="mono" :datetime="item.event?.timestamp">{{ item.event ? formatTime(item.event.timestamp) : '-' }}</time>
                <span class="role-chip" :class="item.role">{{ item.badgeLabel }}</span>
                <div class="timeline-message">
                  <strong>{{ item.tool?.toolName ?? item.title }}</strong>
                  <span>{{ displayChatBody(item) }}</span>
                  <section v-if="item.plan" class="timeline-plan-card" aria-label="AI root cause and repair plan">
                    <dl class="timeline-plan-facts">
                      <div>
                        <dt>根因</dt>
                        <dd>{{ item.plan.rootCause }}</dd>
                      </div>
                      <div>
                        <dt>修复目标</dt>
                        <dd>{{ item.plan.repairTarget }}</dd>
                      </div>
                      <div>
                        <dt>测试命令</dt>
                        <dd class="mono">{{ item.plan.testCommand }}</dd>
                      </div>
                    </dl>
                    <div v-if="item.plan.steps.length" class="timeline-plan-section">
                      <span>修复步骤</span>
                      <ol>
                        <li v-for="step in item.plan.steps" :key="step">{{ step }}</li>
                      </ol>
                    </div>
                    <div v-if="item.plan.files.length" class="timeline-plan-files">
                      <span>涉及文件</span>
                      <code v-for="file in item.plan.files" :key="file">{{ shortTarget(file) }}</code>
                    </div>
                  </section>
                  <section v-if="item.patch" class="timeline-patch-card" aria-label="Patch summary">
                    <div class="timeline-patch-head">
                      <span>自动补丁</span>
                      <strong>{{ item.patch.summary }}</strong>
                    </div>
                    <ul class="timeline-patch-list">
                      <li v-for="operation in item.patch.operations" :key="operation.filePath">
                        <code>{{ shortTarget(operation.filePath) }}</code>
                        <span>{{ operation.reason }}</span>
                        <em v-if="operation.lineSummary">{{ operation.lineSummary }}</em>
                      </li>
                    </ul>
                  </section>
                </div>
                <span class="duration mono">{{ chatItemDuration(item) }}</span>
                <CheckCircle2 class="row-check" :size="16" />
              </article>
            </TransitionGroup>
            <Transition name="next-step">
              <div v-if="nextTimelineHint" class="timeline-next-step" aria-live="polite">
                <ChevronRight :size="15" />
                <span>{{ nextTimelineHint }}</span>
              </div>
            </Transition>
          </div>

          <p v-if="stream.streamError.value" class="inline-warning" role="alert">
            {{ stream.streamError.value }}
          </p>

          <section class="run-tool-table" :class="{ collapsed: runToolTraceCollapsed }" aria-label="Tool trace in current run">
            <div class="run-table-heading">
              <span class="run-table-title">
                <strong>Tool Trace（本次运行）</strong>
                <em>{{ primaryToolEvents.length }} 条</em>
              </span>
              <span class="run-table-actions">
                <button class="text-button compact" type="button" @click="toggleRunToolTrace">
                  <component :is="runToolTraceCollapsed ? ChevronRight : ChevronDown" :size="15" />
                  {{ runToolTraceCollapsed ? '展开' : '隐藏' }}
                </button>
                <button class="text-button compact" type="button" @click="activeView = 'tools'">查看全部</button>
              </span>
            </div>
            <div v-if="!runToolTraceCollapsed" class="mini-tool-table" role="table">
              <div class="mini-tool-row mini-tool-head" role="row">
                <span role="columnheader">时间</span>
                <span role="columnheader">工具</span>
                <span role="columnheader">状态</span>
                <span role="columnheader">目标/输入</span>
                <span role="columnheader">耗时</span>
              </div>
              <div
                v-for="tool in primaryToolEvents"
                :key="tool.id"
                class="mini-tool-row mini-tool-data-row"
                :class="{ selected: selectedChatItem?.tool?.id === tool.id }"
                role="row"
                tabindex="0"
                @click="selectToolEvent(tool)"
                @keydown.enter.prevent="selectToolEvent(tool)"
                @keydown.space.prevent="selectToolEvent(tool)"
              >
                <span class="mono" role="cell">{{ formatDate(tool.event.timestamp) }}</span>
                <span role="cell">{{ tool.toolName }}</span>
                <span role="cell" class="mini-status" :class="outcomeClass(tool.status)">{{ tool.status }}</span>
                <span class="mono" role="cell">{{ shortTarget(tool.target || tool.summary) }}</span>
                <span class="mono" role="cell">{{ toolDuration(tool) }}</span>
              </div>
              <div v-if="primaryToolEvents.length === 0" class="mini-tool-row empty" role="row">
                <span role="cell">等待 ReadLog / ReadCode / PatchAgent / RunTest / GitCommit / GitHub PR / Feishu 工具事件。</span>
              </div>
            </div>
          </section>
        </section>

        <aside class="detail-panel" aria-label="Evidence and artifacts">
          <div class="right-top-grid">
            <section class="panel phase-panel" aria-labelledby="detail-title">
              <div class="panel-heading">
                <div>
                  <p class="eyebrow">当前阶段</p>
                  <h2 id="detail-title">{{ activeWorkbenchStage.label }}</h2>
                </div>
              </div>
              <ol class="compact-stage-list" aria-label="Repair stages">
                <li
                  v-for="stage in workbenchStages"
                  :key="stage.id"
                  class="stage-item"
                  :class="stageStateForWorkbench(stage)"
                >
                  <component :is="stage.icon" :size="17" />
                  <span>{{ stage.label }}</span>
                </li>
              </ol>
              <section class="active-tool-card" aria-live="polite">
                <span class="section-kicker">当前执行工具</span>
                <template v-if="activeTool">
                  <strong>{{ toolTitle(activeTool.toolName) }}</strong>
                  <dl class="detail-dl compact-dl">
                    <div><dt>目标</dt><dd class="mono">{{ activeTool.target }}</dd></div>
                    <div><dt>动作</dt><dd>{{ toolSentence(activeTool) }}</dd></div>
                    <div><dt>状态</dt><dd><i class="status-dot"></i>{{ activeTool.status }}</dd></div>
                    <div><dt>耗时</dt><dd class="mono">{{ toolDuration(activeTool) }}</dd></div>
                  </dl>
                </template>
                <p v-else>等待 Agent 调用 ReadLog / ReadCode / PatchAgent / RunTest / GitCommit。</p>
              </section>
            </section>

            <section class="panel sse-panel" aria-label="SSE 详情">
              <div class="panel-heading">
                <div>
                  <p class="eyebrow">SSE 详情（只读）</p>
                  <h2>{{ selectedChatItem?.title ?? inspectorEvent?.stage ?? 'SSE' }}</h2>
                </div>
                <button class="secondary-button compact" type="button" @click="copySseRaw">
                  {{ sseCopyStatus || '复制' }}
                </button>
              </div>
              <div class="sse-code" role="log" aria-live="polite">
                <div v-for="row in sseRawRows" :key="row.number" class="sse-line">
                  <span>{{ row.number }}</span>
                  <code>{{ row.text }}</code>
                </div>
              </div>
            </section>
          </div>

          <section class="panel artifact-panel">
            <div class="panel-heading">
              <div>
                <h2>Artifacts</h2>
              </div>
              <div class="segmented">
                <button type="button" :class="{ selected: diffViewMode === 'unified' }" @click="diffViewMode = 'unified'">统一视图</button>
                <button type="button" :class="{ selected: diffViewMode === 'split' }" @click="diffViewMode = 'split'">Split</button>
                <a v-if="selectedPrUrl" :href="selectedPrUrl" target="_blank" rel="noreferrer">
                  View PR
                  <ExternalLink :size="14" />
                </a>
              </div>
            </div>
            <div v-if="reviewDiffFiles.length && selectedDiffFile" class="artifact-diff">
              <aside class="changed-files" aria-label="Files changed">
                <strong>Files changed ({{ reviewDiffFiles.length }})</strong>
                <button
                  v-for="file in reviewDiffFiles"
                  :key="file.filePath"
                  type="button"
                  class="changed-file"
                  :class="{ selected: selectedDiffFile.filePath === file.filePath }"
                  @click="selectDiffFile(file.filePath)"
                >
                  <span class="mono">{{ file.filePath }}</span>
                  <small><b class="diff-stat add">+{{ file.additions }}</b> <b class="diff-stat delete">-{{ file.deletions }}</b></small>
                </button>
              </aside>
              <section class="diff-surface">
                <div class="preview-file-head">
                  <span class="mono">{{ selectedDiffFile.filePath }}</span>
                  <span class="diff-stat add">+{{ selectedDiffFile.additions }}</span>
                  <span class="diff-stat delete">-{{ selectedDiffFile.deletions }}</span>
                </div>
                <template v-if="diffViewMode === 'split'">
                  <section v-for="hunk in selectedDiffFile.hunks" :key="hunk.header" class="split-hunk">
                    <div class="preview-hunk mono">{{ hunk.header }}</div>
                    <div class="split-diff-grid">
                      <template v-for="(row, rowIndex) in splitDiffRows(hunk)" :key="`${hunk.header}-${rowIndex}`">
                        <div
                          class="preview-diff-line split-side"
                          :class="[diffLineClass(row.left?.type ?? 'empty'), { 'line-placeholder': !row.left }]"
                        >
                          <span class="mono">{{ row.left?.oldLineNumber ?? '' }}</span>
                          <code v-html="highlightCode(row.left?.content ?? '')"></code>
                        </div>
                        <div
                          class="preview-diff-line split-side"
                          :class="[diffLineClass(row.right?.type ?? 'empty'), { 'line-placeholder': !row.right }]"
                        >
                          <span class="mono">{{ row.right?.newLineNumber ?? '' }}</span>
                          <code v-html="highlightCode(row.right?.content ?? '')"></code>
                        </div>
                      </template>
                    </div>
                  </section>
                </template>
                <template v-else>
                  <section v-for="hunk in selectedDiffFile.hunks" :key="hunk.header" class="preview-diff-lines">
                    <div class="preview-hunk mono">{{ hunk.header }}</div>
                    <div
                      v-for="(line, lineIndex) in hunk.lines"
                      :key="`${hunk.header}-${lineIndex}`"
                      class="preview-diff-line"
                      :class="diffLineClass(line.type)"
                    >
                      <span class="mono">{{ diffLinePrefix(line.type) }}</span>
                      <code v-html="highlightCode(line.content)"></code>
                    </div>
                  </section>
                </template>
              </section>
            </div>
            <pre v-else-if="hasRawDiffFallback" class="diff-block">{{ visibleRecord?.diffSummary }}</pre>
            <p v-else class="empty-copy">完成修复后，这里会直接显示 PR 的代码删改。</p>
          </section>
        </aside>
      </section>

      <section v-else-if="activeView === 'tools'" class="view-grid tools-view" aria-labelledby="tools-title">
        <section class="panel tools-main">
          <div class="panel-heading">
            <div>
              <p class="eyebrow">Tool Use Evidence</p>
              <h2 id="tools-title">工具调用全过程</h2>
            </div>
            <span class="status-pill neutral">
              <Wrench :size="16" />
              {{ allToolEvents.length }} events
            </span>
          </div>

          <div class="coverage-grid">
            <article
              v-for="item in requiredToolCoverage"
              :key="item.toolName"
              class="coverage-card"
              :class="item.present ? 'success' : 'neutral'"
            >
              <component :is="toolIcon(item.toolName)" :size="20" />
              <strong>{{ toolTitle(item.toolName) }}</strong>
              <span>{{ item.present ? '已展示' : '等待事件' }}</span>
            </article>
          </div>

          <div v-if="allToolEvents.length === 0" class="empty-state">
            <Wrench :size="24" />
            <p>启动 Demo 后，这里会按时间展示 ReadLog、ReadCode、PatchAgent、RunTest、GitCommit 等工具调用。</p>
          </div>

          <div v-else class="tool-event-list" role="list" aria-label="Tool events">
            <article
              v-for="tool in allToolEvents"
              :key="tool.id"
              class="tool-event-row"
              :class="outcomeClass(tool.status)"
              role="listitem"
            >
              <div class="tool-event-icon" aria-hidden="true">
                <component :is="toolIcon(tool.toolName)" :size="18" />
              </div>
              <div>
                <div class="tool-event-title">
                  <strong>{{ toolTitle(tool.toolName) }}</strong>
                  <span class="mini-status" :class="outcomeClass(tool.status)">{{ tool.status }}</span>
                </div>
                <p>{{ toolSentence(tool) }}</p>
                <details class="tool-details">
                  <summary>查看 target / status / summary / raw details</summary>
                  <dl class="detail-dl">
                    <div>
                      <dt>target</dt>
                      <dd class="mono">{{ tool.target }}</dd>
                    </div>
                    <div>
                      <dt>duration</dt>
                      <dd class="mono">{{ toolDuration(tool) }}</dd>
                    </div>
                    <div>
                      <dt>summary</dt>
                      <dd>{{ tool.summary }}</dd>
                    </div>
                  </dl>
                  <pre class="raw-json-block">{{ detailJson(tool.event) }}</pre>
                </details>
              </div>
              <time :datetime="tool.event.timestamp">{{ formatDate(tool.event.timestamp) }}</time>
            </article>
          </div>
        </section>

        <aside class="panel detail-panel">
          <div class="panel-heading">
            <div>
              <p class="eyebrow">Raw Target</p>
              <h2>最近工具详情</h2>
            </div>
          </div>
          <template v-if="activeTool">
            <dl class="detail-dl">
              <div>
                <dt>tool</dt>
                <dd class="mono">{{ activeTool.toolName }}</dd>
              </div>
              <div>
                <dt>eventType</dt>
                <dd class="mono">{{ activeTool.action }}</dd>
              </div>
              <div>
                <dt>target</dt>
                <dd class="mono">{{ activeTool.target }}</dd>
              </div>
              <div>
                <dt>success</dt>
                <dd class="mono">{{ formatBoolean(activeTool.success) }}</dd>
              </div>
            </dl>
          </template>
          <p v-else>还没有工具事件。</p>
        </aside>
      </section>

      <section v-else-if="activeView === 'records'" class="records-view" aria-labelledby="records-title">
        <section class="panel records-panel">
          <div class="panel-heading">
            <div>
              <p class="eyebrow">Records</p>
              <h2 id="records-title">修复记录归档</h2>
            </div>
            <button class="secondary-button" type="button" :disabled="loadingRecords" @click="loadRecords">
              <RefreshCw :size="16" />
              刷新记录
            </button>
          </div>

          <div v-if="records.length === 0" class="empty-state">
            <History :size="24" />
            <p>暂无修复记录。完成一次 PR-safe Demo 后，这里会展示 outcome、测试、PR、飞书和 token 摘要。</p>
          </div>

          <div v-else class="records-table" role="table" aria-label="Repair records">
            <div class="records-row records-head" role="row">
              <span role="columnheader">Session</span>
              <span role="columnheader">Outcome</span>
              <span role="columnheader">Duration</span>
              <span role="columnheader">Tests</span>
              <span role="columnheader">PR / Feishu</span>
              <span role="columnheader">Action</span>
            </div>
            <div v-for="record in records" :key="record.sessionId" class="records-row" role="row">
              <span class="mono" role="cell">{{ record.sessionId }}</span>
              <span role="cell" class="mini-status" :class="outcomeClass(record.outcome)">{{ record.outcome }}</span>
              <span role="cell">{{ formatDuration(record.durationMillis) }}</span>
              <span role="cell">{{ formatBoolean(record.testSuccess) }}</span>
              <span role="cell">{{ record.prUrl ? 'PR yes' : 'PR no' }} / Feishu {{ formatBoolean(record.notificationSuccess) }}</span>
              <button class="text-button" type="button" role="cell" @click="openRecord(record)">
                <ListChecks :size="15" />
                查看详情
              </button>
            </div>
          </div>
        </section>

        <section class="panel record-detail">
          <div class="panel-heading">
            <div>
              <p class="eyebrow">Root Cause -> Solution -> Evidence</p>
              <h2>根因 / 计划 / 测试 / 反思</h2>
            </div>
          </div>

          <div class="detail-stack">
            <section class="detail-section">
              <span class="section-kicker">Root Cause</span>
              <p>{{ visibleRecord?.plan?.rootCause ?? scenario?.evidenceSummary ?? '等待 Agent 读取 Traceback 并生成诊断。' }}</p>
            </section>
            <section class="detail-section">
              <span class="section-kicker">Repair Plan</span>
              <p>{{ visibleRecord?.plan?.repairTarget ?? 'AI Plan Agent 输出后会显示修复目标。' }}</p>
              <ul v-if="visibleRecord?.plan?.patchStrategy?.length" class="compact-list">
                <li v-for="item in visibleRecord.plan.patchStrategy" :key="item">{{ item }}</li>
              </ul>
            </section>
            <section class="detail-section">
              <span class="section-kicker">Code Changes</span>
              <p>{{ visibleRecord?.patchProposal?.summary ?? visibleRecord?.patchApplicationResult?.message ?? '等待 Patch Agent 生成安全补丁。' }}</p>
              <button class="text-button" type="button" @click="activeView = 'artifacts'">
                <FileDiff :size="15" />
                查看 PR Diff
              </button>
            </section>
            <section class="detail-section split-facts">
              <div>
                <span class="section-kicker">Tests</span>
                <strong>{{ formatBoolean(visibleRecord?.testResult?.success) }}</strong>
                <p>exitCode={{ visibleRecord?.testResult?.exitCode ?? '-' }}</p>
              </div>
              <div>
                <span class="section-kicker">Review</span>
                <strong>{{ visibleRecord?.reviewDecision?.status ?? '-' }}</strong>
                <p>{{ visibleRecord?.reviewDecision?.risk ?? visibleRecord?.reviewDecision?.riskLevel ?? 'risk n/a' }}</p>
              </div>
            </section>
            <section class="detail-section split-facts">
              <div>
                <span class="section-kicker">PR</span>
                <a v-if="visibleRecord?.pullRequestResult?.url || scenario?.prUrl" :href="visibleRecord?.pullRequestResult?.url || scenario?.prUrl" target="_blank" rel="noreferrer">
                  View PR
                </a>
                <strong v-else>-</strong>
              </div>
              <div>
                <span class="section-kicker">Feishu</span>
                <strong>{{ formatBoolean(visibleRecord?.notificationResult?.success ?? scenario?.notificationSuccess) }}</strong>
              </div>
            </section>
            <section class="detail-section">
              <span class="section-kicker">Reflection</span>
              <p>{{ visibleRecord?.reflection?.lessonsLearned ?? '修复完成后展示反思沉淀。' }}</p>
            </section>
          </div>
        </section>
      </section>

      <section v-else-if="activeView === 'artifacts'" class="diff-view" aria-labelledby="diff-title">
        <section class="panel diff-panel">
          <div class="panel-heading">
            <div>
              <p class="eyebrow">GitHub-like Files Changed</p>
              <h2 id="diff-title">PR 代码删改审查</h2>
            </div>
            <a v-if="selectedPrUrl" class="secondary-link-button" :href="selectedPrUrl" target="_blank" rel="noreferrer">
              <ExternalLink :size="16" />
              View PR
            </a>
          </div>

          <div class="diff-summary-strip">
            <span class="status-pill" :class="visibleRecord?.outcome === 'FIXED' ? 'success' : outcomeClass(visibleRecord?.outcome)">
              {{ visibleRecord?.outcome ?? 'NO RECORD' }}
            </span>
            <span class="status-pill neutral">
              <FileDiff :size="16" />
              {{ reviewDiffFiles.length }} files
            </span>
            <span class="status-pill success">
              +{{ reviewDiffFiles.reduce((sum, file) => sum + (file.additions ?? 0), 0) }}
            </span>
            <span class="status-pill danger">
              -{{ reviewDiffFiles.reduce((sum, file) => sum + (file.deletions ?? 0), 0) }}
            </span>
          </div>

          <div v-if="reviewDiffFiles.length" class="diff-file-list">
            <article v-for="file in reviewDiffFiles" :key="file.filePath" class="diff-file">
              <button class="diff-file-header" type="button" @click="toggleDiffFile(file.filePath)">
                <component :is="isDiffFileCollapsed(file.filePath) ? ChevronRight : ChevronDown" :size="17" />
                <strong class="mono">{{ file.filePath }}</strong>
                <span class="mini-status neutral">{{ file.status }}</span>
                <span class="diff-stat add">+{{ file.additions }}</span>
                <span class="diff-stat delete">-{{ file.deletions }}</span>
              </button>

              <div v-if="!isDiffFileCollapsed(file.filePath)" class="diff-hunks">
                <section v-for="hunk in file.hunks" :key="`${file.filePath}-${hunk.header}`" class="diff-hunk">
                  <div class="diff-hunk-header mono">{{ hunk.header }}</div>
                  <div
                    v-for="(line, lineIndex) in hunk.lines"
                    :key="`${hunk.header}-${lineIndex}`"
                    class="diff-line"
                    :class="diffLineClass(line.type)"
                  >
                    <span class="line-number mono">{{ line.oldLineNumber ?? '' }}</span>
                    <span class="line-number mono">{{ line.newLineNumber ?? '' }}</span>
                    <span class="line-prefix mono">{{ diffLinePrefix(line.type) }}</span>
                    <code v-html="highlightCode(line.content)"></code>
                  </div>
                </section>
              </div>
            </article>
          </div>

          <pre v-else-if="hasRawDiffFallback" class="diff-block">{{ visibleRecord?.diffSummary }}</pre>

          <div v-else class="empty-state">
            <FileDiff :size="24" />
            <p>暂无可展示 diff。完成新的 PR-safe Demo 后，记录会包含提交前捕获的结构化 `diffFiles`。</p>
          </div>
        </section>
      </section>

      <section v-else class="score-view" aria-labelledby="score-title">
        <section class="panel score-panel">
          <div class="panel-heading">
            <div>
              <p class="eyebrow">Judge Evidence</p>
              <h2 id="score-title">评分维度证据</h2>
            </div>
          </div>
          <div class="evidence-grid">
            <article v-for="item in scoreEvidence" :key="item.title" class="evidence-item">
              <span class="section-kicker">{{ item.title }}</span>
              <p>{{ item.body }}</p>
            </article>
          </div>
        </section>

        <section class="panel architecture-panel">
          <div class="panel-heading">
            <div>
              <p class="eyebrow">Closed Loop</p>
              <h2>演示闭环</h2>
            </div>
          </div>
          <ol class="flow-list">
            <li>服务报错并写入 Traceback</li>
            <li>Agent 调用 ReadLog / ReadCode 定位根因</li>
            <li>Patch Agent 生成安全补丁</li>
            <li>Run Test 通过后进入 Review gate</li>
            <li>Git Commit 推送 repair 分支并创建 PR</li>
            <li>飞书卡片通知 Review，记录反思沉淀</li>
          </ol>
        </section>
      </section>
    </div>
  </main>
</template>
