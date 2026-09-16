package com.renaser.ai.ai_engine.prueba.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Los contratos de la prueba tal como la ve el candidato.
 *
 * <p>Igual que con la evaluación del hito 2, aquí manda una regla: <b>lo que el candidato no
 * debe saber no tiene campo en el contrato.</b> No viaja la rúbrica ({@code criterio.puntos},
 * {@code metodoVerificacion}), no viaja {@code pregunta_prueba.revela}, y no viajan las
 * variantes del cambio inesperado que no le tocaron a él.
 *
 * <p>El minuto exacto del cambio tampoco viaja de antemano: {@code cambioTexto} solo aparece
 * en la respuesta una vez que el servidor decide que ya toca mostrarlo (RF-77 — si se supiera
 * de antemano, se aprendería el patrón).
 */
public final class DtosPrueba {

    private DtosPrueba() {}

    public record PreguntaCandidato(Long id, String tipo, String enunciado, String respuestaTexto) {}

    public record EntregableRequeridoCandidato(
            Long id, String nombre, String detalle, String formato,
            boolean esObligatorio, boolean entregado) {}

    public record MiPrueba(
            Long id,
            String estadoIntento,          // PENDIENTE | EN_CURSO | ENTREGADA
            String modalidad,
            Instant iniciadoEn,
            Instant venceEn,
            Integer duracionMinutos,
            String enunciado,
            String materiales,
            String herramientasPermitidas,
            String cambioTexto,            // null hasta que toque mostrarlo
            List<PreguntaCandidato> preguntas,
            List<EntregableRequeridoCandidato> entregables) {}

    /**
     * Lo que el candidato escribe en una pregunta de la prueba.
     *
     * <p>⚠️ <b>Sin {@code @NotBlank}, y a propósito.</b> Lo tuvo, y eso hacía que vaciar el
     * recuadro rebotara con un 400 antes siquiera de entrar al servicio. Para el candidato
     * eso era un error en mitad de una prueba cronometrada por haber borrado lo que él mismo
     * había escrito, y encima el rechazo dejaba el texto viejo guardado: recuadro vacío en la
     * pantalla y respuesta entera en el servidor.
     *
     * <p>Ahora el vacío lo entiende el servicio y significa lo que parece —esta pregunta se
     * queda sin responder—, igual que en la evaluación del Perfil Integral y en el
     * cuestionario técnico. El tope de tamaño sí se queda: eso sí es un error.
     */
    public record Responder(
            @Size(max = 20_000, message = "La respuesta es demasiado larga")
            String texto) {}

    /** Uno de los dos: {@code enlace}, o nada si se sube archivo por multipart. */
    public record SubirEntregableEnlace(@NotBlank String enlace) {}

    public record EntregaResponse(String estado, boolean completa, int faltantes) {}
}
