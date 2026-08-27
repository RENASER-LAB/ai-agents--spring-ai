package com.renaser.ai.ai_engine.seguridad.service;

import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.Login;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.Sesion;

/**
 * La entrada del equipo al panel.
 *
 * <p>Con RENASER OS dormido, todo el panel —Renaser incluida— entra con correo y
 * contraseña por {@link #entrar}. El login de desarrollo queda para local y para las
 * pruebas, apagado por defecto ({@code app.seguridad.dev-login-activo=false}).
 */
public interface ServicioAccesoEquipo {

    /**
     * El login del panel: correo y contraseña, solo cuentas con {@code es_equipo}.
     *
     * <p>Ese filtro es lo único que separa los dos mundos: un candidato también tiene
     * correo y contraseña, y sin él este login le emitiría un token de EQUIPO. El mensaje
     * de error es el mismo exista o no la cuenta, sea o no de equipo.
     */
    Sesion entrar(Login datos);

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
