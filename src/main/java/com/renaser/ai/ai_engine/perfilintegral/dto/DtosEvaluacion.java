package com.renaser.ai.ai_engine.perfilintegral.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Los contratos de la evaluación que responde el candidato.
 *
 * <p>Aquí la regla que manda es RF-53: <b>el candidato nunca ve la clave.</b> Ni el puntaje de
 * cada opción, ni la lógica interna de la pregunta, ni el código de dimensión que mide. Si eso
 * viaja al navegador, cualquiera lo lee y el banco entero queda inutilizado.
 *
 * <p>Por eso estos records <b>no tienen</b> esos campos. No es que se filtren al serializar:
 * es que no existen en el contrato, que es la única forma de que nadie los añada por descuido.
 */
public final class DtosEvaluacion {

    private DtosEvaluacion() {}

    /**
     * Una opción tal como la ve el candidato: su id, la letra y el texto. Sin puntaje.
     *
     * <p>El id hace falta para poder responder: es lo que se manda de vuelta. Lo que no sale
     * de aquí es cuánto vale la opción ni qué dimensión mide.
     */
    public record OpcionCandidato(Long id, String letra, String texto) {}

    /**
     * Una pregunta tal como la ve el candidato.
     *
     * <p>{@code situacion} es el contexto del caso, que sí se muestra. {@code logicaInterna}
     * y {@code esPuntuable} se quedan fuera a propósito.
     */
    public record PreguntaCandidato(
            Long id,
            Integer posicion,
            String tipo,
            String enunciado,
            String situacion,
            List<OpcionCandidato> opciones,
            String respuestaTexto,
            Long respuestaOpcionId) {}

    /** La evaluación completa, con su avance. */
    public record EvaluacionCandidato(
            Long id,
            String estado,
            Instant venceEn,
            Instant iniciadaEn,
            Instant terminadaEn,
            Integer minutosObjetivo,
            int total,
            int respondidas,
            List<PreguntaCandidato> preguntas) {}

    /**
     * Lo que envía el candidato al responder.
     *
     * <p>Una de las tres: {@code opcionId} para las de opción única, {@code texto} para las
     * abiertas, y {@code detalle} para los formatos del banco v3, que necesitan más de un
     * valor. {@code segundos} es cuánto tardó, que sirve para detectar respuestas
     * apresuradas — no para penalizar a nadie.
     *
     * <p>La forma de {@code detalle} depende del tipo de la pregunta, y se comprueba contra él
     * al guardar:
     *
     * <pre>{@code
     * EF-4    {"mas": 12, "menos": 15}                      ids de opción, distintos
     * SJT-R   {"calificaciones": {"12": 5, "13": 2}}        id de opción -> 1..5
     * SEC     {"orden": [14, 12, 13, 15, 16]}               ids en el orden que eligió
     * INV/DE  {"marcadas": [12, 14, 17]}                    ids de lo que marcó
     * CD      {"campos": {"1": "...", "2": "..."}}          nº de campo -> lo que puso
     * }</pre>
     */
    public record Responder(
            Long opcionId,
            @Size(max = 20_000, message = "La respuesta es demasiado larga")
            String texto,
            Map<String, Object> detalle,
            Integer segundos) {}

    /** Lo que devuelve entregar la evaluación. */
    public record EntregaResponse(String estado, int respondidas, int total) {}
}
