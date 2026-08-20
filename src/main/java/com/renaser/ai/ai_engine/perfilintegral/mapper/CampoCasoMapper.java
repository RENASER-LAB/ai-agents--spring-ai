package com.renaser.ai.ai_engine.perfilintegral.mapper;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.CampoCasoResponse;
import com.renaser.ai.ai_engine.perfilintegral.entity.CampoCaso;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CampoCasoMapper {
    CampoCasoResponse toResponse(CampoCaso entity);
}
