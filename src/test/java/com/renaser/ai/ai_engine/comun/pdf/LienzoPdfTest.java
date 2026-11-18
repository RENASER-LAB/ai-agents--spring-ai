package com.renaser.ai.ai_engine.comun.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las primitivas que el brochure (V86) enseñó al lienzo: dibujar una imagen recortada al centro
 * sobre blanco, y un rectángulo translúcido. Se comprueban pintando la hoja y mirando sus píxeles.
 */
@DisplayName("El lienzo del PDF: imágenes y transparencia")
class LienzoPdfTest {

    @Test
    @DisplayName("un PNG transparente se pinta sobre blanco, nunca sobre negro")
    void pngTransparenteSobreBlanco() throws IOException {
        byte[] transparente = png(new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB));

        BufferedImage hoja = pintar(l -> {
            l.rectangulo(0, 0, LienzoPdf.ANCHO, LienzoPdf.ALTO, Color.BLACK);
            l.imagen(l.leerImagen(transparente), 100, 100, 100, 100, 0f);
        });

        assertThat(color(hoja, 150, 150)).isEqualTo(Color.WHITE);
        // Fuera del recuadro sigue el negro de detrás.
        assertThat(color(hoja, 50, 50)).isEqualTo(Color.BLACK);
    }

    @Test
    @DisplayName("la imagen llena el recuadro recortada al centro, como una portada")
    void recortadaAlCentro() throws IOException {
        BufferedImage tres = new BufferedImage(300, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = tres.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 100);
        g.setColor(Color.GREEN);
        g.fillRect(100, 0, 100, 100);
        g.setColor(Color.BLUE);
        g.fillRect(200, 0, 100, 100);
        g.dispose();
        byte[] bytes = png(tres);

        BufferedImage hoja = pintar(l -> l.imagen(l.leerImagen(bytes), 100, 100, 100, 100, 0f));

        // En un cuadrado solo cabe el tercio del centro: verde de lado a lado, sin rojo ni azul.
        assertThat(cerca(color(hoja, 104, 150), Color.GREEN)).isTrue();
        assertThat(cerca(color(hoja, 196, 150), Color.GREEN)).isTrue();
        assertThat(color(hoja, 90, 150)).isEqualTo(Color.WHITE);
        assertThat(color(hoja, 210, 150)).isEqualTo(Color.WHITE);
    }

    @Test
    @DisplayName("un JPG grande (una foto) también se dibuja")
    void fotoEnJpg() throws IOException {
        BufferedImage foto = new BufferedImage(1600, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = foto.createGraphics();
        g.setColor(new Color(0x2E5E3E));
        g.fillRect(0, 0, 1600, 400);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(foto, "jpeg", salida);

        BufferedImage hoja = pintar(l -> {
            LienzoPdf.Imagen leida = l.leerImagen(salida.toByteArray());
            assertThat(leida).isNotNull();
            assertThat(leida.ancho()).isEqualTo(1600);
            l.imagen(leida, 0, 0, LienzoPdf.ANCHO, 200, 0f);
        });

        assertThat(cerca(color(hoja, 300, 100), new Color(0x2E5E3E))).isTrue();
    }

    @Test
    @DisplayName("lo que no es una imagen, o es demasiado grande, no se lee: devuelve nulo y no rompe nada")
    void loQueNoSeLee() throws IOException {
        try (LienzoPdf lienzo = new LienzoPdf()) {
            lienzo.nuevaPagina();
            assertThat(lienzo.leerImagen(null)).isNull();
            assertThat(lienzo.leerImagen(new byte[0])).isNull();
            assertThat(lienzo.leerImagen("<svg></svg>".getBytes())).isNull();
            assertThat(lienzo.leerImagen(png(new BufferedImage(LienzoPdf.LADO_MAXIMO_IMAGEN + 1, 2,
                    BufferedImage.TYPE_INT_RGB)))).isNull();
            // Dibujar «nada» no hace nada.
            lienzo.imagen(null, 0, 0, 10, 10, 0f);
            assertThat(lienzo.guardar()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("un rectángulo al 50 % deja ver lo de detrás a medias")
    void rectanguloTranslucido() throws IOException {
        BufferedImage hoja = pintar(l -> l.rectangulo(100, 100, 100, 100, Color.BLACK, 0.5f));

        Color medio = color(hoja, 150, 150);
        assertThat(medio.getRed()).isBetween(120, 135);
        assertThat(color(hoja, 50, 50)).isEqualTo(Color.WHITE);
    }

    // ================================================================== ayudas

    private static BufferedImage pintar(Consumer<LienzoPdf> dibujo) throws IOException {
        byte[] archivo;
        try (LienzoPdf lienzo = new LienzoPdf()) {
            lienzo.nuevaPagina();
            dibujo.accept(lienzo);
            archivo = lienzo.guardar();
        }
        try (PDDocument documento = Loader.loadPDF(archivo)) {
            return new PDFRenderer(documento).renderImage(0, 1f);
        }
    }

    private static Color color(BufferedImage hoja, int x, int y) {
        return new Color(hoja.getRGB(x, y));
    }

    private static boolean cerca(Color a, Color b) {
        return Math.abs(a.getRed() - b.getRed()) < 12 && Math.abs(a.getGreen() - b.getGreen()) < 12
                && Math.abs(a.getBlue() - b.getBlue()) < 12;
    }

    private static byte[] png(BufferedImage imagen) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);
        return salida.toByteArray();
    }
}
