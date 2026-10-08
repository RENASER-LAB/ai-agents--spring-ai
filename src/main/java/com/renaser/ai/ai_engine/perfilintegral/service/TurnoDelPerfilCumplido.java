package com.renaser.ai.ai_engine.perfilintegral.service;

/**
 * El candidato acaba de hacer todo lo que le tocaba en el Perfil Integral (V70).
 *
 * <p>El segundo evento de Spring del proyecto, hermano de {@link RetratoTerminado} y por la
 * misma razón: el pase a la prueba crea lo que el candidato va a rendir, eso pasa por
 * {@code ServicioEvaluacion}, y quien publica esto es justamente {@code ServicioEvaluacion}.
 * Llamar al pase directamente cerraría un círculo con el que Spring no arranca.
 *
 * <p><b>Quién lo publica:</b> la entrega del banco ({@code ServicioEvaluacionImpl.entregar}) y
 * la postulación a una vacante sin banco que califica sola
 * ({@code ServicioPostulacionPortalImpl.postular}).
 *
 * <p><b>Quién lo escucha:</b> {@code PaseAutomaticoAlInstante}, después del commit, y nadie
 * más. Corre en el mismo hilo antes de que la respuesta salga, así que cuando el portal vuelve a
 * pedir la postulación ya la ve en la prueba.
 *
 * <p>⚠️ <b>Al que escucha no lo inyecta nadie</b>, igual que al de {@link RetratoTerminado}.
 *
 * @param postulacionId quién lo hizo. Viaja el id: quien escucha relee la postulación ya
 *                      confirmada
 * @param momento       qué hizo, para el motivo que leerá el historial
 */
public record TurnoDelPerfilCumplido(Long postulacionId, Momento momento) {

    /** Qué acaba de hacer. */
    public enum Momento {
        /** Entregó el banco de preguntas. */
        AL_ENTREGAR("Pase automático al entregar: la nota se calcula después"),
        /** Postuló a una vacante sin banco, cumpliendo los requisitos. */
        AL_POSTULAR("Pase automático al postular: la nota se calcula después");

        private final String motivo;

        Momento(String motivo) {
            this.motivo = motivo;
        }

        /** Lo que dice el historial de la ficha junto al paso. */
        public String motivo() {
            return motivo;
        }
    }
}
