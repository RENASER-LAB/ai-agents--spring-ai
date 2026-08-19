package com.renaser.ai.ai_engine.perfilintegral.mapper;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.VersionBancoResponse;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface VersionBancoMapper {
    VersionBancoResponse toResponse(VersionBanco entity);
}
