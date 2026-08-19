package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.NotaCriterioResponse;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.PonerNotaCriterio;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.math.BigDecimal;
import java.util.List;

/**
 * La calificación de la prueba del puesto, criterio a criterio.
 *
 * <p>A diferencia del hito 2 —donde las preguntas cerradas se puntúan por completo contra
 * una clave, sin que nadie tenga que mirarlas—, la rúbrica de la prueba (RF-85) es
 * mayoritariamente cualitativa: comprensión, calidad, criterio, capacidad de explicar. No hay
 * una fórmula genérica que la calcule sola.
 *
 * <p>Lo que sí es determinístico es <b>ponderar</b> lo que ya se calificó: cada criterio
 * declara sus puntos ({@code criterio.puntos}) y cómo se verifica (RF-87). Este servicio no
 * inventa notas — las suma. Ponerlas es trabajo de una persona hoy, y del agente
 * {@code PRUEBA_PUESTO} cuando exista.
 */
public interface ServicioCalificacionPrueba {

    List<NotaCriterioResponse> verNotas(ContextoUsuario quien, Long postulacionId);

    void ponerNota(ContextoUsuario quien, Long postulacionId, Long criterioId, PonerNotaCriterio datos);

    /**
     * Pondera las notas ya puestas y guarda la nota de la etapa PRUEBA_PUESTO.
     *
     * @throws IllegalStateException si falta la nota de algún criterio de la rúbrica
     */
    BigDecimal calcularNotaEtapa(ContextoUsuario quien, Long postulacionId);
}
