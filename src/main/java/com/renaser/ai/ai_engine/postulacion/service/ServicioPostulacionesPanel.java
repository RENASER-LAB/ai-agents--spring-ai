package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.postulacion.dto.DtosPostulacion.*;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

public interface ServicioPostulacionesPanel {

    // La bandeja: todo lo que espera a alguien, filtrado por el alcance de quien mira
    List<FilaBandeja> bandeja(ContextoUsuario quien, String esperaA);

    ConteoEmbudo embudo(ContextoUsuario quien, Long vacanteId);

    FichaPostulacion ficha(ContextoUsuario quien, Long postulacionId);

    List<PasoHistorial> historial(ContextoUsuario quien, Long postulacionId);

    // Una persona puede mover una postulación a donde quiera, siempre con motivo
    void transicionar(ContextoUsuario quien, Long postulacionId, Transicionar datos);

    // Aplica el estado siguiente calculado por la máquina
    void confirmarAvance(ContextoUsuario quien, Long postulacionId, String motivo);

    byte[] descargarArchivo(ContextoUsuario quien, Long archivoId, StringBuilder nombreSalida);
}
