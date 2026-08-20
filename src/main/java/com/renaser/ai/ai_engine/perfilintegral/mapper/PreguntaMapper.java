package com.renaser.ai.ai_engine.perfilintegral.mapper;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.PreguntaResponse;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;

import org.mapstruct.Mapper;

// Plantilla de referencia para el hito 2: MapStruct + sufijo Response. logicaInterna
// queda fuera a propósito: nunca sale de la base (RF-53). El resto —incluidos los campos
// de puntuación del v3— sí sale: estos DTOs son del panel, y quien edita el banco
// necesita ver la clave que escribió.
@Mapper(componentModel = "spring")
public interface PreguntaMapper {
    PreguntaResponse toResponse(Pregunta entity);
}
