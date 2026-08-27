package com.renaser.ai.ai_engine.ai.exception;

/**
 * La ingesta pedida no se puede hacer: la ruta se sale del directorio permitido, ahí no hay
 * documento, o la ingesta por ruta está apagada en esta instalación.
 *
 * <p>Existe para que estos casos salgan como <b>400 y no como 500</b>. No vale lanzar un
 * {@code IllegalArgumentException}: el manejador que lo convierte en 400,
 * {@code comun.exception.ManejadorErrores}, está acotado por paquete a los controladores de
 * selección, y {@code RagController} no está entre ellos. Desde aquí un
 * {@code IllegalArgumentException} caería en el {@code @ExceptionHandler(Exception.class)} de
 * {@link GlobalControllerAdvice} y saldría 500 — justo el síntoma que se quiere quitar.
 *
 * <p>El mensaje no distingue «no existe» de «está fuera del directorio», a propósito: esa
 * diferencia convierte el endpoint en un detector de qué ficheros hay en el servidor.
 */
public class IngestaNoPermitidaException extends RuntimeException {

    public IngestaNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
