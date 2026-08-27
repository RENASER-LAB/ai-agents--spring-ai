package com.renaser.ai.ai_engine.seguridad.service;

import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.AceptarInvitacion;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.InvitacionCreada;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.InvitacionPanel;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.Sesion;

import java.util.List;

/**
 * Las invitaciones al panel de empresas: la única forma de que nazca una cuenta de equipo.
 *
 * <p>El panel no tiene registro público a propósito. Renaser invita al administrador de
 * cada empresa al darla de alta, y cada administrador invita a su gente con
 * {@code crear}. El invitado pone su nombre y su contraseña en {@code aceptar}, una sola
 * vez: el token se gasta al canjearse, porque una cuenta no se crea dos veces.
 */
public interface ServicioInvitaciones {

    /** Invita a alguien al equipo de la organización de quien invita. */
    InvitacionCreada crear(ContextoUsuario quien, String correo, List<String> roles);

    /**
     * Invita a alguien al equipo de OTRA organización. Es el paso final del alta de una
     * empresa: quien invita es de la plataforma y el invitado será el primer
     * administrador de la empresa nueva. Quien llama ya comprobó que puede.
     */
    InvitacionCreada crearParaOrganizacion(ContextoUsuario quien, Long organizacionId,
                                           String correo, List<String> roles);

    /**
     * Canjea el token: crea Persona y Usuario de equipo con los roles de la invitación y
     * devuelve la sesión abierta. Una invitación inexistente, vencida, revocada o ya
     * canjeada responde siempre el mismo error, para no regalar información.
     */
    Sesion aceptar(AceptarInvitacion datos);

    List<InvitacionPanel> listar(ContextoUsuario quien);

    /** Invalida una invitación que aún no se canjeó, sin borrar la fila. */
    void revocar(ContextoUsuario quien, Long invitacionId);
}
