package com.renaser.ai.ai_engine.whatsapp.service;

/**
 * El bot falló o no contestó al encender WhatsApp con un clic (V92): 503 con un {@code codigo}
 * que el portal lee para decir «No pudimos activarlo. Vuelve a intentarlo en un momento.».
 *
 * <p>Se lanza dentro de la transacción de la activación, así que no queda nada a medias: ni el
 * número sale de la BAJA, ni se crean vínculos, ni se enciende el interruptor.
 */
public class WhatsappNoActivadoException extends RuntimeException {

    /** El código que viaja en el 503. */
    public static final String CODIGO = "NO_SE_PUDO_ACTIVAR";

    public WhatsappNoActivadoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }

    public String getCodigo() {
        return CODIGO;
    }
}
