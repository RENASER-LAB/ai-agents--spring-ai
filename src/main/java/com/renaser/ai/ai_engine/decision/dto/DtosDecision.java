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
            @NotBlank String motivo,

            // Contratar sabiendo que faltan notas de etapas que la vacante pesa.
            //
            // Hay puestos que se saltan simulación o validación a propósito, y sus notas no
            // van a existir nunca: sin esta casilla, exigir todas las etapas dejaría a esos
            // candidatos sin poder ser contratados jamás. Pero contratar con media evidencia
            // tampoco puede pasar en silencio, así que se pide decirlo — y queda registrado
            // qué etapas faltaban.
            //
            // Ausente o false significa «no lo he considerado», que es lo correcto por
            // defecto: quien no sabe que faltan notas no está reconociendo nada.
            Boolean aunqueFaltenEtapas) {

        public boolean reconoceQueFaltanEtapas() {
            return Boolean.TRUE.equals(aunqueFaltenEtapas);
        }
    }

    public record PedirEvidencia(@NotBlank String motivo, @NotBlank String enunciado) {}

    public record EvidenciaResponse(
            Long id, Integer numero, String motivo, String enunciado, Instant entregadaEn) {}
}
