package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.archivo.service.TiposDeArchivo;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;

/**
 * El logo y la portada de una empresa (V85): qué se acepta y cómo se limpia antes de guardar.
 *
 * <p><b>Son los primeros archivos de EX que se ven sin cuenta</b>, así que no se guarda lo que
 * llega: se lee la imagen y se vuelve a codificar en el servidor. Lo que se guarda son solo los
 * píxeles, sin los metadatos que traía (la posición GPS, la cámara, la fecha). El panel ya la
 * recorta y la repinta en el navegador, pero un cliente que llame a la API directamente no pasa
 * por ahí.
 *
 * <p>Solo JPG y PNG, hasta 2 MB (el perfil del candidato admite también WebP: aquí no, porque
 * ImageIO no lo sabe re-codificar). Un logo de al menos 200 × 200 y una portada de al menos
 * 1200 × 300. Por arriba, 4096 píxeles por lado: una imagen de 2 MB puede descomprimirse en
 * cientos de megas, y la imagen se mide antes de abrirla.
 */
public final class ImagenesDeLaEmpresa {

    /** El lado más grande que se abre. El panel manda 512 × 512 o 1600 × 400. */
    public static final int LADO_MAXIMO = 4096;

    private static final float CALIDAD_JPEG = 0.9f;

    private ImagenesDeLaEmpresa() {
    }

    /** Cuál de las dos: lo que se pide en la dirección y sus reglas. */
    public enum Cual {
        LOGO("logo", "El logo", true, 200, 200),
        PORTADA("portada", "La portada", false, 1200, 300);

        private final String codigo;
        private final String como;
        private final boolean masculino;
        private final int anchoMinimo;
        private final int altoMinimo;

        Cual(String codigo, String como, boolean masculino, int anchoMinimo, int altoMinimo) {
            this.codigo = codigo;
            this.como = como;
            this.masculino = masculino;
            this.anchoMinimo = anchoMinimo;
            this.altoMinimo = altoMinimo;
        }

        public String codigo() {
            return codigo;
        }

        /** «logo» o «portada», exactos y en minúsculas; cualquier otra cosa no existe. */
        public static Optional<Cual> deTexto(String texto) {
            for (Cual cual : values()) {
                if (cual.codigo.equals(texto)) {
                    return Optional.of(cual);
                }
            }
            return Optional.empty();
        }

        public String mensajeFormato() {
            return como + " tiene que ser JPG o PNG.";
        }

        public String mensajePeso() {
            return como + " no puede pesar más de 2 MB. Prueba a guardarl"
                    + (masculino ? "o" : "a") + " más pequeñ" + (masculino ? "o" : "a") + ".";
        }

        public String mensajeIlegible() {
            return como + " no es una imagen que podamos leer.";
        }

        public String mensajeMinimo() {
            return como + " tiene que medir al menos " + anchoMinimo + " × " + altoMinimo
                    + " píxeles.";
        }

        public String mensajeMaximo() {
            return como + " es demasiado grande: hasta " + LADO_MAXIMO + " × " + LADO_MAXIMO
                    + " píxeles.";
        }
    }

    /** La imagen ya limpia: sus bytes, su tipo y la extensión con la que se guarda. */
    public record ImagenLimpia(byte[] bytes, String tipo, String extension) {

        @Override
        public boolean equals(Object otro) {
            return otro instanceof ImagenLimpia o && java.util.Arrays.equals(bytes, o.bytes)
                    && tipo.equals(o.tipo) && extension.equals(o.extension);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(java.util.Arrays.hashCode(bytes), tipo, extension);
        }

        @Override
        public String toString() {
            return "ImagenLimpia[tipo=" + tipo + ", bytes=" + bytes.length + "]";
        }
    }

    /**
     * Comprueba la imagen y la vuelve a codificar sin metadatos.
     *
     * @throws IllegalArgumentException con el motivo, dicho para quien la subió
     */
    public static ImagenLimpia recodificar(MultipartFile archivo, Cual cual) {
        String tipo = archivo.getContentType() == null ? "" : archivo.getContentType();
        String extension = TiposDeArchivo.extensionDe(archivo.getOriginalFilename());
        boolean declaraPng = tipo.equals("image/png") && extension.equals("png");
        boolean declaraJpg = tipo.equals("image/jpeg")
                && (extension.equals("jpg") || extension.equals("jpeg"));
        if (!declaraPng && !declaraJpg) {
            throw new IllegalArgumentException(cual.mensajeFormato());
        }
        if (archivo.getSize() > TiposDeArchivo.MAXIMO_IMAGEN) {
            throw new IllegalArgumentException(cual.mensajePeso());
        }
        final byte[] crudo;
        try {
            crudo = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la imagen que llegó", e);
        }
        return recodificar(crudo, cual);
    }

    /** Lo mismo con los bytes ya en la mano. El formato lo dice el contenido, no el nombre. */
    public static ImagenLimpia recodificar(byte[] crudo, Cual cual) {
        Leida leida = leer(crudo, cual);
        boolean png = leida.formato().equals("png");
        byte[] limpio = png ? escribirPng(leida.imagen()) : escribirJpeg(leida.imagen());
        return new ImagenLimpia(limpio, png ? "image/png" : "image/jpeg", png ? "png" : "jpg");
    }

    private record Leida(BufferedImage imagen, String formato) {}

    /**
     * Lee solo PNG o JPEG, ignorando sus metadatos, y mide la imagen antes de abrirla.
     *
     * <p>⚠️ El {@code IllegalArgumentException} se lanza fuera del {@code try}: dentro, el
     * {@code catch} de los fallos de lectura lo convertiría en «no es una imagen».
     */
    private static Leida leer(byte[] crudo, Cual cual) {
        String problema = null;
        Leida leida = null;
        try (ImageInputStream entrada = ImageIO.createImageInputStream(
                new ByteArrayInputStream(crudo))) {
            Iterator<ImageReader> lectores = entrada == null
                    ? java.util.Collections.emptyIterator() : ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) {
                problema = cual.mensajeIlegible();
            } else {
                ImageReader lector = lectores.next();
                try {
                    String formato = lector.getFormatName().toLowerCase(Locale.ROOT);
                    lector.setInput(entrada, true, true);
                    int ancho = lector.getWidth(0);
                    int alto = lector.getHeight(0);
                    if (!formato.equals("png") && !formato.equals("jpeg")) {
                        problema = cual.mensajeFormato();
                    } else if (ancho > LADO_MAXIMO || alto > LADO_MAXIMO) {
                        problema = cual.mensajeMaximo();
                    } else if (ancho < cual.anchoMinimo || alto < cual.altoMinimo) {
                        problema = cual.mensajeMinimo();
                    } else {
                        leida = new Leida(lector.read(0), formato);
                    }
                } finally {
                    lector.dispose();
                }
            }
        } catch (IOException | RuntimeException e) {
            problema = cual.mensajeIlegible();
        }
        if (problema != null || leida == null || leida.imagen() == null) {
            throw new IllegalArgumentException(problema == null ? cual.mensajeIlegible() : problema);
        }
        return leida;
    }

    private static byte[] escribirPng(BufferedImage imagen) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(imagen, "png", salida)) {
                throw new IllegalStateException("No hay quien escriba PNG en este servidor");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo volver a codificar la imagen", e);
        }
        return salida.toByteArray();
    }

    /**
     * JPEG sin transparencia: lo que tuviera canal alfa (o no sea RGB) se pinta antes sobre
     * blanco, porque el escritor de JPEG de ImageIO no sabe escribir alfa.
     */
    private static byte[] escribirJpeg(BufferedImage original) {
        BufferedImage rgb = original;
        if (original.getType() != BufferedImage.TYPE_INT_RGB) {
            rgb = new BufferedImage(original.getWidth(), original.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D pincel = rgb.createGraphics();
            try {
                pincel.drawImage(original, 0, 0, Color.WHITE, null);
            } finally {
                pincel.dispose();
            }
        }
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ImageOutputStream destino = ImageIO.createImageOutputStream(salida)) {
            escritor.setOutput(destino);
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(CALIDAD_JPEG);
            escritor.write(null, new IIOImage(rgb, null, null), parametros);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo volver a codificar la imagen", e);
        } finally {
            escritor.dispose();
        }
        return salida.toByteArray();
    }

    /**
     * La dirección pública de la imagen, la única por la que se sirve. Lleva el id del archivo
     * como versión ({@code ?v=}): se sirve con caché pública de un día, y al cambiar el logo la
     * dirección cambia con él.
     */
    public static String direccionPublica(String codigoEmpresa, Cual cual, Long archivoId) {
        if (codigoEmpresa == null || archivoId == null) {
            return null;
        }
        return "/api/v1/portal/empresas/" + codigoEnLaDireccion(codigoEmpresa) + "/imagenes/"
                + cual.codigo() + "?v=" + archivoId;
    }

    /** El código en minúsculas y escapado, como va en {@code /empresas/<código>}. */
    public static String codigoEnLaDireccion(String codigoEmpresa) {
        return URLEncoder.encode(codigoEmpresa.toLowerCase(Locale.ROOT), StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
