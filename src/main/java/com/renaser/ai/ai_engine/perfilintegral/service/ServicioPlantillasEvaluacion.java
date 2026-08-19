package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPlantillaEvaluacion.*;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

// La "receta" de una vacante: qué cuotas de preguntas de cada tipo/dimensión le tocan
// (RF-48). Publicada nunca se modifica, mismo criterio que version_pesos y version_banco.
public interface ServicioPlantillasEvaluacion {

    Long crear(ContextoUsuario quien, CrearPlantilla datos);
    List<PlantillaResponse> listar(ContextoUsuario quien);
    void publicar(ContextoUsuario quien, Long id);

    Long agregarCuota(ContextoUsuario quien, Long plantillaId, CrearCuota datos);
    List<CuotaResponse> listarCuotas(ContextoUsuario quien, Long plantillaId);
}
