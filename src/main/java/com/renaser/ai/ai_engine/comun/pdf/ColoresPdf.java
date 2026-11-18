package com.renaser.ai.ai_engine.comun.pdf;

import java.awt.Color;

/**
 * La paleta Northstar en los PDF que arma EX: la comparten el informe por etapa
 * ({@code PdfDelInforme}) y el brochure de la empresa ({@code PdfDelBrochure}), para que los dos
 * hablen el mismo idioma visual que el portal y el panel (los tokens de {@code mundo.css}).
 */
public final class ColoresPdf {

    /** La tinta del texto. */
    public static final Color TINTA = new Color(0x111312);
    /** El único azul de Northstar, el de la acción. */
    public static final Color AZUL = new Color(0x1E3A8A);
    public static final Color AZUL_OSCURO = new Color(0x172A66);
    public static final Color VERDE = new Color(0x0B7A63);
    public static final Color ROJO = new Color(0xC22A3F);
    /** El fondo claro de las cajas. */
    public static final Color BRUMA = new Color(0xF5F7FB);
    public static final Color HALO = new Color(0xE9EDFF);
    /** El gris de las etiquetas y del pie. */
    public static final Color GRIS = new Color(0x687170);
    /** El gris del texto corrido. */
    public static final Color GRIS_MEDIO = new Color(0x4A5251);
    public static final Color TEXTO2 = new Color(0x2B2F2D);

    private ColoresPdf() {
    }
}
