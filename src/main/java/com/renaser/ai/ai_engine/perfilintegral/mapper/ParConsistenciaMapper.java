package com.renaser.ai.ai_engine.perfilintegral.mapper;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.ParConsistenciaResponse;
import com.renaser.ai.ai_engine.perfilintegral.entity.ParConsistencia;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ParConsistenciaMapper {
    ParConsistenciaResponse toResponse(ParConsistencia entity);
}
