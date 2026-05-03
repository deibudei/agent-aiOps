<script setup lang="ts">
import type { Component } from 'vue';
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
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
  LayoutDashboard,
  ListChecks,
  MessagesSquare,
  Play,
  Radio,
  RefreshCw,
  RotateCw,
  ShieldCheck,
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
}

interface ChatItem {
  id: string;
  role: ChatRole;
  badgeLabel: string;
  title: string;
  body: string;
  meta: string;
  status: string;
  event?: RepairEvent;
  tool?: ToolEventView;
  plan?: DisplayPlan;
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

interface PersistedState {
  activeView?: ActiveView;
  selectedFault?: FaultType;
  sessionId?: string;
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
];

const stageDefs: StageDef[] = [
  { stage: 'detecting', label: '读取 Traceback', description: 'ReadLog 收集日志证据', icon: Radio },
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
  { id: 'score', label: 'Judge Evidence', description: '评分证据映射', icon: LayoutDashboard },
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

let restoringPersistedState = true;
let skipNextFaultReset = false;
let clockTimer: number | undefined;

const activeFault = computed(() => faultMetas.find((fault) => fault.type === selectedFault.value) ?? faultMetas[0]);
const currentEvents = computed(() =>
  stream.events.value
    .filter((event) => !event.sessionId || event.sessionId === sessionId.value)
    .slice()
    .sort((left, right) => new Date(left.timestamp).getTime() - new Date(right.timestamp).getTime()),
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
const currentDuration = computed(() => {
  const eventDuration = numberDetail(completedEvent.value, 'durationMillis');
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
  ['ReadLog', 'ReadCode', 'SearchCode', 'RunTest', 'GitCommit', 'GitHub PR', 'Feishu'].map((toolName) => ({
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
    });
  }

  const mergedTools = new Map<string, ToolEventView>();
  for (const tool of primaryToolEvents.value) {
    const key = `${tool.toolName}|${tool.target}`;
    const existing = mergedTools.get(key);
    if (!existing || toolStatusRank(tool.status) >= toolStatusRank(existing.status)) {
      mergedTools.set(key, tool);
    }
  }
  for (const tool of mergedTools.values()) {
    items.push(chatItemFromTool(tool));
  }

  const planningEntry = [...currentEvents.value.entries()]
    .reverse()
    .find(([, event]) => event.stage === 'planning');
  if (planningEntry) {
    const [index, event] = planningEntry;
    items.push(chatItemFromPlan(event, index, displayPlan.value));
  }

  for (const [index, event] of currentEvents.value.entries()) {
    if (event.stage === 'planning' || isToolEvent(event) || isNoisyAgentEvent(event)) {
      continue;
    }
    const item = chatItemFromEvent(event, index);
    if (item) {
      items.push(item);
    }
  }
  return items.sort((left, right) => itemTime(left) - itemTime(right));
});

const latestChatItem = computed(() => {
  const items = chatItems.value;
  return items.length === 0 ? null : items[items.length - 1];
});
const selectedToolEvent = computed(() => allToolEvents.value.find((tool) => tool.id === selectedChatItemId.value) ?? null);
const selectedChatItem = computed(() => {
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
  collectTokenUsage(unknownArrayDetail(completedEvent.value, 'modelUsage'), currentRecord.value?.timing?.modelUsage),
);
const prStatusLabel = computed(() => (currentPrUrl.value ? '已创建' : '未创建'));
const feishuStatusLabel = computed(() => {
  const event = [...currentEvents.value].reverse().find((item) => item.stage === 'notified');
  if (event) {
    if (stringDetail(event, 'status') === 'failed' || booleanDetail(event, 'success') === false) {
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
  { label: '测试环境可用', ok: readiness.value?.baseBranchMatches ?? false },
  { label: '日志中心可访问', ok: faults.value.length > 0 },
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
    scenario: scenario.value,
    targetRestart: targetRestart.value,
    currentRecord: currentRecord.value,
    selectedRecord: selectedRecord.value,
    events: stream.events.value,
  }),
  (state) => persistState(state),
  { deep: true },
);

onMounted(async () => {
  restoreState();
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
    || ['RunTest', 'GitCommit', 'ReadLog', 'ReadCode', 'SearchCode'].includes(stringDetail(event, 'toolName'))
    || isEvidenceReadLogEvent(event)
    || event.stage === 'pr_created'
    || event.stage === 'notified';
}

function isPrimaryTool(toolName: string) {
  return ['ReadLog', 'ReadCode', 'SearchCode', 'RunTest', 'GitCommit', 'GitHub PR', 'Feishu'].includes(toolName);
}

function isNoisyAgentEvent(event: RepairEvent) {
  const eventType = stringDetail(event, 'eventType');
  if (eventType === 'agent_tool_started' || eventType === 'agent_tool_completed') {
    return true;
  }
  return stringDetail(event, 'toolName') === 'AgentTool';
}

function isEvidenceReadLogEvent(event: RepairEvent) {
  if (event.stage !== 'detecting') {
    return false;
  }
  return event.message.includes('EvidenceAgent collecting')
    || event.message.includes('Evidence collected')
    || Boolean(asRecord(event.details?.evidence));
}

function toToolEvent(event: RepairEvent, index: number): ToolEventView {
  const toolName = stringDetail(event, 'toolName') || (isEvidenceReadLogEvent(event) ? 'ReadLog' : stageToolName(event.stage));
  const inferredSuccess = event.stage === 'pr_created'
    ? nestedBooleanDetail(event, 'pullRequest', 'success')
    : event.stage === 'notified'
      ? nestedBooleanDetail(event, 'notification', 'success')
      : null;
  const status = stringDetail(event, 'status')
    || (event.message.includes('Creating') || event.message.includes('Sending') || event.message.includes('collecting') ? 'running' : '')
    || (booleanDetail(event, 'success') === false || inferredSuccess === false ? 'failed' : 'completed');
  const success = booleanDetail(event, 'success') ?? inferredSuccess;
  const target = stringDetail(event, 'target') || stringDetail(event, 'branchName') || (toolName === 'ReadLog' ? 'target-service/logs' : event.stage);
  const summary = stringDetail(event, 'summary') || event.message;
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
  };
}

function chatItemFromTool(tool: ToolEventView): ChatItem {
  return {
    id: tool.id,
    role: 'tool',
    badgeLabel: 'Tool',
    title: toolTitle(tool.toolName),
    body: toolSentence(tool),
    meta: `${tool.action} / ${formatDate(tool.event.timestamp)}`,
    status: tool.status,
    event: tool.event,
    tool,
  };
}

function chatItemFromPlan(event: RepairEvent, index: number, plan: DisplayPlan): ChatItem {
  return {
    id: `${event.timestamp}-${index}-plan`,
    role: 'agent',
    badgeLabel: stageLabel(event.stage),
    title: 'AI 根因与计划',
    body: event.message,
    meta: `${event.stage} / ${formatDate(event.timestamp)}`,
    status: stringDetail(event, 'status') || event.stage,
    event,
    plan,
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

function recordDuration(record: RepairRecord) {
  if (!record.startedAt || !record.completedAt) {
    return null;
  }
  return new Date(record.completedAt).getTime() - new Date(record.startedAt).getTime();
}

function itemTime(item: ChatItem) {
  return item.event ? new Date(item.event.timestamp).getTime() : 0;
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

function detailDuration(event?: RepairEvent | null) {
  if (!event) {
    return '-';
  }
  const value = event.details?.durationMillis;
  return typeof value === 'number' ? formatDuration(value) : '-';
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

function leftSplitLines(hunk: RepairDiffHunk): RepairDiffLine[] {
  return hunk.lines.filter((line) => line.type !== 'add');
}

function rightSplitLines(hunk: RepairDiffHunk): RepairDiffLine[] {
  return hunk.lines.filter((line) => line.type !== 'delete');
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

</script>

<template>
  <main class="app-shell">
    <header class="topbar" aria-label="Agent AI Ops summary">
      <div class="topbar-title">
        <h1>Agent 自动修复工作台</h1>
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
          <span class="token-up">↑ {{ tokenUsage.input !== null ? formatNumber(tokenUsage.input) : 'n/a' }}</span>
          <span class="token-down">↓ {{ tokenUsage.output !== null ? formatNumber(tokenUsage.output) : 'n/a' }}</span>
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
        <div class="nav-env">
          <strong>赛训环境</strong>
          <span><i></i> online</span>
          <small>v1.0.0-rc.3</small>
        </div>
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

          <div class="chat-feed" role="log" aria-live="polite" aria-label="Agent repair messages">
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
              </div>
              <span class="duration mono">{{ item.event ? detailDuration(item.event) : '-' }}</span>
              <CheckCircle2 class="row-check" :size="16" />
            </article>
          </div>

          <p v-if="stream.streamError.value" class="inline-warning" role="alert">
            {{ stream.streamError.value }}
          </p>

          <section class="run-tool-table" aria-label="Tool trace in current run">
            <div class="run-table-heading">
              <strong>Tool Trace（本次运行）</strong>
              <button class="text-button" type="button" @click="activeView = 'tools'">查看全部</button>
            </div>
            <div class="mini-tool-table" role="table">
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
                <span class="mono" role="cell">{{ detailDuration(tool.event) }}</span>
              </div>
              <div v-if="primaryToolEvents.length === 0" class="mini-tool-row empty" role="row">
                <span role="cell">等待 ReadLog / ReadCode / RunTest / GitCommit / GitHub PR / Feishu 工具事件。</span>
              </div>
            </div>
          </section>

          <div class="timeline-input">
            <input class="text-input" disabled value="输入消息（支持 /help 查看指令）" />
            <button class="primary-button compact" type="button" disabled>发送</button>
          </div>
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
                    <div><dt>耗时</dt><dd class="mono">{{ detailDuration(activeTool.event) }}</dd></div>
                  </dl>
                </template>
                <p v-else>等待 Agent 调用 ReadLog / ReadCode / RunTest / GitCommit。</p>
              </section>
            </section>

            <section class="panel sse-panel" aria-label="端给 SSE 详情">
              <div class="panel-heading">
                <div>
                  <p class="eyebrow">端给 SSE 详情（只读）</p>
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
                  <section v-for="hunk in selectedDiffFile.hunks.slice(0, 2)" :key="hunk.header" class="split-hunk">
                    <div class="preview-hunk mono">{{ hunk.header }}</div>
                    <div class="split-diff-grid">
                      <div class="split-column">
                        <div
                          v-for="(line, lineIndex) in leftSplitLines(hunk).slice(0, 16)"
                          :key="`left-${hunk.header}-${lineIndex}`"
                          class="preview-diff-line"
                          :class="diffLineClass(line.type)"
                        >
                          <span class="mono">{{ line.oldLineNumber ?? '' }}</span>
                          <code>{{ line.content }}</code>
                        </div>
                      </div>
                      <div class="split-column">
                        <div
                          v-for="(line, lineIndex) in rightSplitLines(hunk).slice(0, 16)"
                          :key="`right-${hunk.header}-${lineIndex}`"
                          class="preview-diff-line"
                          :class="diffLineClass(line.type)"
                        >
                          <span class="mono">{{ line.newLineNumber ?? '' }}</span>
                          <code>{{ line.content }}</code>
                        </div>
                      </div>
                    </div>
                  </section>
                </template>
                <template v-else>
                  <section v-for="hunk in selectedDiffFile.hunks.slice(0, 2)" :key="hunk.header" class="preview-diff-lines">
                    <div class="preview-hunk mono">{{ hunk.header }}</div>
                    <div
                      v-for="(line, lineIndex) in hunk.lines.slice(0, 26)"
                      :key="`${hunk.header}-${lineIndex}`"
                      class="preview-diff-line"
                      :class="diffLineClass(line.type)"
                    >
                      <span class="mono">{{ diffLinePrefix(line.type) }}</span>
                      <code>{{ line.content }}</code>
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
            <p>启动 Demo 后，这里会按时间展示 ReadLog、ReadCode、RunTest、GitCommit 等工具调用。</p>
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
                      <dd class="mono">{{ detailDuration(tool.event) }}</dd>
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
                    <code>{{ line.content }}</code>
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
