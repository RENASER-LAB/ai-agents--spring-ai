package com.renaser.ai.ai_engine.perfilintegral.service;

/**
 * La parte de la calificación que no necesita inteligencia artificial.
 *
 * <p>Existe separada a propósito. Las preguntas cerradas se puntúan contra una clave
 * versionada y las contradicciones se detectan comparando dos números: eso es aritmética, y
 * un modelo generativo no debe tocarlo (RF-147). Así además esta parte se puede construir y
 * probar entera sin depender de que la IA responda.
 */
public interface ServicioCalificacion {

    /**
     * Puntúa lo cerrado de una evaluación terminada y levanta las alertas de consistencia.
     *
     * @return la nota de la evaluación sobre 100
     */
    java.math.BigDecimal calificarLoCerrado(Long postulacionId);

    /**
     * Lo mismo, pero sin guardar nada.
     *
     * <p>Lo usa el Perfil de Talento, que necesita volver a mirar la nota de lo cerrado para
     * combinarla con la de lo abierto. Devuelve también cuántas preguntas la produjeron,
     * porque esa cuenta es la que pondera las dos mitades: no pesa igual una nota sacada de
     * 20 preguntas que una de 3.
     */
    ResumenCerrado resumenDeLoCerrado(Long postulacionId);

    /** La nota de lo cerrado, sobre 100, y de cuántas preguntas salió. */
    record ResumenCerrado(java.math.BigDecimal nota, int preguntas) {
    }
}
