package com.renaser.ai.ai_engine.prueba.dto;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarOpcion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PreguntaElegida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PuntosDePregunta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.TextoDe;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Los contratos de la prueba técnica escrita en el editor (V67): lo que entra al editor, la
 * ficha de la prueba de un candidato y la lista de quienes no la completaron.
 *
 * <p>Lo que sale del editor es el mismo {@code EditorDePreguntas} de la fase 1, con el caso,
 * el tiempo, los entregables y la parte calificada de cada criterio: así el panel reutiliza
 * sus bloques en vez de tener dos editores.
 *
 * <p><b>Los puntos entran como número decimal y se exigen enteros al validar</b>, como en la
 * fase 1: Jackson convierte en silencio un 2,5 en 2, y la regla es rechazarlo con un 400.
 *
 * <p>Nada de esto llega al portal: el candidato tiene sus propios contratos
 * ({@code DtosPrueba}), sin campo para puntos, claves, criterios ni calificadores (RF-53).
 */
public final class DtosPruebaPropia {

    private DtosPruebaPropia() {
    }

    // ============================== El editor: lo que entra ==============================

    /**
     * Un criterio de la prueba: su nombre, qué evalúa, <b>lo que vale entero</b> ({@code puntos},
     * V69) y quién califica lo que no suman sus cerradas (IA o PERSONA). Esa diferencia es su
     * parte calificada, que se deduce: si luego cambian sus cerradas, el criterio sigue
     * valiendo lo mismo. <b>Lo que mira no se escribe</b> (V68): lo deduce el sistema del
     * alcance de los entregables.
     */
    public record GuardarCriterioDePrueba(
            @NotBlank(message = "El criterio necesita un nombre")
            @Size(max = 120, message = "El nombre del criterio admite hasta 120 caracteres")
            String nombre,
            @Size(max = 1000, message = "«Qué evalúa» admite hasta 1000 caracteres")
            String queEvalua,
            BigDecimal puntos,
            String calificador) {
    }

    /**
     * Una pregunta de la prueba. Las abiertas no llevan puntos (decisión 3): la IA califica el
     * criterio entero. Las cerradas sí, como en la fase 1.
     */
    public record GuardarPreguntaDePrueba(
            @NotBlank(message = "Falta el tipo de la pregunta") String tipo,
            @NotBlank(message = "Falta el enunciado")
            @Size(max = 2000, message = "El enunciado admite hasta 2000 caracteres")
            String enunciado,
            BigDecimal puntos,
            Long criterioId,
            @Size(max = 1000, message = "«Qué debe tener una buena respuesta» admite hasta 1000 caracteres")
            String queDebeTener,
            @Valid List<GuardarOpcion> opciones) {
    }

    /**
     * La guía de la IA, el caso y el tiempo del borrador. Lo que no venga queda vacío.
     *
     * <p>El tiempo es «Cronometrada» ({@code CRONOMETRADA} con sus minutos) o «Sin cronómetro»
     * ({@code PLAZO_ABIERTO}, sin días: se trabaja hasta la fecha límite de la vacante).
     */
    public record GuardarDatosDeLaPrueba(
            @Size(max = 2000, message = "La guía de calificación admite hasta 2000 caracteres")
            String guiaCalificacion,
            @Size(max = 10_000, message = "El enunciado admite hasta 10000 caracteres")
            String enunciado,
            @Size(max = 2000, message = "Los materiales admiten hasta 2000 caracteres")
            String materiales,
            @Size(max = 1000, message = "Las herramientas permitidas admiten hasta 1000 caracteres")
            String herramientasPermitidas,
            String modalidad,
            Integer duracionMinutos) {
    }

    /**
     * Un entregable: su nombre, qué debe contener (lo lee el candidato), su formato (ARCHIVO,
     * ENLACE o CUALQUIERA), si es obligatorio y «qué debe tener una buena entrega» (lo leen la
     * IA y quien califica).
     *
     * <p>Y su alcance (V68): {@code preguntaId} para el archivo de una pregunta (como mucho uno
     * por pregunta); si no, es general y cubre {@code todaLaPrueba} o las preguntas de
     * {@code cubre}. Los criterios que lo miran se deducen de eso.
     *
     * <p>Los dos sí/no admiten venir omitidos o nulos (Jackson 3 no deja un {@code null} en un
     * primitivo y respondía 500): {@code obligatorio} vale entonces sí, como el entregable que
     * propone la IA sin decirlo, y {@code todaLaPrueba} vale no, de modo que un general sin
     * alcance cae en la falta «Elige qué cubre» y no en un error.
     */
    public record GuardarEntregable(
            @NotBlank(message = "El entregable necesita un nombre")
            @Size(max = 200, message = "El nombre del entregable admite hasta 200 caracteres")
            String nombre,
            @Size(max = 1000, message = "«Qué debe contener» admite hasta 1000 caracteres")
            String detalle,
            String formato,
            Boolean obligatorio,
            @Size(max = 1000, message = "«Qué debe tener una buena entrega» admite hasta 1000 caracteres")
            String queDebeTener,
            Long preguntaId,
            Boolean todaLaPrueba,
            List<Long> cubre) {

        public GuardarEntregable {
            obligatorio = !Boolean.FALSE.equals(obligatorio);
            todaLaPrueba = Boolean.TRUE.equals(todaLaPrueba);
        }

        /** Un general que cubre toda la prueba, sin pregunta propia. */
        public GuardarEntregable(String nombre, String detalle, String formato, boolean obligatorio,
                                 String queDebeTener) {
            this(nombre, detalle, formato, obligatorio, queDebeTener, null, true, List.of());
        }
    }

    /**
     * La fecha límite para dar la prueba (V68): la de la vacante ({@code prueba_cierra_en}),
     * que se pone desde el editor. El motivo solo hace falta con la prueba publicada y alguien
     * ya en la etapa técnica: entonces queda en la auditoría.
     */
    public record FijarFechaLimite(
            @NotNull(message = "Falta la fecha límite") Instant cierraEn,
            @Size(max = 1000, message = "El motivo admite hasta 1000 caracteres") String motivo) {
    }

    /**
     * Corregir las instrucciones de la IA de una prueba publicada: la guía, el «qué evalúa»
     * de cada criterio, el «qué debe tener» de cada abierta y el de cada entregable. Solo lo
     * que venga se cambia. Recalifica los criterios de IA de quien ya tiene nota.
     */
    public record CorregirInstruccionesDePrueba(
            @Size(max = 2000, message = "La guía de calificación admite hasta 2000 caracteres")
            String guiaCalificacion,
            @Valid List<TextoDe> criterios,
            @Valid List<TextoDe> preguntas,
            @Valid List<TextoDe> entregables) {
    }

    /**
     * Los puntos nuevos de una prueba publicada: de cerradas, opciones y lo que vale cada
     * criterio con parte calificada (V69). Lo que no se dice se mantiene: un criterio con parte
     * calificada sigue valiendo lo mismo y su parte se ajusta a sus cerradas nuevas; uno solo
     * de cerradas vale lo que sumen.
     */
    public record CambiarPuntosDePrueba(@Valid List<PuntosDePregunta> preguntas,
                                        @Valid List<PuntosDeCriterio> criterios) {
    }

    /** Lo que vale un criterio entero; su parte calificada es eso menos sus cerradas. */
    public record PuntosDeCriterio(@NotNull Long id,
                                   @NotNull(message = "Faltan los puntos del criterio") BigDecimal puntos) {
    }

    /**
     * Lo que se agrega de una propuesta de la IA: el caso (si se quiere), los entregables
     * propuestos por su posición, los criterios enteros por su posición y preguntas sueltas.
     * Un criterio que mira entregables propuestos los agrega con él.
     */
    public record AgregarDeLaPropuestaDePrueba(boolean caso, List<Integer> entregables,
                                               List<Integer> criterios,
                                               List<PreguntaElegida> preguntas) {
    }

    // ============================== La ficha: la prueba de un candidato ==============================

    /**
     * La prueba de un candidato, criterio por criterio (punto 12).
     *
     * @param estado          SIN_PRUEBA · SIN_EMPEZAR · EN_CURSO · NO_COMPLETADA · ENTREGADA
     * @param nota            la nota de la etapa, o nula mientras falte algún criterio
     * @param loQueFalta      qué falta para tenerla, dicho por criterio
     * @param puedeAjustar    si quien mira tiene {@code ajustar_nota} y la prueba está entregada
     * @param recalificando   si hay una recalificación con la guía nueva en curso
     * @param motivoPendiente por qué la IA no terminó (sin saldo, apagada, falló), o nulo
     */
    public record PruebaDelCandidato(Long postulacionId, String estado, boolean cuestionario,
                                     Instant iniciadoEn, Instant entregadoEn,
                                     boolean entregaAutomatica, BigDecimal nota,
                                     List<String> loQueFalta, boolean puedeAjustar,
                                     boolean recalificando, String motivoPendiente,
                                     List<CriterioDelCandidato> criterios,
                                     List<EntregaVista> entregables) {
    }

    /**
     * Un criterio: su nota y de dónde sale («Sistema 8/10 + IA 16/20»).
     *
     * @param estado CALIFICADO · PENDIENTE (falta la parte calificada) · SOLO_SISTEMA
     */
    public record CriterioDelCandidato(Long criterioId, String nombre, String queEvalua,
                                       int maximo, int sistemaMaximo, BigDecimal sistema,
                                       int calificadaMaximo, String calificador,
                                       BigDecimal calificada, BigDecimal calificadaIa,
                                       BigDecimal nota, String estado, String explicacion,
                                       String evidencia, String origen,
                                       String ajustadaPor, Instant ajustadaEn,
                                       String motivoAjuste,
                                       List<PreguntaDelCandidato> preguntas,
                                       List<Long> entregables) {
    }

    /**
     * Una pregunta con lo que contestó. En las cerradas, las opciones con sus puntos y cuál
     * marcó; en las abiertas, el texto.
     */
    public record PreguntaDelCandidato(Long preguntaId, String tipo, String enunciado,
                                       String queDebeTener, Integer puntos, BigDecimal obtenido,
                                       String respuesta, boolean respondida,
                                       List<OpcionDelCandidato> opciones) {
    }

    public record OpcionDelCandidato(Long id, String texto, int puntos, boolean marcada) {
    }

    /**
     * Un entregable y cómo llegó. {@code enlace} y {@code archivoId} viajan solo con
     * {@code descargar_entregables}; {@code porQueNoSeVe} lo dice con palabras cuando no hay
     * contenido que enseñar. {@code preguntaId} (V68): la pregunta de la que es el archivo, para
     * enseñarlo junto a su respuesta; nulo en los generales.
     */
    public record EntregaVista(Long entregableId, String nombre, String detalle, String formato,
                               boolean obligatorio, String queDebeTener, boolean loEntrego,
                               String enlace, Long archivoId, String archivoNombre,
                               Instant subidoEn, String porQueNoSeVe, Long preguntaId) {
    }

    /** Ajustar (o poner) la parte calificada de un criterio, con su motivo. */
    public record AjustarCriterio(
            @NotNull(message = "Falta la nota") BigDecimal puntaje,
            @NotBlank(message = "El motivo es obligatorio")
            @Size(max = 1000, message = "El motivo admite hasta 1000 caracteres")
            String motivo) {
    }

    // ============================== Quienes no la completaron ==============================

    /**
     * Quien dejó vencer el tiempo con algo sin responder (decisión 11): no sale en el ranking
     * y su postulación sigue donde estaba hasta que alguien cierre su proceso.
     *
     * @param queFalto «le faltaron 3 preguntas», «le faltó «Tablero.xlsx»»
     */
    public record NoCompleto(Long postulacionId, String candidato, String estado,
                             String queFalto, Instant cerradaEn, boolean procesoCerrado) {
    }
}
