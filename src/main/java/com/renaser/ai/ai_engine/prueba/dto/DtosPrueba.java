package com.renaser.ai.ai_engine.prueba.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Los contratos de la prueba tal como la ve el candidato.
 *
 * <p>Igual que con la evaluación del hito 2, aquí manda una regla: <b>lo que el candidato no
 * debe saber no tiene campo en el contrato.</b> No viaja la rúbrica ({@code criterio.puntos},
 * {@code metodoVerificacion}), no viaja {@code pregunta_prueba.revela}, y no viajan las
 * variantes del cambio inesperado que no le tocaron a él.
 *
 * <p>El minuto exacto del cambio tampoco viaja de antemano: {@code cambioTexto} solo aparece
 * en la respuesta una vez que el servidor decide que ya toca mostrarlo (RF-77 — si se supiera
 * de antemano, se aprendería el patrón).
 *
 * <p><b>Y antes de empezar no viaja nada que dé ventaja</b> (decisión del usuario del
 * 10/10/2026). El caso se lee dentro del reloj: mientras el intento no tenga
 * {@code iniciadoEn}, la prueba llega sin preguntas, sin enunciado, sin materiales, sin
 * consigna y sin el detalle de los entregables ({@link MiPrueba#antesDeEmpezar()}). Solo
 * viaja lo que se necesita para decidir cuándo empezar: el estado, el tiempo y la modalidad,
 * la fecha límite o los días, cuántas preguntas son ({@code totalPreguntas}), las herramientas
 * permitidas y qué archivos habrá que entregar —nombre, si es obligatorio, su formato y en qué
 * pregunta va—. Todo lo demás llega en la respuesta de {@code POST /inicio} y en las lecturas
 * posteriores.
 */
public final class DtosPrueba {

    private DtosPrueba() {}

    /**
     * Una pregunta tal como la ve el candidato. En la prueba escrita en el editor (V67) trae
     * además sus opciones —id, texto y orden, <b>nunca sus puntos</b> (RF-53)— y lo que ya
     * marcó, para poder retomarla; y su posición, que es como se nombra al decir qué falta.
     */
    public record PreguntaCandidato(Long id, String tipo, String enunciado, String respuestaTexto,
                                    List<OpcionCandidato> opciones, Long respuestaOpcionId,
                                    List<Long> respuestaMarcadas, Integer posicion) {

        public PreguntaCandidato(Long id, String tipo, String enunciado, String respuestaTexto) {
            this(id, tipo, enunciado, respuestaTexto, List.of(), null, List.of(), null);
        }
    }

    /** Una opción o un nivel de la escala: sin puntos ni clave (RF-53). */
    public record OpcionCandidato(Long id, String texto, int orden) {}

    /** El enunciado adjunto en PDF o Word, con un enlace para descargarlo (o nulo si no hay). */
    public record ConsignaCandidato(String nombre, String url) {}

    /**
     * Un entregable que tiene que subir. {@code preguntaId} (V68, solo la prueba del editor): la
     * pregunta en la que se sube su archivo; nulo en los generales, que van al final.
     * {@code posicionDeLaPregunta} (10/10/2026): el número de esa pregunta en la prueba, el de
     * «en la pregunta 4». Hace falta aparte porque antes de empezar las preguntas no viajan, y el
     * número no da contenido.
     */
    public record EntregableRequeridoCandidato(
            Long id, String nombre, String detalle, String formato,
            boolean esObligatorio, boolean entregado, Long preguntaId, Integer posicionDeLaPregunta) {

        public EntregableRequeridoCandidato(Long id, String nombre, String detalle, String formato,
                                            boolean esObligatorio, boolean entregado) {
            this(id, nombre, detalle, formato, esObligatorio, entregado, null, null);
        }

        /**
         * El mismo entregable sin su {@code detalle}: lo que se enseña antes de empezar. El nombre,
         * si es obligatorio, el formato y la pregunta no dan contenido; el detalle a veces sí («con
         * las partidas en tránsito separadas de los errores»), y se lee dentro del reloj.
         */
        public EntregableRequeridoCandidato sinDetalle() {
            return new EntregableRequeridoCandidato(id, nombre, null, formato, esObligatorio, entregado,
                    preguntaId, posicionDeLaPregunta);
        }
    }

    /**
     * La prueba del candidato. {@code totalPreguntas} (10/10/2026) dice cuántas son aunque
     * {@code preguntas} llegue vacía, que es lo que pasa antes de empezar.
     */
    public record MiPrueba(
            Long id,
            String estadoIntento,          // PENDIENTE | EN_CURSO | ENTREGADA
            String modalidad,
            Instant iniciadoEn,
            Instant venceEn,
            Integer duracionMinutos,
            String enunciado,
            String materiales,
            String herramientasPermitidas,
            String cambioTexto,            // null hasta que toque mostrarlo
            List<PreguntaCandidato> preguntas,
            List<EntregableRequeridoCandidato> entregables,
            /* Solo en la prueba escrita en el editor (V67). `estadoIntento` puede ser además
               NO_COMPLETADA: venció con algo sin responder y ya no se entrega. */
            Integer plazoDias,
            boolean cuestionario,
            ConsignaCandidato consigna,
            boolean delEditor,
            /* La fecha límite para dar la prueba (V68, solo la prueba del editor): la de la
               vacante, o la suya si se la dieron a mano. Nula si no hay. */
            Instant fechaLimite,
            /* Cuántas preguntas tiene su prueba (10/10/2026). Viaja siempre, también antes de
               empezar, cuando `preguntas` va vacía. */
            int totalPreguntas) {

        /**
         * Lo que viaja antes de empezar: lo mismo sin nada que dé ventaja. Sin preguntas (solo
         * cuántas son), sin enunciado, sin materiales, sin consigna, sin cambio inesperado y sin
         * el detalle de cada entregable. Se quedan el estado, el tiempo y la modalidad, las
         * fechas y los días, las herramientas permitidas y la lista de lo que habrá que
         * entregar.
         */
        public MiPrueba antesDeEmpezar() {
            return new MiPrueba(id, estadoIntento, modalidad, iniciadoEn, venceEn, duracionMinutos,
                    null, null, herramientasPermitidas, null, List.of(),
                    entregables.stream().map(EntregableRequeridoCandidato::sinDetalle).toList(),
                    plazoDias, cuestionario, null, delEditor, fechaLimite, totalPreguntas);
        }
    }

    /**
     * Lo que el candidato escribe en una pregunta de la prueba.
     *
     * <p>⚠️ <b>Sin {@code @NotBlank}, y a propósito.</b> Lo tuvo, y eso hacía que vaciar el
     * recuadro rebotara con un 400 antes siquiera de entrar al servicio. Para el candidato
     * eso era un error en mitad de una prueba cronometrada por haber borrado lo que él mismo
     * había escrito, y encima el rechazo dejaba el texto viejo guardado: recuadro vacío en la
     * pantalla y respuesta entera en el servidor.
     *
     * <p>Ahora el vacío lo entiende el servicio y significa lo que parece —esta pregunta se
     * queda sin responder—, igual que en la evaluación del Perfil Integral y en el
     * cuestionario técnico. El tope de tamaño sí se queda: eso sí es un error.
     */
    public record Responder(
            @Size(max = 20_000, message = "La respuesta es demasiado larga")
            String texto,
            /* La opción elegida en una de opción única o en una escala (V67). Nula = sin
               responder. */
            Long opcionId,
            /* Las marcadas en una de opción múltiple (V67). Vacía = sin responder. */
            List<Long> marcadas) {

        public Responder(String texto) {
            this(texto, null, null);
        }
    }

    /** Uno de los dos: {@code enlace}, o nada si se sube archivo por multipart. */
    public record SubirEntregableEnlace(@NotBlank String enlace) {}

    public record EntregaResponse(String estado, boolean completa, int faltantes) {}
}
