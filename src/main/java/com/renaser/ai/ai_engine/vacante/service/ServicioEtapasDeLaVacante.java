package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

/**
 * Cambiar el recorrido de una vacante desde la tuerca (V95).
 *
 * <p>Solo mientras la vacante no tiene ninguna postulación: con la primera, las etapas quedan
 * fijas. Ver specs/Ready/etapas-por-vacante/spec.md, Flujos 1 y 2.
 */
public interface ServicioEtapasDeLaVacante {

    /**
     * Guarda el recorrido pedido, en orden.
     *
     * <p>400 si está vacío, no empieza por la etapa fija, repite alguna o trae un código que no
     * existe; 409 si la vacante tiene postulaciones o está cerrada, archivada o eliminada; 404 si
     * no es de la empresa de quien pregunta. Un recorrido igual al que ya tiene no escribe nada.
     */
    void cambiar(ContextoUsuario quien, Long vacanteId, List<String> etapas);
}
