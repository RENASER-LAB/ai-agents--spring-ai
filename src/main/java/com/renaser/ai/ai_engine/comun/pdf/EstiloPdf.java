package com.renaser.ai.ai_engine.comun.pdf;

import java.awt.Color;

/**
 * Cómo se escribe un trozo de texto en un PDF: familia, tamaño en puntos, color, si va en peso
 * medio, el espaciado entre letras (en fracción del tamaño, como el {@code letter-spacing} en em
 * de la maqueta) y si va en mayúsculas.
 *
 * <p>Es inmutable: los «con…» devuelven otro estilo, así que se pueden declarar una vez como
 * constantes y derivar los que cambian en un detalle.
 */
public record EstiloPdf(Familia familia, float tamano, Color color, boolean media,
                        float espaciado, boolean mayusculas) {

    /** Onest para el texto y Syne para la voz de interfaz (cabecera y pie). */
    public enum Familia { TEXTO, VOZ }

    public static EstiloPdf texto(float tamano, Color color) {
        return new EstiloPdf(Familia.TEXTO, tamano, color, false, 0f, false);
    }

    public static EstiloPdf voz(float tamano, Color color) {
        return new EstiloPdf(Familia.VOZ, tamano, color, false, 0.06f, true);
    }

    public EstiloPdf conColor(Color otro) {
        return new EstiloPdf(familia, tamano, otro, media, espaciado, mayusculas);
    }

    public EstiloPdf conTamano(float otro) {
        return new EstiloPdf(familia, otro, color, media, espaciado, mayusculas);
    }

    public EstiloPdf medio() {
        return new EstiloPdf(familia, tamano, color, true, espaciado, mayusculas);
    }

    public EstiloPdf normal() {
        return new EstiloPdf(familia, tamano, color, false, espaciado, mayusculas);
    }

    public EstiloPdf espaciado(float em) {
        return new EstiloPdf(familia, tamano, color, media, em, mayusculas);
    }

    public EstiloPdf enMayusculas() {
        return new EstiloPdf(familia, tamano, color, media, espaciado, true);
    }

    /** El alto de una línea de este estilo, con el interlineado que se le pida. */
    public float interlineado(float factor) {
        return tamano * factor;
    }
}
