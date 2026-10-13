package com.renaser.ai.ai_engine.whatsapp.service;

/**
 * El botón de WhatsApp no se puede usar en esta postulación ahora (V73): 409 con un
 * {@code codigo} que el portal sabe leer.
 *
 * <p>{@link #SIN_CELULAR} es el que importa: el portal pide entonces el celular en la misma
 * tarjeta y vuelve a intentarlo.
 */
public class WhatsappNoDisponibleException extends RuntimeException {

    /** La cuenta no tiene celular: hay que pedírselo antes. */
    public static final String SIN_CELULAR = "SIN_CELULAR";
    /** La vacante no es de RENASER, la beta está apagada o no hay bot. */
    public static final String NO_DISPONIBLE = "NO_DISPONIBLE";
    /** El proceso ya terminó: no hay novedades que contar. */
    public static final String PROCESO_TERMINADO = "PROCESO_TERMINADO";
    /** El celular de la cuenta escribió BAJA al bot. */
    public static final String SUPRIMIDO = "SUPRIMIDO";

    private final String codigo;

    public WhatsappNoDisponibleException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
