package com.renaser.ai.ai_engine.portal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

// Los contratos del portal del candidato, juntos: son pequeños y se leen mejor así.
public final class DtosPortal {

    private DtosPortal() {}

    // ---------- lo que entra ----------

    public record CrearCuenta(
            @NotBlank String nombre,
            @NotBlank String apellidos,
            @NotBlank @Email String correo,
            @NotBlank @Size(min = 8, message = "La contraseña necesita al menos 8 caracteres") String contrasena,
            @NotNull Boolean aceptaProceso,
            Boolean aceptaFuturosContactos) {}

    public record Login(@NotBlank String correo, @NotBlank String contrasena) {}

    public record PedirBorrado(String motivo) {}

    // ---------- lo que sale ----------

    public record VacantePublica(Long id, String titulo, String descripcion, String proposito,
                                 String responsabilidades, String requisitos, String modalidad,
                                 String horario, String ubicacion, String compensacionPublica,
                                 List<RequisitoPublico> requisitosObjetivos) {}

    public record RequisitoPublico(Long id, String descripcion) {}

    public record TextoConsentimientoPublico(String tipo, String version, String texto) {}

    public record Sesion(String token, Long usuarioId) {}

    public record MiPostulacion(String uuid, String vacante, String estado, String estadoNombre,
                                String grupoPrioridad, long diasSinCambio, Instant creadoEn) {}

    public record PasoHistorial(String estadoAnterior, String estadoNuevo, boolean fueElSistema,
                                Instant ocurridaEn) {}

    public record MiPostulacionDetalle(MiPostulacion resumen, List<PasoHistorial> historial) {}
}
