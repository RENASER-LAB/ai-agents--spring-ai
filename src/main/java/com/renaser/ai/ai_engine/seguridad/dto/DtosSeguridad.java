package com.renaser.ai.ai_engine.seguridad.dto;

import jakarta.validation.constraints.NotBlank;

// Los contratos de entrada al sistema.
public final class DtosSeguridad {

    private DtosSeguridad() {}

    public record DevLogin(@NotBlank String usuarioRenaserOsId) {}

    public record Sesion(String token, Long usuarioId) {}
}
