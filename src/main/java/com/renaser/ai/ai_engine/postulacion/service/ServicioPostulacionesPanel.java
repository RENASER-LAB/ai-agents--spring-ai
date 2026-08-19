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

    /**
     * Un enlace temporal para bajarse el archivo <b>del almacen directamente</b>.
     *
     * <p>Es lo que evita que un curriculum de diez megas entre y salga del backend solo para
     * llegar a un navegador: paga el doble de trafico, ocupa memoria mientras dura, y varios
     * a la vez se notan. Con el enlace, el navegador habla con el almacen y aqui no pasa
     * nada.
     *
     * <p>El permiso se comprueba <b>antes</b> de firmar. Despues ya no hay a quien
     * preguntarle: el enlace vale por si solo, y por eso dura minutos y no horas.
     *
     * @throws IllegalStateException si el almacen no sabe firmar enlaces —el de disco no—,
     *                               y entonces toca {@link #descargarArchivo}
     */
    EnlaceArchivo enlaceDeArchivo(ContextoUsuario quien, Long archivoId);
}
