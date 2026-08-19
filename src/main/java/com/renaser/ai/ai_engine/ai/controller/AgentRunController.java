package com.renaser.ai.ai_engine.ai.controller;

import com.renaser.ai.ai_engine.ai.dto.AgentRunRequest;
import com.renaser.ai.ai_engine.ai.dto.AgentRunResponse;
import com.renaser.ai.ai_engine.ai.model.AgentType;
import com.renaser.ai.ai_engine.ai.service.AgentExecutionService;
import com.renaser.ai.ai_engine.ai.service.AgentRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agent-runs")
@RequiredArgsConstructor
public class AgentRunController {

    private final AgentRunService agentRunService;
    private final AgentExecutionService agentExecutionService;

    @PostMapping
    public ResponseEntity<AgentRunResponse> registerRun(@RequestBody AgentRunRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agentRunService.registerRun(request));
    }

    @PostMapping("/execute")
    public ResponseEntity<AgentRunResponse> execute(@RequestBody AgentRunRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agentExecutionService.execute(request));
    }

    @PostMapping("/execute-async")
    public ResponseEntity<UUID> executeAsync(@RequestBody AgentRunRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(agentExecutionService.enqueue(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgentRunResponse> getById(@PathVariable UUID id) {
        return agentRunService.getById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/history/{entityId}")
    public ResponseEntity<List<AgentRunResponse>> getHistory(@PathVariable String entityId) {
        return ResponseEntity.ok(agentRunService.getHistory(entityId));
    }

    @GetMapping("/by-agent/{agentType}")
    public ResponseEntity<List<AgentRunResponse>> getRunsByAgent(@PathVariable AgentType agentType) {
        return ResponseEntity.ok(agentRunService.getRunsByAgent(agentType));
    }

    @GetMapping("/pending-approvals")
    public ResponseEntity<List<AgentRunResponse>> getPendingApprovals() {
        return ResponseEntity.ok(agentRunService.getPendingApprovals());
    }

    @GetMapping("/last")
    public ResponseEntity<AgentRunResponse> getLastRun(
            @RequestParam String entityId,
            @RequestParam AgentType agentType) {
        return agentRunService.getLastRun(entityId, agentType)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<AgentRunResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(agentRunService.approve(id));
    }
}
