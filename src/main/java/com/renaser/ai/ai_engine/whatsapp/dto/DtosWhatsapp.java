package com.renaser.ai.ai_engine.whatsapp.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

/**
 * Los contratos de las novedades por WhatsApp (V73): lo que ve el portal y lo que cruza con el
 * bot. Ver docs/CONTRATO-NOVEDADES-POR-WHATSAPP.md.
 */
public final class DtosWhatsapp {

    private DtosWhatsapp() {}

    // ---------- el portal ----------

    /**
     * La tarjeta de WhatsApp de una postulación.
     *
     * <p>{@code disponible}: la vacante es de RENASER, la beta está encendida y el proceso no
     * terminó. Sin eso, {@code estado} y {@code numero} van vacíos y la tarjeta no sale.
     *
     * <p>{@code estado}: {@code SIN_CELULAR}, {@code SIN_VINCULAR}, {@code PENDIENTE},
     * {@code ACTIVO}, {@code REVOCADO} o {@code SUPRIMIDO}. {@code numero} va completo en los que
     * enseñan el número de su cuenta («+51 987 654 321») y enmascarado en {@code ACTIVO}
     * («+51 9•• ••• 321»).
     */
    public record WhatsappDelProceso(boolean disponible, String estado, String numero) {

        public static final WhatsappDelProceso NO_DISPONIBLE = new WhatsappDelProceso(false, null, null);
    }

    /** Lo que devuelve el botón: el enlace {@code wa.me} con el mensaje ya escrito, y cómo queda. */
    public record EnlaceWhatsapp(String enlace, WhatsappDelProceso whatsapp) {}

    // ---------- el bot llama a EX ----------

    /**
     * El primer mensaje (o cualquiera) que llegó al bot desde un número anunciado. El texto da
     * igual —solo cuenta el número—, pero se recibe para la auditoría del bot.
     */
    public record PeticionVinculo(@NotBlank String telefono, String wamid, String texto,
                                  Instant recibidoEn) {}

    public record PostulacionVinculada(String postulacionRef) {}

    /** Lo que se le cuenta de un proceso, con las mismas palabras del portal. */
    public record Novedad(String postulacionRef, String vacante, String titulo, String texto) {}

    /** 200 de {@code /vinculos}: lo que quedó vinculado a ese número y sus novedades. */
    public record Vinculo(List<PostulacionVinculada> postulaciones, String nombre,
                          List<Novedad> novedades) {}

    /**
     * Un no de EX al bot, con el texto que el bot tiene que contestar tal cual:
     * {@code SIN_ANUNCIO} (404), {@code PROCESO_TERMINADO} o {@code BETA_APAGADA} (409).
     */
    public record Rechazo(String codigo, String texto) {}

    /** {@code GET /novedades}: las de cada proceso activo vinculado al número; la lista puede ir vacía. */
    public record Novedades(String nombre, List<Novedad> novedades) {}

    public record PeticionBaja(@NotBlank String telefono) {}

    public record Baja(int desvinculadas) {}
}
