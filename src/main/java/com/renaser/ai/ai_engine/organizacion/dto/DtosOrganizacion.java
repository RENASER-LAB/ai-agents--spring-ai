package com.renaser.ai.ai_engine.organizacion.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

// Los contratos de la organización: el alta de empresas y la personalización.
public final class DtosOrganizacion {

    private DtosOrganizacion() {}

    public record CrearEmpresa(@NotBlank String nombre, @NotBlank String codigo,
                               @NotBlank String correoAdministrador) {}

    // La respuesta lleva el enlace de la invitación del primer administrador, por lo
    // mismo que las invitaciones normales: quien da el alta puede vérselo llegar.
    public record EmpresaCreada(Long id, Long invitacionId, String urlInvitacion,
                                Instant invitacionVenceEn) {}

    public record EmpresaPanel(Long id, String codigo, String nombre, boolean esActiva,
                               Instant creadoEn) {}

    // Qué tiene personalizado la organización de quien pregunta: una casilla por bandera.
    public record Personalizacion(boolean bancoPropio, boolean pesosPropios,
                                  boolean plantillasEvaluacionPropias,
                                  boolean pruebasPuestoPropias) {}
}
