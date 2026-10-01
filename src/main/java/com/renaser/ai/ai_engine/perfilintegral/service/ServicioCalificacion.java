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

    /**
     * La nota de las preguntas propias de la vacante (método PUNTOS, V66).
     *
     * <p>Es la suma de sus criterios, de 0 a 100, sin ponderar cerradas y abiertas por cuántas
     * hay (eso es {@link #notaCombinada}, que sigue siendo de los otros métodos).
     *
     * @return nulo si la evaluación de esa postulación no es de este método
     */
    NotaDelBanco notaDelBancoPorPuntos(Long postulacionId);

    /**
     * @param nota               la del banco, sobre 100; nula mientras falte alguna abierta
     * @param completa           si todas sus preguntas con puntos tienen nota
     * @param cerradasSobreCien  lo cerrado en porcentaje de su máximo, o nulo si no hay
     * @param cerradas           cuántas cerradas con puntos tiene
     * @param abiertasSobreCien  lo abierto ya calificado en porcentaje de su máximo, o nulo
     * @param abiertasSobreCuatro la nota de cada abierta calificada llevada a 0–4, por el id
     *                           de su respuesta: la escala que el retrato conoce
     */
    record NotaDelBanco(java.math.BigDecimal nota, boolean completa,
                        java.math.BigDecimal cerradasSobreCien, int cerradas,
                        java.math.BigDecimal abiertasSobreCien,
                        java.util.Map<Long, java.math.BigDecimal> abiertasSobreCuatro) {
    }

    /**
     * La nota de la evaluación entera, sobre 100: lo cerrado y lo abierto ponderados por
     * cuántas preguntas produjo cada mitad. Sin nada calificado no se inventa un cero: nulo.
     *
     * <p>Vive aquí, y no copiada en cada sitio, porque cómo combinar las dos mitades es una
     * interpretación todavía pendiente de confirmar con el cliente: el día que cambie, tiene
     * que cambiar a la vez para la nota de la etapa y para el desglose que enseña el panel.
     * Los puntajes de lo abierto llegan de 0 a 4, como los guarda {@code nota_respuesta}.
     */
    static java.math.BigDecimal notaCombinada(ResumenCerrado cerrado,
                                              java.util.List<java.math.BigDecimal> puntajesAbiertas) {
        java.util.List<java.math.BigDecimal> conNota = puntajesAbiertas.stream()
                .filter(java.util.Objects::nonNull).toList();

        java.math.BigDecimal suma = java.math.BigDecimal.ZERO;
        int total = 0;
        if (cerrado.preguntas() > 0) {
            suma = suma.add(cerrado.nota()
                    .multiply(java.math.BigDecimal.valueOf(cerrado.preguntas())));
            total += cerrado.preguntas();
        }
        if (!conNota.isEmpty()) {
            java.math.BigDecimal sobreCien = conNota.stream()
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                    .multiply(new java.math.BigDecimal("100"))
                    .divide(new java.math.BigDecimal("4")
                                    .multiply(java.math.BigDecimal.valueOf(conNota.size())),
                            2, java.math.RoundingMode.HALF_UP);
            suma = suma.add(sobreCien.multiply(java.math.BigDecimal.valueOf(conNota.size())));
            total += conNota.size();
        }
        return total == 0 ? null
                : suma.divide(java.math.BigDecimal.valueOf(total), 2,
                        java.math.RoundingMode.HALF_UP);
    }
}
