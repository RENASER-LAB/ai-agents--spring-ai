package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.dto.AgentRunRequest;
import com.renaser.ai.ai_engine.ai.dto.AgentRunResponse;
import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.mapper.AgentRunMapper;
import com.renaser.ai.ai_engine.ai.model.AgentRun;
import com.renaser.ai.ai_engine.ai.model.AgentType;
import com.renaser.ai.ai_engine.ai.repository.AgentRunRepository;
import com.renaser.ai.ai_engine.ai.service.AgentRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentRunServiceImpl implements AgentRunService {

    private final AgentRunRepository agentRunRepository;
    private final AgentRunMapper agentRunMapper;

    @Override
    public AgentRunResponse registerRun(AgentRunRequest request) {
        AgentRun run = agentRunMapper.toAgenteRun(request);
        run.setCreatedAt(Instant.now());
        AgentRun saved = agentRunRepository.save(run);
        return agentRunMapper.toAgenteRunResponse(saved);
    }

    @Override
    public Optional<AgentRunResponse> getById(UUID id) {
        return agentRunRepository.findById(id).map(agentRunMapper::toAgenteRunResponse);
    }

    @Override
    public List<AgentRunResponse> getHistory(String entityId) {
        return agentRunRepository.findByEntityIdOrderByCreatedAtDesc(entityId).stream()
                .map(agentRunMapper::toAgenteRunResponse)
                .toList();
    }

    @Override
    public List<AgentRunResponse> getRunsByAgent(AgentType agentType) {
        return agentRunRepository.findByAgentType(agentType).stream()
                .map(agentRunMapper::toAgenteRunResponse)
                .toList();
    }

    @Override
    public List<AgentRunResponse> getPendingApprovals() {
        return agentRunRepository.findByRequiresHumanApprovalTrueAndApprovedFalse().stream()
                .map(agentRunMapper::toAgenteRunResponse)
                .toList();
    }

    @Override
    public Optional<AgentRunResponse> getLastRun(String entityId, AgentType agentType) {
        return agentRunRepository.findTopByEntityIdAndAgentTypeOrderByCreatedAtDesc(entityId, agentType)
                .map(agentRunMapper::toAgenteRunResponse);
    }

    @Override
    public AgentRunResponse approve(UUID runId) {
        return agentRunRepository.findById(runId)
                .map(run -> {
                    run.setApproved(true);
                    return run;
                })
                .map(agentRunRepository::save)
                .map(agentRunMapper::toAgenteRunResponse)
                .orElseThrow(() -> new ResourceNotFoundException("AgentRun", "id", runId));
    }
}
