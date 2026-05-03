import { ref } from 'vue';

import type { RepairEvent, RepairStage } from '@/types';

export const repairStages: RepairStage[] = [
  'detecting',
  'planning',
  'executing',
  'patching',
  'testing',
  'reviewing',
  'committing',
  'pr_created',
  'notified',
  'reflecting',
  'completed',
  'error',
];

export function useRepairStream() {
  const events = ref<RepairEvent[]>([]);
  const connected = ref(false);
  const streamError = ref('');
  let source: EventSource | null = null;

  function reset() {
    events.value = [];
    streamError.value = '';
  }

  function setEvents(restoredEvents: RepairEvent[]) {
    events.value = Array.isArray(restoredEvents) ? restoredEvents : [];
  }

  function close() {
    if (source) {
      source.close();
      source = null;
    }
    connected.value = false;
  }

  function connect(streamUrl: string, onTerminalEvent?: (event: RepairEvent) => void) {
    close();
    streamError.value = '';
    source = new EventSource(streamUrl);
    connected.value = true;

    for (const stage of repairStages) {
      source.addEventListener(stage, (message) => {
        const event = JSON.parse((message as MessageEvent).data) as RepairEvent;
        if (!events.value.some((existing) => eventKey(existing) === eventKey(event))) {
          events.value = [...events.value, event];
        }
        if (event.stage === 'completed' || event.stage === 'error') {
          onTerminalEvent?.(event);
          close();
        }
      });
    }

    source.onerror = () => {
      streamError.value = 'SSE 连接暂时中断，浏览器会自动尝试重连。';
      connected.value = false;
    };
  }

  return {
    events,
    connected,
    streamError,
    reset,
    setEvents,
    connect,
    close,
  };
}

function eventKey(event: RepairEvent) {
  return `${event.timestamp}|${event.stage}|${event.message}`;
}
