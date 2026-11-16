package com.renaser.ai.ai_engine.comun.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import org.apache.pdfbox.util.Matrix;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Dónde se dibuja: el documento, sus páginas A4 y las primitivas que necesita un informe —texto,
 * rectángulos, líneas, círculos, el anillo de la nota, enlaces y texto en vertical—.
 *
 * <p><b>Las coordenadas van desde arriba</b>, como en la maqueta HTML: {@code y} es la distancia
 * al borde superior. PDF las cuenta desde abajo, y la conversión vive solo aquí.
 *
 * <p>Se puede volver a una página ya dibujada ({@link #enPagina}) para escribir lo que no se sabía
 * al dibujarla, como «Página 1 de 3».
 */
public final class LienzoPdf implements AutoCloseable {

    public static final float ANCHO = PDRectangle.A4.getWidth();
    public static final float ALTO = PDRectangle.A4.getHeight();

    private final PDDocument documento;
    private final TipografiaPdf tipografia;
    private final List<PDPage> paginas = new ArrayList<>();
    private PDPage pagina;
    private PDPageContentStream trazo;

    public LienzoPdf() {
        this.documento = new PDDocument();
        this.tipografia = TipografiaPdf.para(documento);
    }

    public TipografiaPdf tipografia() {
        return tipografia;
    }

    public void titulo(String titulo, String autor) {
        documento.getDocumentInformation().setTitle(titulo);
        documento.getDocumentInformation().setAuthor(autor);
        documento.getDocumentInformation().setCreator("EX");
    }

    /** Abre una página nueva y la deja como página actual. Devuelve su número, desde 1. */
    public int nuevaPagina() {
        cerrarTrazo();
        pagina = new PDPage(PDRectangle.A4);
        documento.addPage(pagina);
        paginas.add(pagina);
        abrirTrazo();
        return paginas.size();
    }

    /** Vuelve a una página ya creada, para añadirle algo encima. */
    public void enPagina(int numero) {
        cerrarTrazo();
        pagina = paginas.get(numero - 1);
        abrirTrazo();
    }

    public int paginas() {
        return paginas.size();
    }

    // ------------------------------------------------------------------ texto

    /** Una línea de texto con su borde de arriba en {@code y}. Devuelve el ancho escrito. */
    public float texto(float x, float y, EstiloPdf estilo, String texto) {
        if (texto == null || texto.isEmpty()) {
            return 0f;
        }
        float base = y + tipografia.ascenso(estilo);
        try {
            trazo.beginText();
            trazo.setNonStrokingColor(estilo.color());
            ponerPeso(estilo);
            trazo.setCharacterSpacing(estilo.espaciado() * estilo.tamano());
            trazo.newLineAtOffset(x, ALTO - base);
            for (TipografiaPdf.Tramo tramo : tipografia.tramos(texto, estilo)) {
                trazo.setFont(tramo.fuente(), estilo.tamano());
                trazo.showText(tramo.texto());
            }
            trazo.endText();
            trazo.setCharacterSpacing(0f);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return tipografia.ancho(texto, estilo);
    }

    /** Lo mismo, alineado a la derecha de {@code xDerecha}. */
    public void textoDerecha(float xDerecha, float y, EstiloPdf estilo, String texto) {
        texto(xDerecha - tipografia.ancho(texto, estilo), y, estilo, texto);
    }

    /** Centrado en {@code xCentro}. */
    public void textoCentrado(float xCentro, float y, EstiloPdf estilo, String texto) {
        texto(xCentro - tipografia.ancho(texto, estilo) / 2f, y, estilo, texto);
    }

    /** Texto girado 90° que sube desde {@code (x, yAbajo)}: el nombre en la franja estrecha. */
    public void textoVertical(float x, float yAbajo, EstiloPdf estilo, String texto) {
        try {
            trazo.beginText();
            trazo.setNonStrokingColor(estilo.color());
            ponerPeso(estilo);
            trazo.setCharacterSpacing(estilo.espaciado() * estilo.tamano());
            trazo.setTextMatrix(Matrix.getRotateInstance(Math.PI / 2, x, ALTO - yAbajo));
            for (TipografiaPdf.Tramo tramo : tipografia.tramos(texto, estilo)) {
                trazo.setFont(tramo.fuente(), estilo.tamano());
                trazo.showText(tramo.texto());
            }
            trazo.endText();
            trazo.setCharacterSpacing(0f);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void ponerPeso(EstiloPdf estilo) throws IOException {
        if (estilo.media()) {
            trazo.setRenderingMode(RenderingMode.FILL_STROKE);
            trazo.setStrokingColor(estilo.color());
            trazo.setLineWidth(estilo.tamano() * TipografiaPdf.TRAZO_MEDIO);
        } else {
            trazo.setRenderingMode(RenderingMode.FILL);
        }
    }

    // ------------------------------------------------------------------ formas

    public void rectangulo(float x, float y, float ancho, float alto, Color relleno) {
        try {
            trazo.setNonStrokingColor(relleno);
            trazo.addRect(x, ALTO - y - alto, ancho, alto);
            trazo.fill();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Un rectángulo con las esquinas redondeadas; con radio 0 es uno normal. */
    public void rectanguloRedondeado(float x, float y, float ancho, float alto, float radio,
                                     Color relleno, Color borde, float grosor) {
        float r = Math.min(radio, Math.min(ancho, alto) / 2f);
        try {
            camino(x, ALTO - y - alto, ancho, alto, r);
            if (relleno != null && borde != null) {
                trazo.setNonStrokingColor(relleno);
                trazo.setStrokingColor(borde);
                trazo.setLineWidth(grosor);
                trazo.fillAndStroke();
            } else if (relleno != null) {
                trazo.setNonStrokingColor(relleno);
                trazo.fill();
            } else {
                trazo.setStrokingColor(borde);
                trazo.setLineWidth(grosor);
                trazo.stroke();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Lo mismo con trazo discontinuo: el bocadillo de «Sin respuesta». */
    public void rectanguloPunteado(float x, float y, float ancho, float alto, float radio,
                                   Color borde, float grosor) {
        try {
            trazo.setLineDashPattern(new float[] {2f, 2f}, 0f);
            rectanguloRedondeado(x, y, ancho, alto, radio, null, borde, grosor);
            trazo.setLineDashPattern(new float[] {}, 0f);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void camino(float x, float yPdf, float w, float h, float r) throws IOException {
        if (r <= 0f) {
            trazo.addRect(x, yPdf, w, h);
            return;
        }
        float k = 0.5523f * r;
        trazo.moveTo(x + r, yPdf);
        trazo.lineTo(x + w - r, yPdf);
        trazo.curveTo(x + w - r + k, yPdf, x + w, yPdf + r - k, x + w, yPdf + r);
        trazo.lineTo(x + w, yPdf + h - r);
        trazo.curveTo(x + w, yPdf + h - r + k, x + w - r + k, yPdf + h, x + w - r, yPdf + h);
        trazo.lineTo(x + r, yPdf + h);
        trazo.curveTo(x + r - k, yPdf + h, x, yPdf + h - r + k, x, yPdf + h - r);
        trazo.lineTo(x, yPdf + r);
        trazo.curveTo(x, yPdf + r - k, x + r - k, yPdf, x + r, yPdf);
        trazo.closePath();
    }

    public void linea(float x1, float y1, float x2, float y2, Color color, float grosor) {
        try {
            trazo.setStrokingColor(color);
            trazo.setLineWidth(grosor);
            trazo.setLineCapStyle(1);
            trazo.moveTo(x1, ALTO - y1);
            trazo.lineTo(x2, ALTO - y2);
            trazo.stroke();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Un trazo de varios puntos (x, y alternados), abierto: los iconos de línea. */
    public void polilinea(Color color, float grosor, float... puntos) {
        try {
            trazo.setStrokingColor(color);
            trazo.setLineWidth(grosor);
            trazo.setLineCapStyle(1);
            trazo.setLineJoinStyle(1);
            trazo.moveTo(puntos[0], ALTO - puntos[1]);
            for (int i = 2; i + 1 < puntos.length; i += 2) {
                trazo.lineTo(puntos[i], ALTO - puntos[i + 1]);
            }
            trazo.stroke();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void circulo(float cx, float cy, float radio, Color relleno, Color borde, float grosor) {
        try {
            float k = 0.5523f * radio;
            float y = ALTO - cy;
            trazo.moveTo(cx + radio, y);
            trazo.curveTo(cx + radio, y + k, cx + k, y + radio, cx, y + radio);
            trazo.curveTo(cx - k, y + radio, cx - radio, y + k, cx - radio, y);
            trazo.curveTo(cx - radio, y - k, cx - k, y - radio, cx, y - radio);
            trazo.curveTo(cx + k, y - radio, cx + radio, y - k, cx + radio, y);
            trazo.closePath();
            if (relleno != null && borde != null) {
                trazo.setNonStrokingColor(relleno);
                trazo.setStrokingColor(borde);
                trazo.setLineWidth(grosor);
                trazo.fillAndStroke();
            } else if (relleno != null) {
                trazo.setNonStrokingColor(relleno);
                trazo.fill();
            } else {
                trazo.setStrokingColor(borde);
                trazo.setLineWidth(grosor);
                trazo.stroke();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Un arco de circunferencia en trazo, desde las doce y en el sentido del reloj, que cubre
     * {@code fraccion} de la vuelta: el anillo de la nota.
     */
    public void arco(float cx, float cy, float radio, float fraccion, Color color, float grosor) {
        float f = Math.max(0f, Math.min(1f, fraccion));
        if (f <= 0f) {
            return;
        }
        try {
            trazo.setStrokingColor(color);
            trazo.setLineWidth(grosor);
            trazo.setLineCapStyle(1);
            double inicio = Math.PI / 2;
            double total = -2 * Math.PI * f;
            int tramos = (int) Math.ceil(Math.abs(total) / (Math.PI / 2));
            double paso = total / tramos;
            double a0 = inicio;
            float yc = ALTO - cy;
            trazo.moveTo((float) (cx + radio * Math.cos(a0)), (float) (yc + radio * Math.sin(a0)));
            for (int i = 0; i < tramos; i++) {
                double a1 = a0 + paso;
                double k = 4.0 / 3.0 * Math.tan((a1 - a0) / 4);
                double x0 = Math.cos(a0);
                double y0 = Math.sin(a0);
                double x3 = Math.cos(a1);
                double y3 = Math.sin(a1);
                trazo.curveTo(
                        (float) (cx + radio * (x0 - k * y0)), (float) (yc + radio * (y0 + k * x0)),
                        (float) (cx + radio * (x3 + k * y3)), (float) (yc + radio * (y3 - k * x3)),
                        (float) (cx + radio * x3), (float) (yc + radio * y3));
                a0 = a1;
            }
            trazo.stroke();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Un enlace que se abre al pulsar sobre ese rectángulo. Solo http y https. */
    public void enlace(float x, float y, float ancho, float alto, String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            return;
        }
        PDAnnotationLink enlace = new PDAnnotationLink();
        enlace.setRectangle(new PDRectangle(x, ALTO - y - alto, ancho, alto));
        PDBorderStyleDictionary borde = new PDBorderStyleDictionary();
        borde.setWidth(0);
        enlace.setBorderStyle(borde);
        PDActionURI accion = new PDActionURI();
        accion.setURI(url);
        enlace.setAction(accion);
        try {
            pagina.getAnnotations().add(enlace);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ cierre

    public byte[] guardar() {
        cerrarTrazo();
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el PDF", e);
        }
    }

    @Override
    public void close() {
        cerrarTrazo();
        try {
            documento.close();
        } catch (IOException ignorada) {
            // Ya se guardó o ya no hace falta: cerrar solo libera memoria.
        }
    }

    private void abrirTrazo() {
        try {
            trazo = new PDPageContentStream(documento, pagina, PDPageContentStream.AppendMode.APPEND,
                    true, true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void cerrarTrazo() {
        if (trazo == null) {
            return;
        }
        try {
            trazo.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            trazo = null;
        }
    }
}
