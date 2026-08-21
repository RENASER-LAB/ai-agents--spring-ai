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
     *
     * <p>Los tres campos de respuesta son lo que ya contestó, y existen para que pueda
     * <b>retomar</b>: el examen del banco v3 son 190 ítems, nadie los responde de una sentada.
     * Si al volver los viera en blanco, o rehace todo o entrega a medias.
     *
     * <p>{@code respuestaDetalle} es el tercero, y hacía falta porque 162 de esos 190 ítems no
     * se responden con una sola opción —un SJT-R califica cada opción, un EF-4 marca dos, un
     * SEC ordena cinco pasos— y eso no cabe en {@code respuestaOpcionId} ni en
     * {@code respuestaTexto}. Tiene la misma forma que el {@code detalle} de {@link Responder}:
     * lo que se manda al guardar es lo que vuelve al leer, y así el portal pinta la pregunta
     * con el mismo código con que la envió.
     *
     * <p><b>Vuelve solo lo que escribió el candidato</b>, clave por clave según el formato. No
     * es que hoy haya otra cosa guardada ahí —solo escribe ese campo el propio candidato— sino
     * que la columna es {@code jsonb} y admite cualquier cosa: filtrando al salir, ningún
     * puntaje que alguien guarde ahí mañana puede llegar al navegador.
     *
     * <p>{@code casosPedidos} es <b>cuántos campos hay que llenar</b> en un caso descompuesto
     * (CD), y solo lo traen los CD: en los demás formatos viene en nulo. Sin él, el portal
     * tenía que adivinar el número leyendo el «(6 campos)» del propio enunciado, y cuando no
     * lo encontraba pintaba un solo cuadro para un caso de seis datos.
     *
     * <p><b>No es clave</b> y por eso puede salir (RF-53): el enunciado que el candidato ya
     * está leyendo dice ese mismo número entre paréntesis. Dice cuántas casillas llenar, no
     * qué poner en ellas ni cuánto vale ponerlo.
     */
    public record PreguntaCandidato(
            Long id,
            Integer posicion,
            String tipo,
            String enunciado,
            String situacion,
            Short casosPedidos,
            List<OpcionCandidato> opciones,
            String respuestaTexto,
            Long respuestaOpcionId,
            Map<String, Object> respuestaDetalle) {}

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
