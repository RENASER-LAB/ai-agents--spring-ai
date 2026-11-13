package com.renaser.ai.ai_engine.pesos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

// Los cuatro tipos de peso que arman una versión: etapa y componente (hito 1, nunca
// tuvieron API hasta ahora), dimensión y criterio (hito 2). Publicada nunca se modifica.
public final class DtosPesos {

    private DtosPesos() {}

    /**
     * @param pesoBusquedaCv cuánto pesa «Qué buscar en el CV» dentro de la nota del CV, en %, de
     *                       0 a 50 (V80). Opcional: si falta, 25.
     */
    public record CrearVersionPesos(@NotBlank String etiqueta, java.math.BigDecimal pesoBusquedaCv) {

        /** Sin el peso de «Qué buscar en el CV»: queda en 25. */
        public CrearVersionPesos(String etiqueta) {
            this(etiqueta, null);
        }
    }

    public record VersionPesosResponse(Long id, String etiqueta, String estado, Instant publicadaEn) {}

    public record CrearPesoEtapa(@NotBlank String etapaCodigo, @NotNull Double peso) {}

    public record PesoEtapaResponse(String etapaCodigo, Double peso) {}

    public record CrearPesoComponente(
            @NotBlank @Pattern(regexp = "CV|PSICOMETRICO|EVALUACION",
                    message = "componente debe ser CV, PSICOMETRICO o EVALUACION")
            String componente,
            @NotNull Double peso) {}

    public record PesoComponenteResponse(String componente, Double peso) {}

    public record CrearPesoDimension(
            @NotBlank String nivelPuestoCodigo,
            @NotBlank String dimensionCodigo,
            @NotNull Double peso) {}

    public record PesoDimensionResponse(String nivelPuestoCodigo, String dimensionCodigo, Double peso) {}

    public record CrearPesoCriterio(
            @NotBlank String nivelPuestoCodigo,
            @NotNull Long criterioId,
            @NotNull Double peso) {}

    public record PesoCriterioResponse(String nivelPuestoCodigo, Long criterioId, Double peso) {}
}
