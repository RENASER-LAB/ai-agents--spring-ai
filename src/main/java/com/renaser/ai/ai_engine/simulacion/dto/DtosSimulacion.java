package com.renaser.ai.ai_engine.simulacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.List;

/**
 * Los contratos de la simulación de trabajo.
 *
 * <p>Como en el resto del sistema, lo que el candidato no debe saber no tiene campo en su
 * contrato: {@link SesionDisponible} no lleva la matriz de información crítica — que es
 * justamente lo que se espera que él descubra o pregunte por su cuenta.
 */
public final class DtosSimulacion {

    private DtosSimulacion() {}

    // ---------- Administración ----------

    public record CrearSesion(
            @NotNull Instant fechaHora,
            @NotNull @Positive Integer duracionMinutos,
            @NotBlank @Pattern(regexp = "GRUPAL|INDIVIDUAL",
                    message = "modalidad debe ser GRUPAL o INDIVIDUAL")
            String modalidad,
            String lugar,
            String enlace,
            @NotNull @Positive Integer cupo,
            String enunciado,
            @NotNull List<Long> vacanteIds) {}

    public record TramoResponse(String codigo, String nombre, Integer minutoInicio, Integer minutoFin) {}

    public record SesionPanel(
            Long id, Instant fechaHora, Integer duracionMinutos, String modalidad,
            String lugar, String enlace, Integer cupo, long inscritos, String estado,
            String enunciado, List<Long> vacanteIds, List<Long> responsableIds,
            List<TramoResponse> tramos) {}

    public record AmpliarCupo(@NotNull @Positive Integer cupo) {}

    public record CancelarSesion(@NotBlank String motivo) {}

    public record AsignarResponsable(@NotNull Long usuarioId) {}

    public record CrearInformacionCritica(
            @NotBlank @Pattern(regexp = "DEBE_PREGUNTAR|OPCIONAL|DEBE_DESCUBRIR",
                    message = "tipo debe ser DEBE_PREGUNTAR, OPCIONAL o DEBE_DESCUBRIR")
            String tipo,
            @NotBlank String texto) {}

    public record InformacionCriticaResponse(Long id, String tipo, String texto, Integer orden) {}

    // ---------- El candidato ----------

    /** Lo que el candidato ve de una sesión que puede elegir. Sin la matriz, a propósito. */
    public record SesionDisponible(
            Long id, Instant fechaHora, Integer duracionMinutos, String modalidad,
            String lugar, String enlace, long plazasLibres) {}

    public record MiSesion(
            Long inscripcionId, Long sesionId, Instant fechaHora, Integer duracionMinutos,
            String modalidad, String lugar, String enlace, String enunciado,
            Boolean asistio, List<TramoResponse> tramos) {}

    // ---------- Durante la sesión ----------

    public record MarcarEvento(
            @NotBlank @Pattern(regexp = "INICIO|PRIMERA_PREGUNTA|INICIO_TRABAJO|PRIMERA_EVIDENCIA"
                    + "|APARECE_CAMBIO|ABRE_CAMBIO|PRIMERA_REACCION_CAMBIO|COMUNICA_RIESGO"
                    + "|ENTREGA|AUTOCRITICA",
                    message = "evento no es uno de los diez observables")
            String evento,
            /** Si viene vacío se usa el momento de la llamada: lo normal al marcar en vivo. */
            Instant ocurridaEn) {}

    public record MarcaResponse(String evento, Instant ocurridaEn) {}

    public record MarcarAsistencia(@NotNull Boolean asistio) {}

    public record DecidirSobreAusente(
            @NotBlank @Pattern(regexp = "OTRA_FECHA|CERRAR",
                    message = "decision debe ser OTRA_FECHA o CERRAR")
            String decision,
            @NotBlank String motivo) {}

    // ---------- La conversación final ----------

    public record RegistrarPregunta(@NotBlank String texto, Long alertaId) {}

    public record ResponderPregunta(
            @NotBlank String respuesta,
            @NotNull Boolean riesgoResuelto,
            String observacion) {}

    public record PreguntaResponse(
            Long id, String texto, Integer orden, String respuesta,
            Boolean riesgoResuelto, String observacion) {}
}
