package com.renaser.ai.ai_engine.prueba.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * El contrato entre el motor de agentes y la prueba del puesto.
 *
 * <p>Es el mismo trato que ya tiene el Perfil Integral en {@code DtosCalificacionIa}: el
 * motor de agentes no conoce ni una tabla de aquí, así que alguien le pasa los datos
 * («insumo») y alguien guarda lo que el modelo devuelva («resultado»).
 *
 * <p><b>Un solo juego de records, no dos.</b> Los mismos que se le enseñan al modelo son los
 * que se leen de su respuesta, para que no haya dos definiciones que se puedan desincronizar.
 *
 * <p><b>La escala, que aquí no es 0 a 100.</b> Cada criterio de la rúbrica trae sus propios
 * puntos ({@code criterio.puntos}) y la rúbrica publicada suma 100 entre todos. Así que el
 * puntaje de un criterio va de 0 a <i>sus</i> puntos, no a cien: un criterio que vale 15 no
 * puede recibir un 80.
 */
public final class DtosPruebaIa {

    private DtosPruebaIa() {
    }

    /** Lo que se le enseña al agente que califica la prueba. */
    public record InsumoPrueba(
            String puesto,
            String nivelPuesto,
            String queBuscaLaVacante,
            String queSePidio,
            String materiales,
            String herramientasPermitidas,
            Integer duracionMinutos,
            /* El cambio que le apareció a mitad, si le apareció alguno */
            String cambioInesperado,
            /* Si se le acabó el tiempo y el sistema entregó por él: explica una entrega corta */
            boolean seLeAcaboElTiempo,
            /**
             * La guía que escribió quien preparó esta prueba, o nulo si no escribió ninguna.
             *
             * <p><b>Viaja aquí pero no se usa desde aquí.</b> Este record es el insumo, y el
             * insumo se serializa como JSON en el mensaje del usuario. Quien la coloca en el
             * mensaje {@code system} —antes del FORMATO, nunca después— es
             * {@code AgentePruebaPuesto}, y ahí está explicado por qué. Que además aparezca
             * en los datos no molesta: ahí es un campo con nombre, o sea contenido, que es
             * exactamente lo que es.
             */
            String guiaCalificacion,
            List<CriterioDeRubrica> criterios,
            List<RespuestaDePrueba> respuestas,
            List<EntregaDelCandidato> entregas) {
    }

    /**
     * Un criterio de la rúbrica, con lo que vale como máximo.
     *
     * <p>Solo llegan los que la rúbrica marca como verificables por agente
     * ({@code metodo_verificacion = AGENTE}). Los que dicen PERSONA o SISTEMA no se le
     * enseñan: no es que el modelo los haga mal, es que quien escribió la rúbrica ya decidió
     * que esos los mira alguien.
     */
    public record CriterioDeRubrica(String codigo, String nombre, String queMide,
                                    BigDecimal puntosMaximos) {
    }

    /** Lo que contestó a las preguntas previas y posteriores de la prueba. */
    public record RespuestaDePrueba(String tipoDePregunta, String pregunta, String queRevela,
                                    String respuesta) {
    }

    /**
     * Una cosa de las que tenía que entregar, y cómo llegó.
     *
     * <p><b>{@code contenido} vacío no significa que no entregó.</b> Significa que del
     * archivo no se pudo sacar texto —un video, unas diapositivas exportadas como imagen, un
     * enlace a un repositorio— y entonces {@code porQueNoSePuedeLeer} lo dice con palabras.
     * Es la diferencia entre «no hay nada que juzgar» y «no entregó nada», y confundirlas
     * produce un cero injusto.
     */
    public record EntregaDelCandidato(String nombre, String reglaQueDebiaCumplir, String formato,
                                      boolean loEntrego, String enlace, String archivo,
                                      String contenido, String porQueNoSePuedeLeer) {
    }

    /** Lo que devuelve. */
    public record ResultadoPrueba(List<NotaCriterioPruebaIa> criterios, BigDecimal confianza) {
    }

    /**
     * La nota de un criterio. {@code puntaje} entre 0 y los puntos de ese criterio.
     *
     * <p>La explicación es obligatoria: sin ella no se guarda (RF-150). Y la lista puede
     * traer menos criterios de los que se enviaron —eso es correcto y es lo que se le pide
     * al modelo cuando no tiene con qué juzgar—: los que falten se quedan para una persona.
     */
    public record NotaCriterioPruebaIa(String codigo, BigDecimal puntaje, String explicacion,
                                       String evidencia) {
    }
}
