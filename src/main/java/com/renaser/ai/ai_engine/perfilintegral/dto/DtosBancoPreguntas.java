package com.renaser.ai.ai_engine.perfilintegral.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

// Los contratos del banco de preguntas. Convención nueva para el hito 2: sufijo Response
// y mapeo por MapStruct (ver mapper/), a diferencia del hito 1 que mapea a mano. Se parte
// por sub-dominio para no crecer un solo archivo.
//
// Los @Pattern en los catálogos cerrados son a propósito: en el hito 1 un valor inválido
// en un campo así (ej. urgencia) rompía con un 500 de Postgres en vez de un 400 claro,
// porque el DTO no validaba antes de llegar a la base.
//
// Estos DTOs los consume SOLO el panel. El portal del candidato tiene los suyos
// (DtosEvaluacion) y por ahí no viaja ninguna clave: ni puntaje, ni valor, ni la lógica
// interna. En el panel sí se ven —quien edita el banco necesita ver la clave que escribió—
// con una sola excepción: logicaInterna entra pero jamás sale (RF-53).
public final class DtosBancoPreguntas {

    private DtosBancoPreguntas() {}

    public record CrearVersionBanco(
            @NotBlank @Pattern(regexp = "NIVEL|ALINEACION",
                    message = "tipoBanco debe ser NIVEL o ALINEACION")
            String tipoBanco,
            String nivelPuestoCodigo,
            @NotBlank String etiqueta) {}

    public record VersionBancoResponse(
            Long id,
            String tipoBanco,
            String nivelPuestoCodigo,
            String etiqueta,
            String estado,
            Instant publicadaEn) {}

    // Los 14 formatos: los 6 del banco v0.1 (siguen siendo válidos porque hay preguntas
    // publicadas con ellos) y los 8 del v3. Espejo exacto del CHECK pregunta_tipo_check
    // de la V20 — si un día entra un formato nuevo, se amplía allí y aquí.
    public record CrearPregunta(
            @NotBlank String codigo,
            String bloque,
            @NotBlank @Pattern(regexp =
                    "ESTILO|SITUACION|CONDUCTUAL|MICROCASO|DILEMA|CONSISTENCIA"
                    + "|EF-4|SJT-R|SEC|INV|DE|CD|V|PC",
                    message = "tipo debe ser uno de los 14 formatos del banco")
            String tipo,
            @NotBlank String enunciado,
            String situacion,
            // Entra pero nunca sale: no está en PreguntaResponse ni en ningún otro DTO (RF-53).
            String logicaInterna,
            @NotNull Boolean esPuntuable,
            @NotNull Integer orden,
            // --- Los campos de puntuación del v3 ---
            /** 0 no suma, 1 vale hasta 3 puntos, 2 hasta 6. Espejo del CHECK de la V20. */
            @Min(value = 0, message = "peso debe estar entre 0 y 2")
            @Max(value = 2, message = "peso debe estar entre 0 y 2")
            Short peso,
            /** El ítem clave (★): hay que preguntar por él en la entrevista. */
            Boolean esClave,
            /** Descarta al candidato por sí solo, con independencia del puntaje. */
            Boolean esEliminatorio,
            /** Solo CD: el denominador con el que el motor puntúa los campos. */
            @Positive Short casosPedidos,
            /** Solo V: si remite a la tabla de tramos de otro ítem, su código. Puede vivir
             *  en otro banco (C36 remite a D57), así que no se valida contra esta versión. */
            String rangosDePreguntaCodigo,
            /** Solo V: si en vez de tabla trae la fórmula escrita. */
            String formulaPuntaje) {}

    // logicaInterna queda fuera a propósito: no sale de la base (RF-53).
    public record PreguntaResponse(
            Long id,
            Long versionBancoId,
            String codigo,
            String bloque,
            String tipo,
            String enunciado,
            String situacion,
            boolean esPuntuable,
            Integer orden,
            Short peso,
            boolean esClave,
            boolean esEliminatorio,
            Short casosPedidos,
            String rangosDePreguntaCodigo,
            String formulaPuntaje) {}

    // La letra admite lo que el banco v3 trae de verdad: minúsculas ('a'..'e'), series
    // desambiguadas ('a2', 'b3') y los pasos de SEC numerados ('1'..'5').
    public record CrearOpcion(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9]{1,2}",
                    message = "letra debe ser 1 o 2 caracteres alfanuméricos")
            String letra,
            @NotBlank String texto,
            /** v0.1: puntos del ítem. SJT-R: la calificación esperada, de 1 a 5. */
            Double puntaje,
            /** EF-4: el valor oculto de −2 a +2. La V20 no le puso CHECK: esta es la única guarda. */
            @DecimalMin(value = "-2", message = "valor va de -2 a 2")
            @DecimalMax(value = "2", message = "valor va de -2 a 2")
            BigDecimal valor,
            /** INV y DE: si el elemento es de los inventados. */
            Boolean esDistractor,
            /** SEC: el lugar que le toca a este paso en el orden correcto. */
            @Positive Short ordenCorrecto) {}

    public record OpcionResponse(
            Long id,
            Long preguntaId,
            String letra,
            String texto,
            Double puntaje,
            BigDecimal valor,
            boolean esDistractor,
            Short ordenCorrecto) {}

    // --- Las tablas de puntaje de los ítems V ---

    public record CrearRango(
            @NotNull @Positive Integer orden,
            @NotBlank String condicion,
            @NotNull
            @DecimalMin(value = "0", message = "puntaje va de 0 a 3, como toda fórmula del v3")
            @DecimalMax(value = "3", message = "puntaje va de 0 a 3, como toda fórmula del v3")
            BigDecimal puntaje,
            Boolean generaBandera) {}

    public record RangoResponse(
            Long id,
            Long preguntaId,
            Integer orden,
            String condicion,
            BigDecimal puntaje,
            boolean generaBandera) {}

    // --- Los campos de los casos descompuestos (CD) ---

    public record CrearCampoCaso(
            @NotNull @Positive Integer orden,
            @NotBlank String etiqueta,
            String validacion) {}

    public record CampoCasoResponse(
            Long id,
            Long preguntaId,
            Integer orden,
            String etiqueta,
            String validacion) {}

    // --- Los pares de consistencia ---
    // diferenciaMaxima es la regla del v0.1; penalización/separación/condición son la del v3.
    // Se admiten ambas porque hay pares de las dos generaciones en la tabla.
    public record CrearParConsistencia(
            @NotNull Long preguntaAId,
            @NotNull Long preguntaBId,
            BigDecimal diferenciaMaxima,
            BigDecimal penalizacionPorcentaje,
            @Positive Short separacionMinimaItems,
            String condicion) {}

    public record ParConsistenciaResponse(
            Long id,
            Long versionBancoId,
            Long preguntaAId,
            Long preguntaBId,
            BigDecimal diferenciaMaxima,
            BigDecimal penalizacionPorcentaje,
            Short separacionMinimaItems,
            String condicion) {}

    // --- La corrección editorial de una versión PUBLICADA ---
    // Estos records existen para que el candado quede en la firma y no en un if: no
    // llevan, por construcción, ningún campo de clave (valor, puntaje, ordenCorrecto,
    // esDistractor, peso, esPuntuable, casosPedidos, tipo, codigo). Corregir la errata
    // de un texto publicado es legítimo; tocar la puntuación bajo un examen en curso,
    // jamás (RF-138). Campo en null = no se toca.

    public record CorregirTextoPregunta(
            String enunciado,
            String situacion,
            String logicaInterna) {}

    public record CorregirTextoOpcion(@NotBlank String texto) {}

    public record CorregirTextoCampoCaso(
            String etiqueta,
            String validacion) {}

    public record CorregirTextoRango(@NotBlank String condicion) {}

    public record CorregirTextoPar(@NotBlank String condicion) {}

    public record CorregirEtiquetaVersion(@NotBlank String etiqueta) {}

    // --- El catálogo de dimensiones ---
    // Lo que una pregunta puede medir. El panel lo necesita para llenar la columna
    // «Qué mide» del Excel con valores que el importador acepte.
    public record DimensionResponse(
            String codigo,
            String nombre,
            String definicion,
            Integer orden) {}
}
