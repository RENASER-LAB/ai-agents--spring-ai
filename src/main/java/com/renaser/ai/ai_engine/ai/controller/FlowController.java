package com.renaser.ai.ai_engine.ai.controller;

import com.renaser.ai.ai_engine.ai.dto.AgentRunRequest;
import com.renaser.ai.ai_engine.ai.flow.FlowService;
import com.renaser.ai.ai_engine.ai.flow.FlowTraceResponse;
import com.renaser.ai.ai_engine.ai.service.AgentExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/flows")
@RequiredArgsConstructor
public class FlowController {

    private final AgentExecutionService agentExecutionService;
    private final FlowService flowService;

    // Arranca el flujo y devuelve el flowId de inmediato: los agentes se van encadenando
    // en segundo plano y la traza se consulta con GET mientras avanza.
    @PostMapping("/execute")
    public ResponseEntity<Map<String, UUID>> execute(@RequestBody AgentRunRequest request) {
        UUID flowId = agentExecutionService.startFlow(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("flowId", flowId));
    }

    @GetMapping("/{flowId}")
    public ResponseEntity<FlowTraceResponse> getTrace(@PathVariable UUID flowId) {
        return flowService.getTrace(flowId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
