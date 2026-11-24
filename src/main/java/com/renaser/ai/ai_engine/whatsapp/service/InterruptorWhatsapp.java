package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones.WhatsappDeLaCuenta;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.time.Instant;

/**
 * El interruptor de WhatsApp de la cuenta (V92): uno para todos sus procesos, los de hoy y los que
 * vengan, en la ventana «Notificaciones» de «Mis procesos». Sustituye a la tarjeta de cada proceso.
 *
 * <p>Dos caminos al encenderlo, según el número de la cuenta:
 * <ul>
 *   <li><b>comprobado</b> —esta cuenta escribió alguna vez desde él—: con un clic. El bot vincula
 *       sus procesos vivos sin mensaje ({@code /v1/ex/vinculaciones}), levanta su BAJA si la tenía
 *       y le manda la plantilla de confirmación;</li>
 *   <li><b>sin comprobar</b>: se anuncia el número para cada proceso vivo y el portal abre el chat
 *       del bot; queda activo cuando escribe desde él.</li>
 * </ul>
 */
public interface InterruptorWhatsapp {

    /**
     * El estado de WhatsApp de la cuenta y lo que el mensaje flotante necesita saber de sus
     * procesos: si tiene alguno vivo de la beta y cuándo postuló por última vez.
     */
    record Estado(WhatsappDeLaCuenta whatsapp, boolean hayProcesoVivo, Instant ultimaPostulacion) {}

    /**
     * Lo que hizo encenderlo: {@code resultado} es {@code ACTIVADO} o {@code POR_MENSAJE} (y
     * entonces {@code enlace} es el chat del bot); {@code confirmacionEnviada}, si el bot le manda
     * un mensaje para confirmarlo.
     */
    record Activacion(String resultado, String enlace, boolean confirmacionEnviada) {}

    Estado estado(Long personaId, Long usuarioId);

    /**
     * Encenderlo: firma {@code WHATSAPP_CUENTA} (una vez por persona y versión) y sigue el camino
     * que toque. La fila de sus preferencias queda bloqueada mientras dura.
     *
     * <p>409 ({@link WhatsappNoDisponibleException}) sin celular o con la beta apagada; 503
     * ({@link WhatsappNoActivadoException}) si el bot falla o no contesta en el camino de un clic, y
     * entonces no cambia nada: ni la BAJA, ni los vínculos, ni el interruptor.
     */
    Activacion activar(ContextoUsuario quien, String ip, String userAgent);

    /**
     * Apagarlo: cierra todos sus vínculos vivos con {@code DEJAR_DE_RECIBIR}, omite sus avisos
     * pendientes y se lo cuenta al bot. El número sigue comprobado: volver a encenderlo es un clic.
     */
    void apagar(ContextoUsuario quien);

    /**
     * Acaba de postular. Si el interruptor está encendido, la postulación nueva se vincula sola
     * después del commit, sin frenar nada: si el bot falla, queda anunciada para el sondeo de
     * siempre. Recibe la postulación de quien postula tal como quedó (una que se cerró por un
     * requisito ya viene terminada y no se vincula).
     */
    void alPostular(Long personaId, com.renaser.ai.ai_engine.postulacion.entity.Postulacion postulacion);
}
