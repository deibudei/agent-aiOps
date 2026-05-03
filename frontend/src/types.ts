export type FaultType =
  | 'quantity-division-by-zero'
  | 'wrong-quote-route'
  | 'wrong-error-status';

export type DemoScenarioStage =
  | 'CREATED'
  | 'WAITING_FOR_TARGET_RESTART'
  | 'RUNNING'
  | 'FIXED'
  | 'FAILED'
  | 'ERROR';

export type RepairOutcome = 'FIXED' | 'FAILED' | 'ERROR';

export interface DemoFaultResult {
  faultType: FaultType;
  success: boolean;
  message: string;
  changedFiles: string[];
  nextSteps: string[];
}

export interface DemoScenarioResult {
  sessionId: string;
  faultType: FaultType;
  stage: DemoScenarioStage;
  success: boolean;
  message: string;
  changedFiles: string[];
  nextSteps: string[];
  repairStreamUrl?: string | null;
  targetServiceUrl: string;
  evidenceSummary: string;
  branchName: string;
  worktreePath: string;
  prUrl: string;
  notificationSuccess: boolean | null;
  recordJsonPath: string;
  recordMarkdownPath: string;
  outcomeReason: string;
}

export interface DemoTargetRestartResult {
  sessionId: string;
  success: boolean;
  message: string;
  worktreePath: string;
  command: string;
  pid: number | null;
  logPath: string;
  nextSteps: string[];
}

export interface DemoPrScenarioReadiness {
  faultType: FaultType;
  expectedBaseBranch: string;
  configuredBaseBranch: string;
  baseBranchMatches: boolean;
  llmEnabled: boolean;
  gitEnabled: boolean;
  githubEnabled: boolean;
  feishuEnabled: boolean;
  worktreeRoot: string;
  ready: boolean;
  warnings: string[];
}

export interface RepairEvent {
  sessionId: string;
  stage: RepairStage;
  message: string;
  timestamp: string;
  details: Record<string, unknown>;
}

export type RepairStage =
  | 'detecting'
  | 'planning'
  | 'executing'
  | 'patching'
  | 'testing'
  | 'reviewing'
  | 'committing'
  | 'pr_created'
  | 'notified'
  | 'reflecting'
  | 'completed'
  | 'error';

export interface RepairRecordIndex {
  count: number;
  records: RepairRecordSummary[];
}

export interface RepairRecordSummary {
  sessionId: string;
  outcome: RepairOutcome;
  outcomeReason: string;
  startedAt: string;
  completedAt: string;
  durationMillis: number | null;
  patchAttempts: number;
  totalTokens: number | null;
  testSuccess: boolean | null;
  testExitCode: number | null;
  prUrl: string;
  notificationSuccess: boolean | null;
  recordPath: string;
}

export interface RepairRecord {
  recordVersion: number;
  sessionId: string;
  startedAt: string;
  completedAt: string;
  outcome: RepairOutcome;
  outcomeReason: string;
  tracebackSummary?: string;
  plan?: {
    repairTarget?: string;
    rootCause?: string;
    targetFiles?: string[];
    patchStrategy?: string[];
    testCommand?: string;
  };
  stepResults?: Array<{
    toolName?: string;
    action?: string;
    summary?: string;
    success?: boolean;
  }>;
  patchProposal?: {
    summary?: string;
    operations?: Array<{
      path?: string;
      filePath?: string;
      oldText?: string;
      newText?: string;
      reason?: string;
    }>;
  };
  patchApplicationResult?: {
    success?: boolean;
    message?: string;
    changedFiles?: string[];
  };
  diffSummary?: string;
  diffFiles?: RepairDiffFile[];
  testResult?: {
    exitCode?: number;
    stdout?: string;
    stderr?: string;
    durationMillis?: number;
    timedOut?: boolean;
    success?: boolean;
  };
  reviewDecision?: {
    status?: string;
    reason?: string;
    risk?: string;
    riskLevel?: string;
    changedFiles?: string[];
    touchedFiles?: string[];
  };
  pullRequestResult?: {
    success?: boolean;
    url?: string;
    message?: string;
  };
  notificationResult?: {
    success?: boolean;
    message?: string;
  };
  reflection?: {
    rootCause?: string;
    evidence?: string;
    fixStrategy?: string;
    tests?: string;
    lessonsLearned?: string;
    futureHints?: string[];
  };
  timing?: {
    durationMillis?: number;
    steps?: Array<{
      stepName?: string;
      durationMillis?: number;
      success?: boolean;
      summary?: string;
    }>;
    modelUsage?: Array<{
      stepName?: string;
      role?: string;
      configuredModel?: string;
      responseModel?: string;
      callCount?: number;
      inputTokenCount?: number | null;
      outputTokenCount?: number | null;
      totalTokenCount?: number | null;
    }>;
  };
}

export interface RepairDiffFile {
  filePath: string;
  oldPath?: string;
  newPath?: string;
  status: string;
  additions: number;
  deletions: number;
  hunks: RepairDiffHunk[];
}

export interface RepairDiffHunk {
  header: string;
  oldStart: number;
  oldLines: number;
  newStart: number;
  newLines: number;
  lines: RepairDiffLine[];
}

export interface RepairDiffLine {
  type: 'add' | 'delete' | 'context' | 'meta' | string;
  oldLineNumber?: number | null;
  newLineNumber?: number | null;
  content: string;
}
