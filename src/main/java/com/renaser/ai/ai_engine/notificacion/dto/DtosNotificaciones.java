package com.renaser.ai.ai_engine.notificacion.dto;

import java.time.LocalDate;

/**
 * Lo que viaja entre el portal y el botón «Notificaciones» de «Mis procesos» (V92). Ver
 * specs/Ready/notificaciones-del-candidato/spec.md, «Estados de WhatsApp que devuelve el backend».
 */
public final class DtosNotificaciones {

    private DtosNotificaciones() {}

    /** Los estados de WhatsApp de la cuenta, en el orden en que se deciden. */
    public static final String SIN_NUMERO = "SIN_NUMERO";
    public static final String ACTIVO = "ACTIVO";
    public static final String ESPERANDO = "ESPERANDO";
    public static final String NO_LLEGO = "NO_LLEGO";
    public static final String APAGADO = "APAGADO";

    /** Las tres situaciones del mensaje flotante, cada una con su título y su línea. */
    public static final String PRIMERA_VEZ = "PRIMERA_VEZ";
    public static final String TRAS_BAJA = "TRAS_BAJA";
    public static final String CAMBIO_DE_NUMERO = "CAMBIO_DE_NUMERO";

    /** Lo que contesta activar WhatsApp. */
    public static final String ACTIVADO = "ACTIVADO";
    public static final String POR_MENSAJE = "POR_MENSAJE";

    /** {@code GET /api/v1/portal/notificaciones}: los tres canales y el mensaje flotante. */
    public record Notificaciones(WhatsappDeLaCuenta whatsapp, CorreoDeLaCuenta correo,
                                 PortalDeLaCuenta portal, MensajeFlotante mensaje) {}

    /**
     * WhatsApp para toda la cuenta.
     *
     * <p>{@code disponible}: la beta está encendida y hay bot; sin eso el portal oculta la fila y
     * el mensaje flotante. {@code estado}: {@value #SIN_NUMERO}, {@value #APAGADO},
     * {@value #ESPERANDO}, {@value #NO_LLEGO} o {@value #ACTIVO}. {@code numero}: el de la cuenta
     * para mostrar («+51 987 654 321»), enmascarado en {@value #ACTIVO} y nulo sin número.
     * {@code comprobado}: esta cuenta ya escribió alguna vez desde ese número, así que se activa
     * con un clic. {@code porBaja} y {@code bajaEn}: el número escribió BAJA y cuándo (día de
     * Lima). {@code cambioDeNumero}: tuvo WhatsApp en otro número y el de ahora está sin comprobar.
     */
    public record WhatsappDeLaCuenta(boolean disponible, String estado, String numero,
                                     boolean comprobado, boolean porBaja, LocalDate bajaEn,
                                     boolean cambioDeNumero) {}

    /** El correo de sus procesos, y a dónde le llega. */
    public record CorreoDeLaCuenta(boolean activo, String destino) {}

    /** La campana de arriba, para sus procesos. */
    public record PortalDeLaCuenta(boolean activo) {}

    /**
     * El mensaje flotante que invita a activar WhatsApp: si sale ahora y con cuál de los tres
     * textos ({@value #PRIMERA_VEZ}, {@value #TRAS_BAJA} o {@value #CAMBIO_DE_NUMERO}).
     */
    public record MensajeFlotante(boolean mostrar, String situacion) {}

    /** {@code PATCH /api/v1/portal/notificaciones}: lo que no viene se queda como estaba. */
    public record CambiarNotificaciones(Boolean correo, Boolean portal) {}

    /**
     * {@code POST /api/v1/portal/notificaciones/whatsapp}.
     *
     * <p>{@code resultado}: {@value #ACTIVADO} (número comprobado, con un clic: ya recibe sus
     * avisos) o {@value #POR_MENSAJE} (número sin comprobar: tiene que escribir desde él, y
     * {@code enlace} es el chat del bot con el mensaje ya escrito). {@code confirmacionEnviada}:
     * el bot le manda un mensaje para confirmarlo.
     */
    public record ActivacionWhatsapp(String resultado, String enlace, boolean confirmacionEnviada,
                                     Notificaciones notificaciones) {}
}
