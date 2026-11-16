package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.organizacion.service.ImagenesDeLaEmpresa.Cual;
import com.renaser.ai.ai_engine.organizacion.service.ImagenesDeLaEmpresa.ImagenLimpia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El logo y la portada se vuelven a codificar en el servidor, sin metadatos (V81, AC-26).
 */
@DisplayName("El logo y la portada de una empresa")
class ImagenesDeLaEmpresaTest {

    private static final String GPS = "GPSLatitude=-16.3988;GPSLongitude=-71.5369";

    @Test
    @DisplayName("AC-26: un PNG de 400×400 con datos GPS se guarda sin ellos")
    void pngSinGps() throws IOException {
        byte[] conGps = conTexto(png(400, 400), GPS);
        assertThat(new String(conGps, StandardCharsets.ISO_8859_1)).contains("GPSLatitude");

        ImagenLimpia limpia = ImagenesDeLaEmpresa.recodificar(
                new MockMultipartFile("archivo", "logo.png", "image/png", conGps), Cual.LOGO);

        assertThat(limpia.tipo()).isEqualTo("image/png");
        assertThat(new String(limpia.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("GPS");
        BufferedImage leida = ImageIO.read(new ByteArrayInputStream(limpia.bytes()));
        assertThat(leida.getWidth()).isEqualTo(400);
        assertThat(leida.getHeight()).isEqualTo(400);
    }

    @Test
    @DisplayName("un JPEG con su bloque EXIF sale sin él")
    void jpegSinExif() throws IOException {
        byte[] conExif = conExif(jpeg(1600, 400), GPS);
        assertThat(new String(conExif, StandardCharsets.ISO_8859_1)).contains("Exif");

        ImagenLimpia limpia = ImagenesDeLaEmpresa.recodificar(
                new MockMultipartFile("archivo", "portada.jpg", "image/jpeg", conExif), Cual.PORTADA);

        assertThat(limpia.tipo()).isEqualTo("image/jpeg");
        assertThat(limpia.extension()).isEqualTo("jpg");
        String texto = new String(limpia.bytes(), StandardCharsets.ISO_8859_1);
        assertThat(texto).doesNotContain("Exif").doesNotContain("GPS");
    }

    @Test
    @DisplayName("uno de 100×100 se rechaza con su motivo")
    void pequeno() {
        assertThatThrownBy(() -> ImagenesDeLaEmpresa.recodificar(
                new MockMultipartFile("archivo", "logo.png", "image/png", png(100, 100)), Cual.LOGO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El logo tiene que medir al menos 200 × 200 píxeles.");
        assertThatThrownBy(() -> ImagenesDeLaEmpresa.recodificar(
                new MockMultipartFile("archivo", "p.png", "image/png", png(1199, 300)), Cual.PORTADA))
                .hasMessage("La portada tiene que medir al menos 1200 × 300 píxeles.");
    }

    @Test
    @DisplayName("uno que no es imagen se rechaza aunque diga .png")
    void noEsImagen() {
        assertThatThrownBy(() -> ImagenesDeLaEmpresa.recodificar(new MockMultipartFile("archivo",
                "logo.png", "image/png", "esto no es una imagen".getBytes(StandardCharsets.UTF_8)),
                Cual.LOGO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El logo no es una imagen que podamos leer.");
    }

    @Test
    @DisplayName("uno de 3 MB se rechaza por el peso")
    void pesado() {
        assertThatThrownBy(() -> ImagenesDeLaEmpresa.recodificar(new MockMultipartFile("archivo",
                "portada.jpg", "image/jpeg", new byte[3 * 1024 * 1024]), Cual.PORTADA))
                .hasMessage("La portada no puede pesar más de 2 MB. Prueba a guardarla más pequeña.");
        assertThatThrownBy(() -> ImagenesDeLaEmpresa.recodificar(new MockMultipartFile("archivo",
                "logo.jpg", "image/jpeg", new byte[3 * 1024 * 1024]), Cual.LOGO))
                .hasMessage("El logo no puede pesar más de 2 MB. Prueba a guardarlo más pequeño.");
    }

    @Test
    @DisplayName("solo JPG y PNG: un WebP, un GIF o un SVG no")
    void formatos() {
        for (String[] caso : new String[][] {{"logo.webp", "image/webp"}, {"logo.gif", "image/gif"},
                {"logo.svg", "image/svg+xml"}, {"logo.png", "image/jpeg"}}) {
            assertThatThrownBy(() -> ImagenesDeLaEmpresa.recodificar(
                    new MockMultipartFile("archivo", caso[0], caso[1], png(400, 400)), Cual.LOGO))
                    .hasMessage("El logo tiene que ser JPG o PNG.");
        }
    }

    @Test
    @DisplayName("la dirección pública lleva el código en minúsculas y el id como versión")
    void direccion() {
        assertThat(ImagenesDeLaEmpresa.direccionPublica("MOLINOS-DEL-SUR", Cual.LOGO, 12L))
                .isEqualTo("/api/v1/portal/empresas/molinos-del-sur/imagenes/logo?v=12");
        assertThat(ImagenesDeLaEmpresa.direccionPublica("Mi Empresa", Cual.PORTADA, 3L))
                .isEqualTo("/api/v1/portal/empresas/mi%20empresa/imagenes/portada?v=3");
        assertThat(ImagenesDeLaEmpresa.direccionPublica("ACME", Cual.LOGO, null)).isNull();
    }

    // ---------- imágenes de prueba ----------

    private static BufferedImage dibujo(int ancho, int alto, int tipo) {
        BufferedImage imagen = new BufferedImage(ancho, alto, tipo);
        Graphics2D pincel = imagen.createGraphics();
        pincel.setColor(new Color(30, 58, 138));
        pincel.fillRect(0, 0, ancho, alto);
        pincel.dispose();
        return imagen;
    }

    private static byte[] png(int ancho, int alto) {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(dibujo(ancho, alto, BufferedImage.TYPE_INT_ARGB), "png", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] jpeg(int ancho, int alto) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(dibujo(ancho, alto, BufferedImage.TYPE_INT_RGB), "jpeg", salida);
        return salida.toByteArray();
    }

    /** Mete un bloque tEXt justo después del IHDR, con su CRC: un PNG válido con metadatos. */
    private static byte[] conTexto(byte[] png, String texto) {
        byte[] datos = ("Comment\0" + texto).getBytes(StandardCharsets.ISO_8859_1);
        byte[] tipo = "tEXt".getBytes(StandardCharsets.ISO_8859_1);
        CRC32 crc = new CRC32();
        crc.update(tipo);
        crc.update(datos);
        ByteBuffer bloque = ByteBuffer.allocate(12 + datos.length);
        bloque.putInt(datos.length).put(tipo).put(datos).putInt((int) crc.getValue());
        // Firma (8) + IHDR (4 de largo + 4 de tipo + 13 de datos + 4 de CRC) = 33.
        int corte = 33;
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.write(png, 0, corte);
        salida.write(bloque.array(), 0, bloque.capacity());
        salida.write(png, corte, png.length - corte);
        return salida.toByteArray();
    }

    /** Mete un bloque APP1 «Exif» justo después del SOI: un JPEG con sus metadatos. */
    private static byte[] conExif(byte[] jpeg, String texto) {
        byte[] carga = ("Exif\0\0" + texto).getBytes(StandardCharsets.ISO_8859_1);
        int largo = carga.length + 2;
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.write(jpeg, 0, 2);
        salida.write(0xFF);
        salida.write(0xE1);
        salida.write((largo >> 8) & 0xFF);
        salida.write(largo & 0xFF);
        salida.write(carga, 0, carga.length);
        salida.write(jpeg, 2, jpeg.length - 2);
        return salida.toByteArray();
    }
}
