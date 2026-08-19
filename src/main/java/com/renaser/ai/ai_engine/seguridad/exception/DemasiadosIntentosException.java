package com.renaser.ai.ai_engine.seguridad.exception;

import lombok.Getter;

/**
 * La entrada está bloqueada temporalmente por acumular intentos fallidos.
 *
 * <p>Existe para que este caso salga como <b>429</b> y no como un 409. Un 409 dice «el estado
 * actual no lo permite», y lo que pasa aquí no es un conflicto de estado sino un freno por
 * ritmo: lo mismo que se pedía hace un momento se podrá pedir dentro de unos minutos.
 *
 * <p>Lleva los segundos que faltan porque la respuesta los devuelve en la cabecera
 * {@code Retry-After}. Sin ese dato, el frontend solo puede adivinar cuándo reintentar.
 */
@Getter
public class DemasiadosIntentosException extends RuntimeException {

    private final long segundosDeEspera;

    public DemasiadosIntentosException(long segundosDeEspera) {
        super("Demasiados intentos fallidos. Espera unos minutos y vuelve a probar");
        this.segundosDeEspera = segundosDeEspera;
    }
}
