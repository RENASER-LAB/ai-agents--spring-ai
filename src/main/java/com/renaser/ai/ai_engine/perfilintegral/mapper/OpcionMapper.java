package com.renaser.ai.ai_engine.perfilintegral.mapper;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.OpcionResponse;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OpcionMapper {
    OpcionResponse toResponse(Opcion entity);
}
