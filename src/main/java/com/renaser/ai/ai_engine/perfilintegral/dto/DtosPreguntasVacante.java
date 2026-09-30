package com.renaser.ai.ai_engine.perfilintegral.dto;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioPropuesto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Los contratos del editor de preguntas propias de una vacante (V66).
 *
 * <p><b>Los puntos entran como número decimal y se exigen enteros al validar</b>, no con un
 * {@code Integer}: Jackson convierte en silencio un 2,5 en 2, y la regla es que un decimal
 * se rechaza con un 400 que lo diga.
 *
 * <p>Nada de esto llega al portal del candidato: sus DTO ({@code DtosEvaluacion}) no tienen
 * campo para puntos, claves ni criterios (RF-53).
 */
public final class DtosPreguntasVacante {

    private DtosPreguntasVacante() {
    }

    // ============================== Lo que entra ==============================

    public record GuardarCriterio(
            @NotBlank(message = "El criterio necesita un nombre")
            @Size(max = 120, message = "El nombre del criterio admite hasta 120 caracteres")
            String nombre,
            @Size(max = 1000, message = "«Qué evalúa» admite hasta 1000 caracteres")
            String queEvalua) {
    }

    /**
     * Una pregunta del borrador. {@code criterioId} vacío la deja sin criterio, salvo que el
     * borrador no tenga ninguno: entonces nace «General» y va dentro.
     */
    public record GuardarPregunta(
            @NotBlank(message = "Falta el tipo de la pregunta") String tipo,
            @NotBlank(message = "Falta el enunciado")
            @Size(max = 2000, message = "El enunciado admite hasta 2000 caracteres")
            String enunciado,
            @NotNull(message = "Faltan los puntos de la pregunta") BigDecimal puntos,
            Long criterioId,
            @Size(max = 1000, message = "«Qué debe tener una buena respuesta» admite hasta 1000 caracteres")
            String queDebeTener,
            @Valid List<GuardarOpcion> opciones) {
    }

    public record GuardarOpcion(
            @Size(max = 500, message = "El texto de una opción admite hasta 500 caracteres")
            String texto,
            @NotNull(message = "Cada opción necesita sus puntos") BigDecimal puntos) {
    }

    public record Mover(@NotBlank String direccion) {
    }

    public record GuardarDatosDelBorrador(
            @Size(max = 2000, message = "La guía de calificación admite hasta 2000 caracteres")
            String guiaCalificacion,
            @Min(value = 1, message = "Los minutos estimados van de 1 a 600")
            @Max(value = 600, message = "Los minutos estimados van de 1 a 600")
            Integer minutosObjetivo) {
    }

    /**
     * Corregir las instrucciones de la IA de una versión publicada: la guía, el «qué evalúa»
     * de cada criterio y el «qué debe tener» de cada abierta. Solo lo que venga se cambia.
     */
    public record CorregirInstrucciones(
            @Size(max = 2000, message = "La guía de calificación admite hasta 2000 caracteres")
            String guiaCalificacion,
            @Valid List<TextoDe> criterios,
            @Valid List<TextoDe> preguntas) {
    }

    public record TextoDe(@NotNull Long id,
                          @Size(max = 1000, message = "Admite hasta 1000 caracteres") String texto) {
    }

    /** Los puntos nuevos de una versión publicada: de cada pregunta y de cada opción o nivel. */
    public record CambiarPuntos(@NotNull @Valid List<PuntosDePregunta> preguntas) {
    }

    public record PuntosDePregunta(@NotNull Long id, @NotNull BigDecimal puntos,
                                   @Valid List<PuntosDeOpcion> opciones) {
    }

    public record PuntosDeOpcion(@NotNull Long id, @NotNull BigDecimal puntos) {
    }

    public record CopiarDeOtraVacante(@NotNull Long vacanteOrigenId) {
    }

    public record PedirRecomendaciones(
            @Size(max = 1000, message = "La indicación admite hasta 1000 caracteres")
            String indicacion) {
    }

    /**
     * Lo que se agrega de una propuesta. {@code criterios}: los criterios propuestos que se
     * agregan enteros, por su posición. {@code preguntas}: preguntas sueltas, por la posición
     * de su criterio y la suya.
     */
    public record AgregarDeLaPropuesta(List<Integer> criterios, List<PreguntaElegida> preguntas) {
    }

    public record PreguntaElegida(int criterio, int pregunta) {
    }

    public record AjustarNota(
            @NotNull(message = "Falta la nota") BigDecimal puntaje,
            @NotBlank(message = "El motivo es obligatorio")
            @Size(max = 1000, message = "El motivo admite hasta 1000 caracteres")
            String motivo) {
    }

    // ============================== Lo que sale ==============================

    /**
     * El editor entero. {@code puedeEditar} viaja aquí porque el panel no sabe sus permisos:
     * sin {@code editar_vacante} (o con la vacante cerrada o archivada) se pinta en lectura.
     */
    public record EditorDePreguntas(Long vacanteId, String titulo, String nivel,
                                    String origen, boolean aplicaEvaluacion,
                                    boolean puedeEditar, boolean hayPostulantes,
                                    VersionDePreguntas borrador, VersionDePreguntas publicada,
                                    ResumenDePreguntas resumen,
                                    Recalificacion recalificacion) {
    }

    /**
     * Una versión, con el balance ya calculado por el servidor: el panel lo pinta tal cual y
     * no lo cuadra por su cuenta.
     *
     * @param avisos lo que frena la publicación, dicho entero (vacío si se puede publicar)
     */
    public record VersionDePreguntas(Long id, String estado, String guiaCalificacion,
                                     Integer minutosObjetivo, int versionGuia, int total,
                                     int cuantosCriterios, int cuantasPreguntas,
                                     List<CriterioDeLaVersion> criterios,
                                     List<PreguntaDeLaVersion> sinCriterio,
                                     List<String> avisos) {
    }

    /** Un criterio: sus puntos son la suma de sus preguntas, partidos en sistema e IA. */
    public record CriterioDeLaVersion(Long id, String nombre, String queEvalua, int orden,
                                      int puntos, int puntosSistema, int puntosIa,
                                      List<PreguntaDeLaVersion> preguntas) {
    }

    public record PreguntaDeLaVersion(Long id, String tipo, String enunciado, int puntos,
                                      Long criterioId, int orden, String queDebeTener,
                                      List<OpcionDeLaVersion> opciones) {
    }

    /** Una opción o un nivel. En la escala, {@code orden} es el número del nivel. */
    public record OpcionDeLaVersion(Long id, String texto, int puntos, int orden) {
    }

    /**
     * El estado de las preguntas propias de una vacante, para su sección en la ficha de la
     * vacante: «Sin preguntas», «Borrador · 68 de 100 puntos», «Publicadas · 4 criterios · 12
     * preguntas».
     *
     * @param estado SIN_PREGUNTAS · BORRADOR · PUBLICADAS
     */
    public record ResumenDePreguntas(String estado, Integer puntos, Integer criterios,
                                     Integer preguntas) {
    }

    /**
     * Cómo va la recalificación de quienes ya tienen nota.
     *
     * @param rindieron      cuántas personas entregaron su evaluación: a quienes recalcula un
     *                       cambio de puntos
     * @param conNota        cuántas personas tienen abiertas calificadas por la IA
     * @param alDia          cuántas tienen ya todas sus notas con la guía vigente
     * @param recalificando  cuántas tienen una recalificación en marcha
     * @param pendientes     cuántas conservan una nota de una guía anterior y no avanzan
     * @param motivos        por qué se detuvieron las pendientes, sin repetir
     */
    public record Recalificacion(int rindieron, int conNota, int alDia, int recalificando, int pendientes,
                                 List<String> motivos) {
    }

    /** Una vacante de la que se pueden copiar las preguntas. */
    public record VacanteCopiable(Long vacanteId, String titulo, String nivel,
                                  Instant publicadaEn, String estado, int criterios,
                                  int preguntas) {
    }

    /**
     * La recomendación de la IA, para el sondeo del panel.
     *
     * @param estado SIN_PEDIR · EN_CURSO · LISTA · FALLIDA · DETENIDA
     */
    public record EstadoDeLaRecomendacion(String estado, String motivo, Long propuestaId,
                                          Integer puntosQueFaltan, String indicacion,
                                          List<CriterioPropuesto> propuesta) {
    }

    /** Lo que contesta pedir recomendaciones: si quedó en la cola, y si no, por qué. */
    public record RecomendacionPedida(boolean encolada, String mensaje) {
    }

    /**
     * Lo que contesta un cambio que recalcula o recalifica a la gente de la vacante.
     *
     * @param personas a cuántas alcanzó
     * @param motivo   por qué no alcanzó a quien quedaba (la IA apagada, sin cupo…), o nulo
     */
    public record CambioAplicado(int personas, String motivo) {
        public CambioAplicado(int personas) {
            this(personas, null);
        }
    }
}
