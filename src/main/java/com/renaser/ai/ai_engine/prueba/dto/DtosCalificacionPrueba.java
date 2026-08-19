package com.renaser.ai.ai_engine.prueba.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class DtosCalificacionPrueba {

    private DtosCalificacionPrueba() {}

    public record PonerNotaCriterio(
            @NotNull Double puntaje,
            @NotBlank String explicacion) {}

    public record NotaCriterioResponse(
            Long criterioId, String nombre, Double puntosMaximos,
            Double puntaje, String explicacion, String origen) {}
}
