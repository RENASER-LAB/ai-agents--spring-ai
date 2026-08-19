package com.renaser.ai.ai_engine.perfilintegral.mapper;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPlantillaEvaluacion.CuotaResponse;
import com.renaser.ai.ai_engine.perfilintegral.entity.CuotaPlantillaEvaluacion;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CuotaPlantillaEvaluacionMapper {
    CuotaResponse toResponse(CuotaPlantillaEvaluacion entity);
}
