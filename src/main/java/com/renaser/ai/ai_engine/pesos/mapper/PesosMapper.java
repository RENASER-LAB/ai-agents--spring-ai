package com.renaser.ai.ai_engine.pesos.mapper;

import com.renaser.ai.ai_engine.perfilintegral.entity.PesoCriterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.PesoDimension;
import com.renaser.ai.ai_engine.pesos.dto.DtosPesos.*;
import com.renaser.ai.ai_engine.pesos.entity.PesoComponentePerfil;
import com.renaser.ai.ai_engine.pesos.entity.PesoEtapa;
import com.renaser.ai.ai_engine.pesos.entity.VersionPesos;

import org.mapstruct.Mapper;

// Un solo mapper para el agregado "versión de pesos": los cuatro tipos de fila que
// contiene, igual que AgentRunMapper cubre AgentRun entero en el módulo de agentes.
@Mapper(componentModel = "spring")
public interface PesosMapper {
    VersionPesosResponse toResponse(VersionPesos entity);
    PesoEtapaResponse toResponse(PesoEtapa entity);
    PesoComponenteResponse toResponse(PesoComponentePerfil entity);
    PesoDimensionResponse toResponse(PesoDimension entity);
    PesoCriterioResponse toResponse(PesoCriterio entity);
}
