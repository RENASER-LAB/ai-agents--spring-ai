package com.renaser.ai.ai_engine.ai.mapper;

import com.renaser.ai.ai_engine.ai.dto.DtosAgentesIa.AgenteResponse;
import com.renaser.ai.ai_engine.ai.dto.DtosAgentesIa.InstruccionResponse;
import com.renaser.ai.ai_engine.ai.model.Agente;
import com.renaser.ai.ai_engine.ai.model.InstruccionIa;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AgentesIaMapper {
    AgenteResponse toResponse(Agente entity);
    InstruccionResponse toResponse(InstruccionIa entity);
}
