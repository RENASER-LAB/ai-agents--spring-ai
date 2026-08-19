package com.renaser.ai.ai_engine.decision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// Los contratos de la decisión final. Ver RF-113 a RF-121.
public final class DtosDecision {

    private DtosDecision() {}

    public record BarreraResponse(Long id, String descripcion, boolean esActiva) {}

    public record CrearBarrera(@NotBlank String descripcion) {}

    public record BarreraDetectadaResponse(
            Long id, Long barreraCriticaId, String descripcion, String explicacion,
            Instant confirmadaEn, Instant descartadaEn) {}

    // Una persona la reporta directamente: en este MVP, sin agente todavía, no hay
    // "detección pendiente de confirmar" — quien la registra ya la está confirmando.
    public record RegistrarBarrera(@NotNull Long barreraCriticaId, @NotBlank String explicacion) {}

    public record SemaforoResponse(
            String semaforo,               // null si todavía no se puede calcular
            BigDecimal notaGlobal,
            List<String> etapasQueFaltan,
            List<BarreraDetectadaResponse> barrerasConfirmadas,
            Long decididaPorUsuarioId,
            String motivo,
            Instant decididaEn) {}

    public record Decidir(
            @NotBlank
            @Pattern(regexp = "VERDE|AMBAR|ROJO|SIN_DATOS|RESERVA",
                    message = "semaforo debe ser VERDE, AMBAR, ROJO, SIN_DATOS o RESERVA")
            String semaforo,
            @NotBlank String motivo) {}

    public record PedirEvidencia(@NotBlank String motivo, @NotBlank String enunciado) {}

    public record EvidenciaResponse(
            Long id, Integer numero, String motivo, String enunciado, Instant entregadaEn) {}
}
