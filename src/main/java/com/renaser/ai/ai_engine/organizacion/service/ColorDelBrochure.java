package com.renaser.ai.ai_engine.organizacion.service;

import java.awt.Color;
import java.util.Optional;

/**
 * Los seis colores del brochure en PDF (V86): un catálogo fijo en el código y sin tabla, como las
 * plantillas. No hay color libre: un selector abierto permitiría colores ilegibles.
 *
 * <p><b>Todos llevan texto blanco encima con un contraste de al menos 4,5:1</b> (lo comprueba
 * {@code ColorDelBrochureTest}). El color tiñe los títulos, los filetes, la columna o el velo según
 * la plantilla; el resto es la paleta Northstar de {@code ColoresPdf}.
 *
 * <p>Cada color trae sus tres acompañantes, sacados de la maqueta: el {@code oscuro} (el texto
 * sobre el halo y la banda de la portada cuando el fondo ya es el color), el {@code halo} (el
 * fondo claro del propósito y de las tarjetas) y el {@code enlace} (los enlaces sobre la columna
 * del color). El panel pinta los mismos seis círculos con los mismos códigos de color
 * ({@code src/panel/empresa/brochure.ts}).
 */
public enum ColorDelBrochure {
    AZUL("Azul", 0x1E3A8A, 0x172A66, 0xE9EDFF, 0xC9D6F8),
    VERDE("Verde", 0x0B7A63, 0x07523F, 0xE6F5F0, 0xC4EBDF),
    ROJO("Rojo", 0xC22A3F, 0x8A1D2C, 0xFBEFF1, 0xFAD4DA),
    NOCHE("Noche", 0x111312, 0x2B2F2D, 0xF1F2F1, 0xC9D6F8),
    PETROLEO("Petróleo", 0x155E75, 0x0D3D4C, 0xE3F1F5, 0xCFE8EF),
    CIRUELA("Ciruela", 0x6B2D5C, 0x4F1F43, 0xF4EAF1, 0xEBD3E4);

    public static final ColorDelBrochure POR_DEFECTO = AZUL;

    private final String nombre;
    private final Color color;
    private final Color oscuro;
    private final Color halo;
    private final Color enlace;

    ColorDelBrochure(String nombre, int color, int oscuro, int halo, int enlace) {
        this.nombre = nombre;
        this.color = new Color(color);
        this.oscuro = new Color(oscuro);
        this.halo = new Color(halo);
        this.enlace = new Color(enlace);
    }

    public String nombre() {
        return nombre;
    }

    public Color color() {
        return color;
    }

    public Color oscuro() {
        return oscuro;
    }

    public Color halo() {
        return halo;
    }

    public Color enlace() {
        return enlace;
    }

    /**
     * El blanco a medias sobre el color: el texto secundario de la columna y de la portada, como
     * el {@code rgb(255 255 255 / 0.7)} de la maqueta, pero ya mezclado (el PDF no lo necesita
     * transparente).
     */
    public Color blancoAl(float opacidad) {
        float o = Math.max(0f, Math.min(1f, opacidad));
        return new Color(
                Math.round(255 * o + color.getRed() * (1 - o)),
                Math.round(255 * o + color.getGreen() * (1 - o)),
                Math.round(255 * o + color.getBlue() * (1 - o)));
    }

    public static Optional<ColorDelBrochure> deCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        for (ColorDelBrochure tono : values()) {
            if (tono.name().equals(codigo.strip())) {
                return Optional.of(tono);
            }
        }
        return Optional.empty();
    }

    /** El guardado, o el de por defecto si lo guardado ya no se reconoce. */
    public static ColorDelBrochure deLoGuardado(String codigo) {
        return deCodigo(codigo).orElse(POR_DEFECTO);
    }

    /** El contraste WCAG entre dos colores, de 1 a 21. */
    public static double contraste(Color a, Color b) {
        double la = luminancia(a);
        double lb = luminancia(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminancia(Color c) {
        return 0.2126 * lineal(c.getRed()) + 0.7152 * lineal(c.getGreen())
                + 0.0722 * lineal(c.getBlue());
    }

    private static double lineal(int canal) {
        double v = canal / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
}
