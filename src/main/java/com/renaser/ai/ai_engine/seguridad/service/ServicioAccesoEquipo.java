package com.renaser.ai.ai_engine.seguridad.service;

import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.Sesion;

/**
 * La entrada del equipo al panel <b>mientras no exista el contrato con RENASER OS</b>.
 *
 * <p>En producción se apaga con {@code app.seguridad.dev-login-activo=false} y la identidad
 * la emite RENASER OS. Aquí solo se valida su token; nunca se guarda su contraseña.
 */
public interface ServicioAccesoEquipo {

    /**
     * Emite un token de equipo a partir de un id de RENASER OS.
     *
     * <p>El primer id que entra en una base recién migrada se crea solo, con los roles
     * operativos completos: sin alguien del equipo no se pueden crear usuarios, así que
     * habría que entrar a la base a mano para arrancar. Solo pasa una vez y solo en
     * desarrollo.
     *
     * @throws IllegalStateException si el login de desarrollo está apagado
     */
    Sesion devLogin(String usuarioRenaserOsId);
}
