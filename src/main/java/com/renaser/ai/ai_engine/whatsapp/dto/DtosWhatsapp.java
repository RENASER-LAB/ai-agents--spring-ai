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
    //
    // Los límites son los del contrato (specs/novedades-por-whatsapp.md y la spec 007 del CRM):
    // una respuesta que los pasa, el bot la trata como un error de protocolo. Ver
    // TextosDeWhatsapp, que corta cada campo.

    /**
     * El primer mensaje (o cualquiera) que llegó al bot desde un número anunciado. El texto da
     * igual —solo cuenta el número— y puede venir vacío (un Flow o un adjunto); se recibe para la
     * auditoría del bot.
     */
    public record PeticionVinculo(@NotBlank String telefono, String wamid, String texto,
                                  Instant recibidoEn) {}

    /** {@code POST /novedades} y {@code POST /bajas}: solo el número, en el cuerpo y no en la URL. */
    public record PeticionDelNumero(@NotBlank String telefono) {}

    public record PostulacionVinculada(String postulacionRef) {}

    /**
     * Lo que se le cuenta de un proceso, con las mismas palabras del portal: {@code vacante} (hasta
     * 60 caracteres), {@code titulo} del momento (hasta 80) y {@code texto} (hasta 300).
     */
    public record Novedad(String vacante, String titulo, String texto) {}

    /**
     * 200 de {@code /vinculos}: todas las postulaciones activas vinculadas a ese número tras la
     * llamada (también las que ya lo estaban), el nombre de pila (hasta 60) y las novedades de
     * esos procesos (como mucho 8).
     */
    public record Vinculo(List<PostulacionVinculada> postulaciones, String nombre,
                          List<Novedad> novedades) {}

    /**
     * Un error de EX hacia el bot, con el texto que el bot contesta tal cual (hasta 1000
     * caracteres): {@code SIN_ANUNCIO} (404), {@code PROCESO_TERMINADO} o {@code BETA_APAGADA}
     * (409) en {@code /vinculos}; {@code VALIDACION} (400) y {@code FIRMA_INVALIDA} (401) en
     * todas.
     */
    public record Rechazo(String codigo, String texto) {}

    /** 200 de {@code /novedades}: las de cada proceso activo vinculado al número; puede ir vacía. */
    public record Novedades(List<Novedad> novedades) {}
}
