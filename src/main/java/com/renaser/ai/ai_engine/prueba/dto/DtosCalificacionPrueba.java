package com.renaser.ai.ai_engine.prueba.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class DtosCalificacionPrueba {

    private DtosCalificacionPrueba() {}

    public record PonerNotaCriterio(
            @NotNull Double puntaje,
            @NotBlank String explicacion) {}

    public record NotaCriterioResponse(
            Long criterioId, String nombre, Double puntosMaximos,
            Double puntaje, String explicacion, String origen) {}

    /** Lo que se contesta al pedirle al agente que califique. */
    public record CalificacionIaEncolada(String estado, String mensaje) {}

    /**
     * Ponerle a UN candidato la fecha en que se le cierra la prueba.
     *
     * <p>Es una fecha y no un número de días a propósito: lo que se quiere decir es «hasta el
     * domingo a medianoche», y eso mismo para todos. Los días se cuentan desde que cada uno
     * empieza, así que dan una fecha distinta por persona.
     */
    public record DefinirPlazoPrueba(@NotNull Instant venceEn, @NotBlank String motivo) {}

    public record PlazoPrueba(Long postulacionId, Instant venceEn, boolean yaEmpezo) {}
}
