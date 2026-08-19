package com.renaser.ai.ai_engine.perfilintegral.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

// Los contratos de la "receta": qué combinación de preguntas arma la evaluación de un
// nivel y familia. Igual convención que el banco: sufijo Response + MapStruct.
public final class DtosPlantillaEvaluacion {

    private DtosPlantillaEvaluacion() {}

    public record CrearPlantilla(
            @NotBlank String nombre,
            @NotBlank String nivelPuestoCodigo,
            String familiaCodigo,
            @NotNull Integer version,
            @NotNull Integer minutosObjetivo,
            @NotNull Integer vigenciaMeses) {}

    public record PlantillaResponse(
            Long id,
            String nombre,
            String nivelPuestoCodigo,
            String familiaCodigo,
            Integer version,
            String estado,
            Integer minutosObjetivo,
            Integer vigenciaMeses,
            Instant publicadaEn) {}

    public record CrearCuota(
            @NotBlank @Pattern(regexp = "NIVEL|ALINEACION", message = "tipoBanco debe ser NIVEL o ALINEACION")
            String tipoBanco,
            @Pattern(regexp = "ESTILO|SITUACION|CONDUCTUAL|MICROCASO|DILEMA|CONSISTENCIA|",
                    message = "tipoPregunta debe ser uno de los 6 tipos de pregunta, o vacío")
            String tipoPregunta,
            String dimensionCodigo,
            @NotNull Integer cantidadMin,
            @NotNull Integer cantidadMax) {}

    public record CuotaResponse(
            Long id,
            Long plantillaEvaluacionId,
            String tipoBanco,
            String tipoPregunta,
            String dimensionCodigo,
            Integer cantidadMin,
            Integer cantidadMax) {}
}
