package com.renaser.ai.ai_engine.perfilintegral.mapper;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.RangoResponse;
import com.renaser.ai.ai_engine.perfilintegral.entity.RangoPregunta;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RangoPreguntaMapper {
    RangoResponse toResponse(RangoPregunta entity);
}
