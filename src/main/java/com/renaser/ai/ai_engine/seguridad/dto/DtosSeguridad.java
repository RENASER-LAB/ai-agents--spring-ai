package com.renaser.ai.ai_engine.seguridad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
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

    /**
     * Una contraseña nueva no empieza ni termina en espacio. Casi siempre es un espacio que
     * se coló al pegarla, y recortarlo en silencio dejaría a la persona con una contraseña
     * distinta de la que cree haber puesto: se rechaza y se dice. Vale para las dos puertas.
     */
    public static final String SIN_ESPACIOS_EN_LOS_BORDES = "\\S(?:[\\s\\S]*\\S)?";
    public static final String MENSAJE_ESPACIOS_EN_LOS_BORDES =
            "La contraseña no puede empezar ni terminar con un espacio";

    // Pedir el enlace de contraseña nueva. Sin validación a propósito: la respuesta es
    // siempre la misma, y un 400 por un correo mal escrito sería la única que no lo fuera.
    public record PedirRecuperacion(String correo) {}

    // El token va sin @NotBlank: uno vacío es un enlace que no sirve, y contesta el mismo
    // 401 que los demás. La contraseña, con la regla del panel (12, como la invitación) y el
    // tope de BCrypt (72 bytes).
    public record RestablecerClave(String token,
                                   @NotBlank(message = "Escribe la contraseña nueva")
                                   @Size(min = 12, message = "La contraseña necesita al menos 12 caracteres")
                                   @Pattern(regexp = SIN_ESPACIOS_EN_LOS_BORDES,
                                           message = MENSAJE_ESPACIOS_EN_LOS_BORDES)
                                   @CabeEnBcrypt
                                   String contrasena) {}

    public record CrearInvitacion(@NotBlank String correo, @NotEmpty List<String> roles) {}

    // La respuesta lleva el enlace completo a quien creó la invitación: en un entorno sin
    // correo de verdad (transporte «log») es la única forma de hacérselo llegar al
    // invitado, y quien puede crear invitaciones puede ver este enlace — es el mismo
    // criterio que el enlace de acceso del candidato, que el panel también devuelve.
    public record InvitacionCreada(Long id, String url, Instant venceEn) {}

    public record InvitacionPanel(Long id, String correo, List<String> roles, Instant venceEn,
                                  Instant aceptadaEn, Instant revocadaEn) {}
}
