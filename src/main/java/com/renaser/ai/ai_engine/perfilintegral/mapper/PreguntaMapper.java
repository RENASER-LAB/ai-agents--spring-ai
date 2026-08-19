package com.renaser.ai.ai_engine.perfilintegral.mapper;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.PreguntaResponse;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;

import org.mapstruct.Mapper;

// Plantilla de referencia para el hito 2: MapStruct + sufijo Response. situacion y
// logicaInterna quedan fuera a propósito (logicaInterna nunca sale de la base, RF-53).
@Mapper(componentModel = "spring")
public interface PreguntaMapper {
    PreguntaResponse toResponse(Pregunta entity);
}
