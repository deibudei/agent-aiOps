import type {
  DemoFaultResult,
  DemoPrScenarioReadiness,
  DemoScenarioResult,
  DemoTargetRestartResult,
  FaultType,
  RepairRecord,
  RepairRecordIndex,
} from '@/types';

async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
    ...init,
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `${response.status} ${response.statusText}`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export function useRepairApi() {
  function listFaults() {
    return apiFetch<DemoFaultResult[]>('/api/demo/faults');
  }

  function readiness(faultType: FaultType) {
    return apiFetch<DemoPrScenarioReadiness>(
      `/api/demo/pr-scenarios/readiness?faultType=${encodeURIComponent(faultType)}`,
    );
  }

  function startPrScenario(sessionId: string, faultType: FaultType) {
    return apiFetch<DemoScenarioResult>('/api/demo/pr-scenarios/start', {
      method: 'POST',
      body: JSON.stringify({ sessionId, faultType }),
    });
  }

  function confirmPrScenario(sessionId: string) {
    return apiFetch<DemoScenarioResult>(
      `/api/demo/pr-scenarios/${encodeURIComponent(sessionId)}/confirm-target-restarted`,
      { method: 'POST' },
    );
  }

  function restartTargetService(sessionId: string) {
    return apiFetch<DemoTargetRestartResult>(
      `/api/demo/pr-scenarios/${encodeURIComponent(sessionId)}/restart-target-service`,
      { method: 'POST' },
    );
  }

  function getScenario(sessionId: string) {
    return apiFetch<DemoScenarioResult>(
      `/api/demo/pr-scenarios/${encodeURIComponent(sessionId)}`,
    );
  }

  function listRecords() {
    return apiFetch<RepairRecordIndex>('/api/repair/records');
  }

  function getRecord(sessionId: string) {
    return apiFetch<RepairRecord>(`/api/repair/records/${encodeURIComponent(sessionId)}`);
  }

  return {
    listFaults,
    readiness,
    startPrScenario,
    confirmPrScenario,
    restartTargetService,
    getScenario,
    listRecords,
    getRecord,
  };
}
