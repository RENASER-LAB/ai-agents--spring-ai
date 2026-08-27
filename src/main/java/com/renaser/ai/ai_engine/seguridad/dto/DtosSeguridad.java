package com.renaser.ai.ai_engine.seguridad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

// Los contratos de entrada al sistema.
public final class DtosSeguridad {

    private DtosSeguridad() {}

    public record DevLogin(@NotBlank String usuarioRenaserOsId) {}

    public record Login(@NotBlank String correo, @NotBlank String contrasena) {}

    public record Sesion(String token, Long usuarioId) {}

    // La contraseña del panel exige más que la del portal (mínimo 12): una cuenta de
    // equipo ve los datos de muchas personas, no solo los suyos.
    public record AceptarInvitacion(@NotBlank String token,
                                    @NotBlank String nombre,
                                    @NotBlank String apellidos,
                                    @NotBlank @Size(min = 12) String contrasena) {}

    public record CrearInvitacion(@NotBlank String correo, @NotEmpty List<String> roles) {}

    // La respuesta lleva el enlace completo a quien creó la invitación: en un entorno sin
    // correo de verdad (transporte «log») es la única forma de hacérselo llegar al
    // invitado, y quien puede crear invitaciones puede ver este enlace — es el mismo
    // criterio que el enlace de acceso del candidato, que el panel también devuelve.
    public record InvitacionCreada(Long id, String url, Instant venceEn) {}

    public record InvitacionPanel(Long id, String correo, List<String> roles, Instant venceEn,
                                  Instant aceptadaEn, Instant revocadaEn) {}
}
