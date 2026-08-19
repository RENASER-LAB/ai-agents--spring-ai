package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.*;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

// El banco es un repositorio, no un cuestionario que se aplica entero (RF-47): cada
// vacante elige de aquí. Una versión publicada no se modifica; para cambiar preguntas
// hace falta una versión nueva (mismo criterio que version_pesos).
public interface ServicioBancoPreguntas {

    Long crearVersion(ContextoUsuario quien, CrearVersionBanco datos);
    List<VersionBancoResponse> listarVersiones(ContextoUsuario quien);
    void publicarVersion(ContextoUsuario quien, Long id);

    Long crearPregunta(ContextoUsuario quien, Long versionBancoId, CrearPregunta datos);
    List<PreguntaResponse> listarPreguntas(ContextoUsuario quien, Long versionBancoId);

    Long agregarOpcion(ContextoUsuario quien, Long preguntaId, CrearOpcion datos);
    List<OpcionResponse> listarOpciones(ContextoUsuario quien, Long preguntaId);
}
