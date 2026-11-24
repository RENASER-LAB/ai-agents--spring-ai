package com.renaser.ai.ai_engine.portal.service.impl;

import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones;
import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones.ActivacionWhatsapp;
import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones.CambiarNotificaciones;
import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones.CorreoDeLaCuenta;
import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones.MensajeFlotante;
import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones.Notificaciones;
import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones.PortalDeLaCuenta;
import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones.WhatsappDeLaCuenta;
import com.renaser.ai.ai_engine.notificacion.entity.PreferenciaAviso;
import com.renaser.ai.ai_engine.notificacion.service.PreferenciasDeAviso;
import com.renaser.ai.ai_engine.portal.service.ServicioNotificacionesPortal;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.whatsapp.service.InterruptorWhatsapp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

/**
 * Ver {@link ServicioNotificacionesPortal}. Junta lo que vive en dos sitios: las preferencias de
 * aviso ({@link PreferenciasDeAviso}) y el interruptor de WhatsApp ({@link InterruptorWhatsapp}),
 * que es el que sabe de números, vínculos y bajas.
 */
@Service
@RequiredArgsConstructor
public class ServicioNotificacionesPortalImpl implements ServicioNotificacionesPortal {

    /** En estos estados no tiene sentido invitarle a activar WhatsApp. */
    private static final Set<String> SIN_INVITACION = Set.of(DtosNotificaciones.SIN_NUMERO,
            DtosNotificaciones.ACTIVO, DtosNotificaciones.ESPERANDO);

    private final PreferenciasDeAviso preferencias;
    private final InterruptorWhatsapp whatsapp;
    private final UsuarioRepository usuarios;

    @Override
    public Notificaciones notificaciones(ContextoUsuario quien) {
        PreferenciaAviso preferencia = preferencias.de(quien.personaId());
        InterruptorWhatsapp.Estado estado = whatsapp.estado(quien.personaId(), quien.usuarioId());
        String destino = usuarios.findById(quien.usuarioId()).map(Usuario::getCorreo).orElse(null);
        return new Notificaciones(estado.whatsapp(),
                new CorreoDeLaCuenta(preferencia.isCorreo(), destino),
                new PortalDeLaCuenta(preferencia.isPortal()),
                mensaje(estado.whatsapp(), estado.hayProcesoVivo(), estado.ultimaPostulacion(), preferencia));
    }

    @Override
    public Notificaciones cambiar(ContextoUsuario quien, CambiarNotificaciones datos) {
        if (datos != null && (datos.correo() != null || datos.portal() != null)) {
            preferencias.cambiar(quien.personaId(), datos.correo(), datos.portal());
        }
        return notificaciones(quien);
    }

    @Override
    public void mensajeVisto(ContextoUsuario quien) {
        preferencias.mensajeVisto(quien.personaId());
    }

    @Override
    public ActivacionWhatsapp activarWhatsapp(ContextoUsuario quien, String ip, String userAgent) {
        InterruptorWhatsapp.Activacion hecho = whatsapp.activar(quien, ip, userAgent);
        return new ActivacionWhatsapp(hecho.resultado(), hecho.enlace(), hecho.confirmacionEnviada(),
                notificaciones(quien));
    }

    @Override
    public Notificaciones apagarWhatsapp(ContextoUsuario quien) {
        whatsapp.apagar(quien);
        return notificaciones(quien);
    }

    /**
     * Si el mensaje flotante sale ahora, y con qué texto. Sale cuando se cumple todo:
     * <ul>
     *   <li>WhatsApp está disponible, no está activo ni esperando y la cuenta tiene número;</li>
     *   <li>tiene al menos un proceso vivo;</li>
     *   <li>se ha mostrado menos de dos veces;</li>
     *   <li>postuló después de la última vez que se mostró (la primera vez, vale cualquiera).</li>
     * </ul>
     * El texto: tras una BAJA, «Vuelve a recibir…»; con un número nuevo sin comprobar, «Activa
     * WhatsApp en tu número nuevo»; si no, el de la primera vez.
     */
    static MensajeFlotante mensaje(WhatsappDeLaCuenta whatsapp, boolean hayProcesoVivo,
                                   Instant ultimaPostulacion, PreferenciaAviso preferencia) {
        String situacion;
        if (whatsapp.porBaja()) {
            situacion = DtosNotificaciones.TRAS_BAJA;
        } else if (whatsapp.cambioDeNumero()) {
            situacion = DtosNotificaciones.CAMBIO_DE_NUMERO;
        } else {
            situacion = DtosNotificaciones.PRIMERA_VEZ;
        }
        Instant vistoEn = preferencia.getMensajeVistoEn();
        boolean postuloDespues = vistoEn == null
                || (ultimaPostulacion != null && ultimaPostulacion.isAfter(vistoEn));
        boolean mostrar = whatsapp.disponible()
                && !SIN_INVITACION.contains(whatsapp.estado())
                && hayProcesoVivo
                && preferencia.getMensajeVistas() < PreferenciaAviso.MAXIMO_DE_VISTAS
                && postuloDespues;
        return new MensajeFlotante(mostrar, situacion);
    }
}
