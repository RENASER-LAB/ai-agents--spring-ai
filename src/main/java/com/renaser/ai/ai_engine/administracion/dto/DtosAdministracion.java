package com.renaser.ai.ai_engine.administracion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

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

    /**
     * Una casilla de la matriz de permisos de un rol.
     *
     * <p>Sale el catálogo entero y no solo lo concedido: un panel que solo enseña lo que ya
     * está no deja añadir nada, y quien administra necesita ver también lo que falta. El
     * alcance vacío es exactamente eso — este rol no tiene este permiso.
     *
     * @param alcance {@code TODO}, {@code SUS_VACANTES}, {@code PROPIO} o vacío
     */
    public record PermisoDelRol(String codigo, String etiqueta, String grupo, Integer orden,
                                String alcance) {}

    /**
     * Conceder un permiso a un rol, o cambiarle el alcance si ya lo tenía.
     *
     * <p>El motivo es obligatorio y no es burocracia: esto cambia lo que un grupo de personas
     * puede hacer con los datos de los candidatos, y surte efecto en la siguiente petición de
     * cada una. Si alguien pregunta después por qué su equipo empezó a ver algo, la respuesta
     * tiene que estar escrita.
     */
    public record ConcederPermiso(
            @NotBlank @Pattern(regexp = "TODO|SUS_VACANTES|PROPIO",
                    message = "alcance debe ser TODO, SUS_VACANTES o PROPIO")
            String alcance,
            @NotBlank(message = "Cambiar lo que puede un rol exige un motivo escrito")
            String motivo) {}

    /** Quitarle un permiso a un rol. Mismo motivo obligatorio, por lo mismo. */
    public record RevocarPermiso(
            @NotBlank(message = "Cambiar lo que puede un rol exige un motivo escrito")
            String motivo) {}

    public record CrearArea(@NotBlank String nombre) {}

    public record AreaPanel(Long id, String nombre, boolean esActiva) {}
}
