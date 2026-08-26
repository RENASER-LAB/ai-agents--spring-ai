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

    // Los textos legales de la organización. La versión es opcional: en blanco, el
    // sistema numera la siguiente («2.0» tras la «1.0» del alta). El tipo es PROCESO o
    // FUTUROS_CONTACTOS, los dos de texto_consentimiento desde la V3.
    public record NuevoTextoConsentimiento(@NotBlank String tipo, @NotBlank String texto,
                                           String version) {}

    public record TextoConsentimientoPanel(Long id, String tipo, String version, String texto,
                                           Instant publicadoEn) {}

    public record FilaAuditoria(Long id, String accion, String entidad, Long entidadId,
                                Long usuarioId, String motivo, Instant ocurridaEn) {}

    public record SolicitudBorradoPanel(Long id, Long personaId, String motivo,
                                        Instant solicitadoEn, Instant ejecutadoEn) {}

    // usuarioRenaserOsId pasó a opcional con RENASER OS dormido: hoy una cuenta de equipo
    // se crea con correo y entra por invitación o contraseña, no por RENASER OS.
    public record CrearUsuarioEquipo(@NotBlank String nombre, @NotBlank String apellidos,
                                     @NotBlank String correo, String usuarioRenaserOsId,
                                     Long areaId, @NotNull List<String> roles) {}

    public record UsuarioPanel(Long id, String correo, String usuarioRenaserOsId,
                               Long areaId, boolean esActivo, List<String> roles) {}

    public record AsignarRoles(@NotNull List<String> roles) {}

    public record RolPanel(Long id, String codigo, String nombre, boolean esSistema) {}

    public record CrearArea(@NotBlank String nombre) {}

    public record AreaPanel(Long id, String nombre, boolean esActiva) {}
}
