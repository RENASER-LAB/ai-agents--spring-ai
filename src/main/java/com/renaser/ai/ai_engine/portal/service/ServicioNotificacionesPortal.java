package com.renaser.ai.ai_engine.portal.service;

import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones.ActivacionWhatsapp;
import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones.CambiarNotificaciones;
import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones.Notificaciones;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

/**
 * El botón «Notificaciones» de «Mis procesos» (V92): un solo ajuste por cuenta para WhatsApp,
 * correo y la campana del portal, y el mensaje flotante que invita a activar WhatsApp.
 *
 * <p>Todo es de quien pregunta: el token dice de quién, y no hay forma de pedir las de otro.
 */
public interface ServicioNotificacionesPortal {

    /** Los tres canales, en qué está WhatsApp y si sale el mensaje flotante. */
    Notificaciones notificaciones(ContextoUsuario quien);

    /** Encender o apagar el correo y la campana; lo que no viene se queda como estaba. */
    Notificaciones cambiar(ContextoUsuario quien, CambiarNotificaciones datos);

    /** El mensaje flotante se pintó: cuenta una vez más. Cerrarlo con la × no cuenta aparte. */
    void mensajeVisto(ContextoUsuario quien);

    /** Encender WhatsApp para todos sus procesos. Ver {@code InterruptorWhatsapp#activar}. */
    ActivacionWhatsapp activarWhatsapp(ContextoUsuario quien, String ip, String userAgent);

    /** Apagar WhatsApp: se cierran todos sus vínculos. El número sigue comprobado. */
    Notificaciones apagarWhatsapp(ContextoUsuario quien);
}
