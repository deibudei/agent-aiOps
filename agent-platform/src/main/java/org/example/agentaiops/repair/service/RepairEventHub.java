package org.example.agentaiops.repair.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.example.agentaiops.repair.model.RepairEvent;
import org.example.agentaiops.repair.model.RepairStage;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class RepairEventHub {

    private final Map<String, List<RepairEvent>> history = new ConcurrentHashMap<>();
    private final Map<String, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public void publish(String sessionId, RepairStage stage, String message) {
        publish(RepairEvent.of(sessionId, stage, message));
    }

    public void publish(String sessionId, RepairStage stage, String message, Map<String, Object> details) {
        publish(RepairEvent.of(sessionId, stage, message, details));
    }

    public void publish(RepairEvent event) {
        history.computeIfAbsent(event.sessionId(), ignored -> new ArrayList<>()).add(event);
        List<SseEmitter> emitters = subscribers.getOrDefault(event.sessionId(), List.of());
        List<SseEmitter> failed = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                send(emitter, event);
            } catch (IOException e) {
                failed.add(emitter);
            }
        }
        if (!failed.isEmpty()) {
            emitters.removeAll(failed);
        }
    }

    public SseEmitter subscribe(String sessionId) {
        SseEmitter emitter = new SseEmitter(600_000L);
        subscribers.computeIfAbsent(sessionId, ignored -> new ArrayList<>()).add(emitter);
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

    private void send(SseEmitter emitter, RepairEvent event) throws IOException {
        emitter.send(SseEmitter.event()
                .name(event.stage())
                .data(event, MediaType.APPLICATION_JSON));
    }

    private void remove(String sessionId, SseEmitter emitter) {
        List<SseEmitter> emitters = subscribers.get(sessionId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }
}
