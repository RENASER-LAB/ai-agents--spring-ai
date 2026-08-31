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

    // ciudadUbigeo es obligatoria y solo se pide aquí: a quien ya tiene cuenta no se le
    // vuelve a preguntar nunca. Es el único momento en que sale gratis —el formulario ya
    // está abierto— y sin ella el panel no puede filtrar la tanda por dónde vive nadie.
    public record CrearCuenta(
            @NotBlank String nombre,
            @NotBlank String apellidos,
            @NotBlank @Email String correo,
            @NotBlank @Size(min = 8, message = "La contraseña necesita al menos 8 caracteres") String contrasena,
            @NotBlank(message = "Hay que decir dónde vives") String ciudadUbigeo,
            @NotNull Boolean aceptaProceso,
            Boolean aceptaFuturosContactos) {}

    public record Login(@NotBlank String correo, @NotBlank String contrasena) {}

    public record PedirBorrado(String motivo) {}

    // ---------- lo que sale ----------

    // nombreEmpresa existe porque el tablón mezcla vacantes de todas las empresas: sin
    // él, el candidato no sabría a quién le está mandando su currículum.
    public record VacantePublica(Long id, String titulo, String nombreEmpresa, String descripcion,
                                 String proposito, String responsabilidades, String requisitos,
                                 String modalidad, String horario, String ubicacion,
                                 String compensacionPublica,
                                 List<RequisitoPublico> requisitosObjetivos) {}

    public record RequisitoPublico(Long id, String descripcion) {}

    public record TextoConsentimientoPublico(String tipo, String version, String texto) {}

    // El texto legal de LA EMPRESA de una vacante, para la casilla del formulario de
    // postular. Lleva el nombre porque es lo que la ley pide enseñar: quién va a tratar
    // los datos. El de la plataforma (crear la cuenta) sale por textosDeConsentimiento.
    public record ConsentimientoDeVacante(String nombreEmpresa, String version, String texto) {}

    public record Sesion(String token, Long usuarioId) {}

    /**
     * Lo unico que se manda para entrar con el enlace del correo: el token.
     *
     * <p>Va en el cuerpo y no en la URL a proposito. Un token en la query string acaba
     * escrito en el registro del servidor, en el historial del navegador y en la cabecera
     * Referer de cualquier recurso externo que cargue la pagina siguiente.
     */
    public record AccesoPorEnlace(@NotBlank String token) {}

    // empresa por lo mismo que nombreEmpresa en el tablón: «mis postulaciones» mezcla los
    // procesos del candidato en todas las empresas, y cada uno debe decir de quién es.
    /**
     * Una postulación suya, como la ve el candidato.
     *
     * <p>{@code instrumentoEtapaTecnica} dice qué rendirá cuando le toque la etapa de la
     * prueba: {@code PLANTILLA} (la prueba del puesto, con su enunciado y sus entregables) o
     * {@code CUESTIONARIO_TECNICO} (preguntas escritas para esa vacante). Los dos comparten
     * los mismos estados, así que sin este dato el portal no sabría a qué pantalla llevarlo
     * y tendría que adivinarlo pidiendo un examen y mirando si responde 404.
     */
    public record MiPostulacion(String uuid, String vacante, String empresa, String estado,
                                String estadoNombre, String grupoPrioridad, long diasSinCambio,
                                Instant creadoEn, String instrumentoEtapaTecnica) {}

    public record PasoHistorial(String estadoAnterior, String estadoNuevo, boolean fueElSistema,
                                Instant ocurridaEn) {}

    public record MiPostulacionDetalle(MiPostulacion resumen, List<PasoHistorial> historial) {}
}
