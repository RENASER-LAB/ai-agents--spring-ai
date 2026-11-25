package com.renaser.ai.ai_engine.notificacion.service;

import com.renaser.ai.ai_engine.notificacion.entity.PreferenciaAviso;
import com.renaser.ai.ai_engine.notificacion.repository.PreferenciaAvisoRepository;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Las preferencias de aviso de cada persona (V92), en un solo sitio: lo que leen el correo y la
 * campana antes de salir, y lo que escriben los ajustes de avisos del portal, el bot y «Mi cuenta».
 *
 * <p>Sin fila vale todo por defecto ({@link PreferenciaAviso#porDefecto}); la fila nace la
 * primera vez que algo cambia. Una persona del equipo nunca tiene fila, así que sus correos —una
 * invitación, el tope de la IA— salen siempre.
 */
@Component
@RequiredArgsConstructor
public class PreferenciasDeAviso {

    private final PreferenciaAvisoRepository preferencias;
    private final UsuarioRepository usuarios;

    /** Las suyas, o las de por defecto si nunca cambió nada. No escribe. */
    public PreferenciaAviso de(Long personaId) {
        if (personaId == null) {
            return PreferenciaAviso.porDefecto(null);
        }
        return preferencias.findById(personaId).orElseGet(() -> PreferenciaAviso.porDefecto(personaId));
    }

    /** Si los correos de sus procesos le salen a este usuario. Sin usuario o sin persona, sí. */
    public boolean recibeCorreo(Long usuarioId) {
        return deUsuario(usuarioId).isCorreo();
    }

    /** Si los avisos de sus procesos le llegan a la campana de este usuario. */
    public boolean recibeEnPortal(Long usuarioId) {
        return deUsuario(usuarioId).isPortal();
    }

    /**
     * La fila, creada si no existía y bloqueada hasta el final de la transacción de quien llama:
     * activar WhatsApp la sujeta mientras habla con el bot.
     */
    @Transactional
    public PreferenciaAviso bloquear(Long personaId) {
        preferencias.asegurar(personaId);
        return preferencias.paraEscribir(personaId)
                .orElseThrow(() -> new IllegalStateException("No hay preferencias para la persona " + personaId));
    }

    /** Encender o apagar el correo y la campana. Lo que no viene se queda como estaba. */
    @Transactional
    public PreferenciaAviso cambiar(Long personaId, Boolean correo, Boolean portal) {
        PreferenciaAviso fila = bloquear(personaId);
        if (correo != null) {
            fila.setCorreo(correo);
        }
        if (portal != null) {
            fila.setPortal(portal);
        }
        return guardar(fila);
    }

    /** El interruptor de WhatsApp de la cuenta. */
    @Transactional
    public void whatsapp(Long personaId, boolean activo) {
        PreferenciaAviso fila = bloquear(personaId);
        if (fila.isWhatsappActivo() != activo) {
            fila.setWhatsappActivo(activo);
            guardar(fila);
        }
    }

    /**
     * Escribió BAJA al bot: WhatsApp queda apagado y el mensaje flotante vuelve a poder salir dos
     * veces, ya con «Vuelve a recibir tus avisos por WhatsApp».
     */
    @Transactional
    public void alDarseDeBaja(Long personaId) {
        PreferenciaAviso fila = bloquear(personaId);
        fila.setWhatsappActivo(false);
        fila.setMensajeVistas((short) 0);
        guardar(fila);
    }

    /** El mensaje flotante se pintó: una vez más, y ahora. */
    @Transactional
    public void mensajeVisto(Long personaId) {
        PreferenciaAviso fila = bloquear(personaId);
        fila.setMensajeVistas((short) Math.min(Short.MAX_VALUE, fila.getMensajeVistas() + 1));
        fila.setMensajeVistoEn(Instant.now());
        guardar(fila);
    }

    /**
     * Dio por vista la guía de los avisos (V93). Idempotente: la segunda vez no cambia nada y se
     * queda la fecha de la primera.
     */
    @Transactional
    public void guiaVista(Long personaId) {
        PreferenciaAviso fila = bloquear(personaId);
        if (fila.getGuiaAvisosVistaEn() == null) {
            fila.setGuiaAvisosVistaEn(Instant.now());
            guardar(fila);
        }
    }

    private PreferenciaAviso guardar(PreferenciaAviso fila) {
        fila.setActualizadoEn(Instant.now());
        return preferencias.save(fila);
    }

    private PreferenciaAviso deUsuario(Long usuarioId) {
        if (usuarioId == null) {
            return PreferenciaAviso.porDefecto(null);
        }
        return de(usuarios.findById(usuarioId).map(Usuario::getPersonaId).orElse(null));
    }
}
