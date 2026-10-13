package com.renaser.ai.ai_engine.perfilintegral.service;

/**
 * La nota del Perfil Integral de esta postulación se acaba de recalcular sin pasar por la IA
 * (V72): un ajuste a mano de una abierta, una recalificación con la guía nueva o un cambio de
 * puntos.
 *
 * <p>El segundo evento de Spring del proyecto, hermano de {@link RetratoTerminado} y por la
 * misma razón: el pase a la prueba crea lo que el candidato va a rendir, eso pasa por
 * {@code ServicioEvaluacion}, que usa la cola de la IA, que usa el puente que publica esto.
 * Llamar al pase directamente cerraría un círculo con el que Spring no arranca.
 *
 * <p><b>Quién lo publica:</b> {@code PuenteCalificacionIaImpl.recalcularSinMover}, después de
 * guardar la nota nueva y el grupo.
 *
 * <p><b>Quién lo escucha:</b> {@code PaseAutomaticoTrasCalificar}, después del commit, y nadie
 * más. Si la postulación sigue en «por confirmar» en una vacante que avanza sola y su nota
 * ahora llega a la mínima, pasa a la prueba. A quien ya está en la prueba no se le toca.
 *
 * <p>⚠️ <b>Al que escucha no lo inyecta nadie</b>, igual que al de {@link RetratoTerminado}.
 *
 * @param postulacionId a quién se le recalculó. Viaja el id: quien escucha relee la
 *                      postulación y su nota ya confirmadas
 */
public record NotaDelPerfilRecalculada(Long postulacionId) {
}
