package com.renaser.ai.ai_engine.seguridad.exception;

/**
 * Quien intenta entrar no ha demostrado ser quien dice.
 *
 * <p>Existe para que este caso salga como <b>401</b> y no como un 400. Un 400 dice «tu petición
 * está mal escrita», y la petición está perfectamente escrita: lo que falla es la identidad.
 * Antes viajaba como {@code IllegalArgumentException} y el manejador la convertía en 400.
 *
 * <p>El mensaje nunca distingue entre «ese correo no existe» y «esa contraseña no es»: decirlo
 * regalaría a un atacante la lista de correos registrados.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException(String mensaje) {
        super(mensaje);
    }
}
