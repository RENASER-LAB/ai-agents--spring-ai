package com.renaser.ai.ai_engine.perfilintegral.mapper;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPlantillaEvaluacion.PlantillaResponse;
import com.renaser.ai.ai_engine.perfilintegral.entity.PlantillaEvaluacion;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PlantillaEvaluacionMapper {
    PlantillaResponse toResponse(PlantillaEvaluacion entity);
}
