package com.renaser.ai.ai_engine.ai.mapper;

import com.renaser.ai.ai_engine.ai.dto.AgentRunRequest;
import com.renaser.ai.ai_engine.ai.dto.AgentRunResponse;
import com.renaser.ai.ai_engine.ai.model.AgentRun;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AgentRunMapper {

    AgentRun toAgenteRun(AgentRunRequest request);
    AgentRunResponse toAgenteRunResponse(AgentRun entity);
}
