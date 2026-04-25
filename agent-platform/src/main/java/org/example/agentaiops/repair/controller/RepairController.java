package org.example.agentaiops.repair.controller;

import org.example.agentaiops.repair.model.RepairRunRequest;
import org.example.agentaiops.repair.model.RepairRunResponse;
import org.example.agentaiops.repair.service.RepairEventHub;
import org.example.agentaiops.repair.service.RepairWorkflowService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/repair")
public class RepairController {

    private final RepairWorkflowService repairWorkflowService;
    private final RepairEventHub repairEventHub;

    public RepairController(RepairWorkflowService repairWorkflowService, RepairEventHub repairEventHub) {
        this.repairWorkflowService = repairWorkflowService;
        this.repairEventHub = repairEventHub;
    }

    @PostMapping("/run")
    public RepairRunResponse run(@RequestBody(required = false) RepairRunRequest request) {
        String sessionId = request == null ? null : request.getSessionId();
        return repairWorkflowService.startAsync(sessionId);
    }

    @GetMapping(value = "/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String sessionId) {
        return repairEventHub.subscribe(sessionId);
    }
}
