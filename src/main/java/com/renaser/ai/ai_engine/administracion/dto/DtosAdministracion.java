package com.renaser.ai.ai_engine.administracion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

// Los contratos de configuración y administración del sistema.
public final class DtosAdministracion {

    private DtosAdministracion() {}

    public record EditarParametro(@NotBlank String valor, @NotBlank String motivo) {}

    public record ParametroPanel(String codigo, String valor, String tipo, String descripcion) {}

    public record NuevaPlantilla(@NotBlank String codigo, @NotBlank String asunto,
                                 @NotBlank String cuerpo) {}

    public record PlantillaPanel(Long id, String codigo, Integer version, String asunto,
                                 String cuerpo, boolean esActiva) {}

    public record FilaAuditoria(Long id, String accion, String entidad, Long entidadId,
                                Long usuarioId, String motivo, Instant ocurridaEn) {}

    public record SolicitudBorradoPanel(Long id, Long personaId, String motivo,
                                        Instant solicitadoEn, Instant ejecutadoEn) {}

    public record CrearUsuarioEquipo(@NotBlank String nombre, @NotBlank String apellidos,
                                     @NotBlank String correo, @NotBlank String usuarioRenaserOsId,
                                     Long areaId, @NotNull List<String> roles) {}

    public record UsuarioPanel(Long id, String correo, String usuarioRenaserOsId,
                               Long areaId, boolean esActivo, List<String> roles) {}

    public record AsignarRoles(@NotNull List<String> roles) {}

    public record RolPanel(Long id, String codigo, String nombre, boolean esSistema) {}

    public record CrearArea(@NotBlank String nombre) {}

    public record AreaPanel(Long id, String nombre, boolean esActiva) {}
}
