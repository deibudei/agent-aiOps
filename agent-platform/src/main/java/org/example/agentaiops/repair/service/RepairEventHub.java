package org.example.agentaiops.repair.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.example.agentaiops.repair.model.RepairEvent;
import org.example.agentaiops.repair.model.RepairStage;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class RepairEventHub {

    private final Map<String, List<RepairEvent>> history = new ConcurrentHashMap<>();
    private final Map<String, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    /** Publishes a simple event without extra details. */
    public void publish(String sessionId, RepairStage stage, String message) {
        publish(RepairEvent.of(sessionId, stage, message));
    }

    /** Publishes an event with structured details for the frontend or logs. */
    public void publish(String sessionId, RepairStage stage, String message, Map<String, Object> details) {
        publish(RepairEvent.of(sessionId, stage, message, details));
    }

    /** Stores the event and pushes it to current SSE subscribers. */
    public void publish(RepairEvent event) {
        history.computeIfAbsent(event.sessionId(), ignored -> new CopyOnWriteArrayList<>()).add(event);
        List<SseEmitter> emitters = subscribers.getOrDefault(event.sessionId(), List.of());
        List<SseEmitter> failed = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                send(emitter, event);
            } catch (IOException | IllegalStateException e) {
                failed.add(emitter);
            }
        }
        if (!failed.isEmpty()) {
            emitters.removeAll(failed);
        }
    }

    /** Opens an SSE stream and replays existing session history first. */
    public SseEmitter subscribe(String sessionId) {
        SseEmitter emitter = new SseEmitter(600_000L);
        subscribers.computeIfAbsent(sessionId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> remove(sessionId, emitter));
        emitter.onError(error -> remove(sessionId, emitter));

        for (RepairEvent event : history.getOrDefault(sessionId, List.of())) {
            try {
                send(emitter, event);
            } catch (IOException e) {
                emitter.completeWithError(e);
                break;
            }
        }
        return emitter;
    }

    /** Sends one event over an SSE connection. */
    private void send(SseEmitter emitter, RepairEvent event) throws IOException {
        emitter.send(SseEmitter.event()
                .name(event.stage())
                .data(event, MediaType.APPLICATION_JSON));
    }

    /** Removes a dead subscriber from the session list. */
    private void remove(String sessionId, SseEmitter emitter) {
        List<SseEmitter> emitters = subscribers.get(sessionId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }
}
