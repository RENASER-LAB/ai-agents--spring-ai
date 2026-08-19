package com.renaser.ai.ai_engine.validacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

// Los contratos de la validación práctica. Ver RF-105 a RF-112.
public final class DtosValidacion {

    private DtosValidacion() {}

    public record HabilitarValidacion(
            @NotBlank @Pattern(regexp = "SIMULACION_EXTENDIDA|TRABAJO_REAL",
                    message = "modalidad debe ser SIMULACION_EXTENDIDA o TRABAJO_REAL")
            String modalidad,
            /** Obligatorio si la modalidad es TRABAJO_REAL. Sin él no se puede habilitar. */
            String tipoVinculacion,
            @Positive Integer dias,
            Long responsableUsuarioId) {}

    public record ValidacionResponse(
            Long id, String modalidad, String tipoVinculacion, Integer dias,
            Instant inicioEn, Instant finEn, String estado,
            Long habilitadaPorUsuarioId, Long responsableUsuarioId) {}

    public record CompletarMetrica(
            @NotNull Double puntaje,
            @NotBlank String explicacion) {}

    public record MetricaResponse(
            Long criterioId, String nombre, Double puntosMaximos,
            Double puntaje, String explicacion, String origen) {}
}
