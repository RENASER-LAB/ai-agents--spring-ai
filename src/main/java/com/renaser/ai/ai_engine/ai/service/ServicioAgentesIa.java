package com.renaser.ai.ai_engine.ai.service;

import com.renaser.ai.ai_engine.ai.dto.DtosAgentesIa.*;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

// Los 9 agentes del hito 2 de selección son un catálogo cerrado (no se crean, ya están
// sembrados). Lo que sí administra Dirección son sus instrucciones: cada calificación
// guarda con qué versión se produjo (RF-56), y solo una instrucción está activa a la vez.
public interface ServicioAgentesIa {

    List<AgenteResponse> listarAgentes(ContextoUsuario quien);
    Long crearInstruccion(ContextoUsuario quien, CrearInstruccion datos);
    void publicarInstruccion(ContextoUsuario quien, Long id);
    List<InstruccionResponse> listarInstrucciones(ContextoUsuario quien, String agenteCodigo);
}
