package com.renaser.ai.ai_engine.portal.service;

/**
 * Ese celular ya es de otra cuenta (V92): 409 con {@code codigo} {@value #CODIGO}, que el portal
 * pinta junto al campo. Un número pertenece a una sola cuenta desde que WhatsApp es por cuenta.
 */
public class TelefonoEnUsoException extends RuntimeException {

    public static final String CODIGO = "TELEFONO_EN_USO";

    /** Lo que se lee junto al campo, en el registro, en «Mi cuenta» y en «Notificaciones». */
    public static final String MENSAJE = "Ese número ya está en otra cuenta. Usa otro o entra con esa cuenta.";

    public TelefonoEnUsoException() {
        super(MENSAJE);
    }

    public String getCodigo() {
        return CODIGO;
    }
}
