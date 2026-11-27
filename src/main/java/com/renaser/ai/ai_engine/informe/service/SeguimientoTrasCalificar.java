package com.renaser.ai.ai_engine.informe.service;

/**
 * Lo que la cola de la IA le pregunta al informe cuando termina una etapa (V94, spec
 * seguimiento-de-pendientes-entre-etapas). Lo implementa {@link ArmadoDelInforme}, que es quien
 * sabe qué dudas siguen abiertas.
 *
 * <p><b>El orden es seguimiento → redactor.</b> Si hay dudas que seguir con datos nuevos, se pide
 * el seguimiento y la cola no pide al redactor: lo pide el seguimiento al terminar, bien o mal, y
 * así el redactor escribe una sola vez y ya con los estados.
 *
 * <p>Nunca califica ni mueve a nadie, y nada de esto frena la etapa: quien lo llama atrapa lo que
 * falle y sigue como siempre.
 */
public interface SeguimientoTrasCalificar {

    /**
     * Terminó la calificación de la etapa ({@code EVALUACION} o {@code PRUEBA}): pide el
     * seguimiento si hay dudas abiertas y algo nuevo que leer.
     *
     * @return true si el seguimiento viene en camino (él pedirá al redactor); false si no hacía
     *         falta —sin dudas, o ya está al día— y la cola pide al redactor como siempre
     */
    boolean pedirElSeguimiento(Long postulacionId, String etapa);

    /**
     * Terminó el seguimiento de esa etapa, bien o agotando sus reintentos: pide al redactor con lo
     * que haya. Si era el de la evaluación y la prueba ya estaba calificada, sigue con la prueba,
     * porque lo que quedó abierto ha cambiado.
     */
    void trasElSeguimiento(Long postulacionId, String etapa);
}
