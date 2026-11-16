package com.renaser.ai.ai_engine.comun.pdf;

import java.util.List;

/**
 * Coloca piezas una debajo de otra y abre página cuando no caben.
 *
 * <p><b>Una pieza no se parte entre dos hojas</b> si cabe entera en una: un punto, una pregunta o
 * un criterio con su texto empiezan y terminan en la misma página. Solo una pieza más alta que una
 * página vacía —una respuesta de tres folios— se reparte, renglón a renglón, y entonces sigue en
 * la página siguiente sin salirse del margen y sin perder una línea.
 *
 * <p>Cada página puede tener su propia geometría (la primera de un informe lleva la columna
 * oscura y las demás una franja estrecha), y la pieza se mide con el ancho de la página donde cae.
 */
public final class FlujoPdf {

    /** Dónde se escribe en cada página, y qué se dibuja detrás al abrirla. */
    public interface Geometria {
        float x(int pagina);

        float ancho(int pagina);

        float arriba(int pagina);

        float abajo(int pagina);

        void decorar(LienzoPdf lienzo, int pagina);
    }

    /** Un trozo que no se parte: un renglón de texto, una barra, una cabecera. */
    public interface Renglon {
        float alto();

        void dibujar(LienzoPdf lienzo, float x, float y, float ancho);
    }

    /** Lo que se mantiene junto: se mide con el ancho de la página y devuelve sus renglones. */
    @FunctionalInterface
    public interface Pieza {
        List<Renglon> renglones(TipografiaPdf tipografia, float ancho);
    }

    private final LienzoPdf lienzo;
    private final Geometria geometria;
    private int pagina;
    private float y;
    private float hueco;

    public FlujoPdf(LienzoPdf lienzo, Geometria geometria) {
        this.lienzo = lienzo;
        this.geometria = geometria;
        abrirPagina();
    }

    public int pagina() {
        return pagina;
    }

    /** Un espacio vertical antes de la próxima pieza; al principio de una página no se pone. */
    public void separar(float puntos) {
        hueco = Math.max(hueco, puntos);
    }

    /** Lo que queda abajo en la página actual, por si alguien quiere saltar antes. */
    public float queda() {
        return geometria.abajo(pagina) - y;
    }

    public void saltarDePagina() {
        abrirPagina();
    }

    /** Lo que mediría una pieza en la página actual, sin colocarla. */
    public float alto(Pieza pieza) {
        return alto(pieza.renglones(lienzo.tipografia(), geometria.ancho(pagina)));
    }

    /** El hueco que se pondría antes de la próxima pieza: ninguno al principio de una página. */
    public float huecoPendiente() {
        return enLoAlto() ? 0f : hueco;
    }

    public void poner(Pieza pieza) {
        List<Renglon> renglones = pieza.renglones(lienzo.tipografia(), geometria.ancho(pagina));
        if (renglones.isEmpty()) {
            return;
        }
        float alto = alto(renglones);
        float conHueco = alto + (enLoAlto() ? 0f : hueco);
        if (conHueco <= queda()) {
            colocar(renglones);
            return;
        }
        int siguiente = pagina + 1;
        float paginaEntera = geometria.abajo(siguiente) - geometria.arriba(siguiente);
        if (!enLoAlto()) {
            abrirPagina();
            renglones = pieza.renglones(lienzo.tipografia(), geometria.ancho(pagina));
            alto = alto(renglones);
        }
        if (alto <= paginaEntera || enLoAlto() && alto <= queda()) {
            colocar(renglones);
            return;
        }
        // Más alta que una página entera: se reparte renglón a renglón.
        hueco = 0f;
        for (Renglon renglon : renglones) {
            if (renglon.alto() > queda() && !enLoAlto()) {
                abrirPagina();
            }
            renglon.dibujar(lienzo, geometria.x(pagina), y, geometria.ancho(pagina));
            y += renglon.alto();
        }
    }

    private void colocar(List<Renglon> renglones) {
        if (!enLoAlto()) {
            y += hueco;
        }
        hueco = 0f;
        for (Renglon renglon : renglones) {
            renglon.dibujar(lienzo, geometria.x(pagina), y, geometria.ancho(pagina));
            y += renglon.alto();
        }
    }

    private boolean enLoAlto() {
        return y <= geometria.arriba(pagina) + 0.01f;
    }

    private void abrirPagina() {
        pagina = lienzo.nuevaPagina();
        geometria.decorar(lienzo, pagina);
        y = geometria.arriba(pagina);
        hueco = 0f;
    }

    private static float alto(List<Renglon> renglones) {
        float total = 0f;
        for (Renglon renglon : renglones) {
            total += renglon.alto();
        }
        return total;
    }
}
