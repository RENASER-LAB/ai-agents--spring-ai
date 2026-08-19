package com.renaser.ai.ai_engine.ai.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

// Los contratos de administración de los 9 agentes de IA del hito 2 de selección
// (docs/07-DICCIONARIO-DE-DATOS.md §18) y sus instrucciones versionadas. Viven junto al
// resto de agentes por separación de responsabilidades (mecánica de IA en un solo sitio),
// pero siguen la convención del panel de selección porque eso es lo que exponen:
// Response + MapStruct, y @PreAuthorize con los permisos sembrados en V12.
public final class DtosAgentesIa {

    private DtosAgentesIa() {}

    public record AgenteResponse(String codigo, String nombre, String descripcion, Integer version, boolean esActivo) {}

    public record CrearInstruccion(@NotBlank String agenteCodigo, @NotBlank String texto) {}

    public record InstruccionResponse(
            Long id, String agenteCodigo, Integer version, String texto, boolean esActiva, Instant publicadaEn) {}
}
