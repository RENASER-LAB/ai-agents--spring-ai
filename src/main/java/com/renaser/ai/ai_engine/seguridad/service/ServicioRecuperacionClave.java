package com.renaser.ai.ai_engine.seguridad.service;

/**
 * «Me olvidé mi contraseña», para las dos puertas: el portal del candidato y el panel del
 * equipo.
 *
 * <p>Dos pasos. Pedir el enlace con el correo, que llega por correo y vale una vez y poco
 * tiempo; y con ese enlace, elegir la contraseña nueva. Ninguno de los dos inicia sesión:
 * quien cambia la contraseña vuelve a la pantalla de entrada y entra con ella.
 *
 * <p>Los dos pasos son públicos a la fuerza —quien los usa no puede entrar—, y por eso no
 * dicen nada que no deban: pedir el enlace contesta siempre lo mismo, exista o no la cuenta,
 * y un enlace que no sirve contesta lo mismo sea cual sea el motivo.
 */
public interface ServicioRecuperacionClave {

    /**
     * De qué puerta se pide. Un correo de candidato pedido en el panel, o uno de equipo en el
     * portal, no recibe nada: cada enlace lleva a la pantalla de su puerta y a ninguna otra.
     */
    enum Publico { CANDIDATO, EQUIPO }

    /**
     * Pide el enlace. Vuelve enseguida y siempre igual, sin excepción que dependa de la
     * cuenta: el trabajo —comprobar la cuenta y su tope, crear el enlace, mandar el correo,
     * auditar— se hace después de responder (ver {@link ColaDeRecuperaciones}).
     *
     * @param ip la dirección de quien pide, para el tope por IP
     */
    void solicitar(Publico publico, String correo, String ip);

    /**
     * Cambia la contraseña con el token del enlace.
     *
     * @throws com.renaser.ai.ai_engine.seguridad.exception.CredencialesInvalidasException
     *         con el mismo texto si el token no existe, venció, se usó, fue reemplazado por
     *         uno más nuevo, o es de la otra puerta
     * @throws IllegalArgumentException si la contraseña nueva es la misma que la actual; el
     *         enlace sigue sirviendo
     */
    void restablecer(Publico publico, String token, String contrasenaNueva);
}
