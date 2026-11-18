package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.organizacion.service.ContenidoDelBrochure.Contacto;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El brochure en PDF de las tres plantillas, leído de vuelta, sin base de datos: el orden, las
 * secciones vacías que no salen, misión sin visión, las sedes sin dirección, los caracteres
 * especiales, los textos al máximo y las imágenes.
 *
 * <p>Sin {@code @Nested} a propósito: el check del harness no corre las anidadas.
 */
@DisplayName("El brochure de la empresa en PDF")
class PdfDelBrochureTest {

    private final PdfDelBrochure pdf = new PdfDelBrochure();

    static final String QUIENES = "Somos una empresa del norte del Perú que compra, procesa y exporta "
            + "café de altura.\nTrabajamos con 640 familias productoras de Jaén y San Ignacio.";
    static final String PROPOSITO = "Que el café de altura del norte del Perú se pague por lo que "
            + "vale, y que ese valor se quede en las familias que lo cultivan.";
    static final String MISION = "Comprar el café de nuestras familias productoras a precio justo y "
            + "llevarlo a los mejores tostadores del mundo.";
    static final String VISION = "Ser en 2030 la marca de café de origen más reconocida del norte "
            + "del Perú.";
    static final List<Contacto> CONTACTO = List.of(
            new Contacto("Perfil en EX", "https://ex.renaser.pe/empresas/cafetalera",
                    "ex.renaser.pe/empresas/cafetalera"),
            new Contacto("Página web", "https://cafevallealto.pe", "cafevallealto.pe"),
            new Contacto("Facebook", "https://www.facebook.com/cafevallealto",
                    "facebook.com/cafevallealto"),
            new Contacto("Video de presentación", "https://www.youtube.com/watch?v=abc123DEF45",
                    "youtube.com/watch?v=abc123DEF45"));

    // ================================================================== el orden y lo que lleva

    @Test
    @DisplayName("AC-03 · «Clásica» completa: todo, en el orden del perfil, y el pie con la fecha")
    void clasicaCompletaEnOrden() throws IOException {
        byte[] archivo = pdf.generar(completo(PlantillaDelBrochure.CLASICA, ColorDelBrochure.AZUL),
                logo(), portada());
        String todo = texto(archivo);

        assertThat(todo).contains("Cafetalera Valle Alto",
                "Agroindustria · 51–200 colaboradores · Fundada en 2004",
                "PRESENTACIÓN DE LA EMPRESA", normalizado(QUIENES), normalizado(PROPOSITO),
                normalizado(MISION), normalizado(VISION), "Seguro EPS", "Jaén", "cafevallealto.pe",
                "ex.renaser.pe/empresas/cafetalera", "BROCHURE GENERADO CON EX · 17/11/2026");
        enOrden(todo, "QUIÉNES SOMOS", "NUESTRO PROPÓSITO", "MISIÓN", "VISIÓN", "BENEFICIOS",
                "DÓNDE ESTAMOS", "CONTACTO", "BROCHURE GENERADO CON EX");
        assertThat(paginas(archivo)).isBetween(1, 3);
        // El pie de la última hoja dice cuántas son.
        int total = paginas(archivo);
        assertThat(texto(archivo, total, total)).contains("PÁGINA " + total + " DE " + total);
    }

    @Test
    @DisplayName("AC-10 · «Portada» con foto: la portada a toda la primera hoja, con el propósito, y lo demás después")
    void portadaConFoto() throws IOException {
        byte[] archivo = pdf.generar(completo(PlantillaDelBrochure.PORTADA, ColorDelBrochure.VERDE),
                logo(), portada());

        String primera = texto(archivo, 1, 1);
        assertThat(primera).contains("Cafetalera Valle Alto", "NUESTRO PROPÓSITO",
                normalizado(PROPOSITO)).doesNotContain("QUIÉNES SOMOS");
        // La portada no lleva pie.
        assertThat(primera).doesNotContain("PÁGINA 1");
        String resto = texto(archivo, 2, paginas(archivo));
        enOrden(resto, "QUIÉNES SOMOS", "MISIÓN", "VISIÓN", "BENEFICIOS", "DÓNDE ESTAMOS", "CONTACTO",
                "BROCHURE GENERADO CON EX · 17/11/2026");
        // El propósito va en la portada, una sola vez.
        assertThat(cuantas(texto(archivo), "NUESTRO PROPÓSITO")).isEqualTo(1);
        // La foto a toda la hoja y el logo encima.
        assertThat(imagenes(archivo, 1)).contains("1600x400", "512x512");
        assertThat(paginas(archivo)).isBetween(2, 3);
    }

    @Test
    @DisplayName("AC-05 · AC-10 · «Columnas» con foto: columna con datos, sedes y contacto; la portada como franja")
    void columnasConFoto() throws IOException {
        byte[] archivo = pdf.generar(completo(PlantillaDelBrochure.COLUMNAS, ColorDelBrochure.PETROLEO),
                logo(), portada());

        String primera = texto(archivo, 1, 1);
        assertThat(primera).contains("PRESENTACIÓN DE LA EMPRESA", "Cafetalera Valle Alto", "SECTOR",
                "Agroindustria", "TAMAÑO", "51–200 colaboradores", "FUNDADA EN", "2004",
                "DÓNDE ESTAMOS", "Jaén", "CONTACTO", "Perfil en EX", "ex.renaser.pe/empresas/cafetalera");
        enOrden(texto(archivo), "QUIÉNES SOMOS", "NUESTRO PROPÓSITO", "MISIÓN", "VISIÓN", "BENEFICIOS",
                "BROCHURE GENERADO CON EX");
        assertThat(imagenes(archivo, 1)).contains("1600x400", "512x512");
    }

    @Test
    @DisplayName("AC-10 · «Clásica» no usa la foto de portada: solo el logo")
    void clasicaSoloLogo() throws IOException {
        byte[] archivo = pdf.generar(completo(PlantillaDelBrochure.CLASICA, ColorDelBrochure.AZUL),
                logo(), portada());
        assertThat(imagenes(archivo, 1)).containsOnly("512x512");
    }

    // ================================================================== sin logo ni portada

    @ParameterizedTest(name = "{0}")
    @EnumSource(PlantillaDelBrochure.class)
    @DisplayName("AC-09 · sin logo ni portada: las iniciales y el nombre, y ninguna imagen")
    void sinLogoNiPortada(PlantillaDelBrochure plantilla) throws IOException {
        byte[] archivo = pdf.generar(completo(plantilla, ColorDelBrochure.CIRUELA), null, null);

        String primera = texto(archivo, 1, 1);
        assertThat(primera).contains("CV", "Cafetalera Valle Alto");
        for (int i = 1; i <= paginas(archivo); i++) {
            assertThat(imagenes(archivo, i)).as("hoja %d", i).isEmpty();
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PlantillaDelBrochure.class)
    @DisplayName("una imagen que no se puede leer es como no tenerla: salen las iniciales, sin romper el PDF")
    void imagenIlegible(PlantillaDelBrochure plantilla) throws IOException {
        byte[] basura = "esto no es una imagen".getBytes();
        byte[] archivo = pdf.generar(completo(plantilla, ColorDelBrochure.ROJO), basura, basura);

        assertThat(texto(archivo, 1, 1)).contains("CV");
        assertThat(imagenes(archivo, 1)).isEmpty();
    }

    // ================================================================== secciones vacías

    @ParameterizedTest(name = "{0}")
    @EnumSource(PlantillaDelBrochure.class)
    @DisplayName("solo con «Quiénes somos»: ningún título de las secciones vacías")
    void soloQuienesSomos(PlantillaDelBrochure plantilla) throws IOException {
        ContenidoDelBrochure solo = new ContenidoDelBrochure("Textiles Ayllu", null, null, null,
                QUIENES, null, null, null, List.of(), List.of(), List.of(CONTACTO.get(0)), null, null,
                plantilla, ColorDelBrochure.AZUL, "17/11/2026");
        byte[] archivo = pdf.generar(solo, null, null);
        String todo = texto(archivo);

        assertThat(todo).contains("QUIÉNES SOMOS", normalizado(QUIENES), "Textiles Ayllu",
                "CONTACTO", "Perfil en EX")
                .doesNotContain("NUESTRO PROPÓSITO", "MISIÓN", "VISIÓN", "BENEFICIOS", "DÓNDE ESTAMOS",
                        "SECTOR", "TAMAÑO", "FUNDADA EN");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PlantillaDelBrochure.class)
    @DisplayName("AC-03 · AC-04 · misión sin visión: la misión a todo el ancho y ningún «Visión»")
    void misionSinVision(PlantillaDelBrochure plantilla) throws IOException {
        ContenidoDelBrochure sinVision = new ContenidoDelBrochure("Cafetalera Valle Alto", null, null,
                null, QUIENES, PROPOSITO, MISION, "   ", List.of(), List.of(), CONTACTO, null, null,
                plantilla, ColorDelBrochure.AZUL, "17/11/2026");
        String todo = texto(pdf.generar(sinVision, null, null));

        assertThat(todo).contains("MISIÓN", normalizado(MISION)).doesNotContain("VISIÓN", "Visión");
    }

    // ================================================================== sedes y contacto

    @Test
    @DisplayName("AC-12 · «Dónde estamos» lleva las provincias que se le dan, sin direcciones")
    void dondeEstamosSinDirecciones() throws IOException {
        ContenidoDelBrochure c = conProvincias(PlantillaDelBrochure.CLASICA, List.of("Jaén"));
        String todo = texto(pdf.generar(c, null, null));
        assertThat(todo).contains("DÓNDE ESTAMOS", "Jaén");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PlantillaDelBrochure.class)
    @DisplayName("con muchas sedes y todos los enlaces no se pierde ninguna, en tres hojas como mucho")
    void muchasSedes(PlantillaDelBrochure plantilla) throws IOException {
        List<String> provincias = IntStream.rangeClosed(1, 30).mapToObj(i -> "Provincia número " + i)
                .toList();
        byte[] archivo = pdf.generar(conProvincias(plantilla, provincias), logo(), portada());
        String todo = texto(archivo);

        for (String provincia : provincias) {
            assertThat(todo).contains(provincia + " ");
        }
        for (Contacto enlace : contactoCompleto()) {
            assertThat(todo).contains(enlace.escrito());
        }
        assertThat(paginas(archivo)).isLessThanOrEqualTo(3);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PlantillaDelBrochure.class)
    @DisplayName("los enlaces de «Contacto» se pueden pulsar y llevan la dirección escrita")
    void enlacesQueSePulsan(PlantillaDelBrochure plantilla) throws IOException {
        byte[] archivo = pdf.generar(completo(plantilla, ColorDelBrochure.NOCHE), logo(), portada());

        assertThat(urls(archivo)).contains("https://ex.renaser.pe/empresas/cafetalera",
                "https://cafevallealto.pe", "https://www.facebook.com/cafevallealto",
                "https://www.youtube.com/watch?v=abc123DEF45");
        assertThat(texto(archivo)).contains("ex.renaser.pe/empresas/cafetalera", "cafevallealto.pe",
                "facebook.com/cafevallealto", "youtube.com/watch?v=abc123DEF45");
    }

    // ================================================================== textos al máximo

    @ParameterizedTest(name = "{0}")
    @EnumSource(PlantillaDelBrochure.class)
    @DisplayName("AC-11 · con todos los textos al máximo nada se corta y no pasa de tres hojas")
    void textosAlMaximo(PlantillaDelBrochure plantilla) throws IOException {
        String quienes = largo("Trabajamos con familias productoras del norte. ", 1500);
        String proposito = largo("Que el valor del café se quede en el campo. ", 1500);
        String mision = largo("Comprar a precio justo y procesar con cuidado. ", 400);
        String vision = largo("Ser la marca de origen más reconocida del país. ", 400);
        List<String> beneficios = IntStream.rangeClosed(1, 12)
                .mapToObj(i -> largo("Beneficio largo número " + i + " ", 40)).toList();
        String nombre = "Cooperativa Agraria Cafetalera y de Servicios Múltiples del Valle Alto";
        ContenidoDelBrochure c = new ContenidoDelBrochure(nombre, "s".repeat(60), "Más de 1000 colaboradores",
                1987, quienes, proposito, mision, vision, beneficios,
                List.of("Jaén", "San Ignacio", "Lima", "Chiclayo", "Piura"), contactoCompleto(), 1L, 2L,
                plantilla, ColorDelBrochure.VERDE, "17/11/2026");
        byte[] archivo = pdf.generar(c, logo(), portada());
        String todo = texto(archivo);

        assertThat(paginas(archivo)).isLessThanOrEqualTo(3);
        assertThat(todo).contains(normalizado(quienes), normalizado(proposito), normalizado(mision),
                normalizado(vision));
        for (String beneficio : beneficios) {
            assertThat(todo).contains(beneficio);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PlantillaDelBrochure.class)
    @DisplayName("un nombre de empresa muy largo se parte en líneas y no se corta")
    void nombreMuyLargo(PlantillaDelBrochure plantilla) throws IOException {
        String nombre = "Corporación Industrial y Comercial de Alimentos Andinos del Sur Peruano "
                + "Sociedad Anónima Cerrada";
        ContenidoDelBrochure c = new ContenidoDelBrochure(nombre, null, null, null, QUIENES, PROPOSITO,
                null, null, List.of(), List.of(), CONTACTO, null, null, plantilla, ColorDelBrochure.AZUL,
                "17/11/2026");
        assertThat(texto(pdf.generar(c, null, null), 1, 1)).contains(nombre);
    }

    @Test
    @DisplayName("un texto lleno de saltos de línea pasa de hoja sin perder ninguna línea")
    void textoConMuchosSaltos() throws IOException {
        String quienes = IntStream.rangeClosed(1, 120).mapToObj(i -> "Línea " + i)
                .collect(Collectors.joining("\n"));
        ContenidoDelBrochure c = new ContenidoDelBrochure("Acme", null, null, null, quienes, null,
                null, null, List.of(), List.of(), CONTACTO, null, null, PlantillaDelBrochure.PORTADA,
                ColorDelBrochure.AZUL, "17/11/2026");
        String todo = texto(pdf.generar(c, null, null));
        assertThat(todo).contains("Línea 1 ", "Línea 60 ", "Línea 120 ");
    }

    // ================================================================== caracteres especiales

    @ParameterizedTest(name = "{0}")
    @EnumSource(PlantillaDelBrochure.class)
    @DisplayName("AC-13 · tildes, «ñ», comillas, saltos de línea y algo parecido a HTML salen tal cual")
    void caracteresEspeciales(PlantillaDelBrochure plantilla) throws IOException {
        String quienes = "Año de la cigüeña: «comillas», “curvas” y 'simples'.\n<b>Negrita</b> & <script>x</script>";
        ContenidoDelBrochure c = new ContenidoDelBrochure("Panadería Ñuñoa & Cía.", null, null, null,
                quienes, "Pan <i>rico</i> para todos", "Misión: «ñandú»", null, List.of("Café & té"),
                List.of("Cañete"), CONTACTO, null, null, plantilla, ColorDelBrochure.AZUL, "17/11/2026");
        String todo = texto(pdf.generar(c, null, null));

        assertThat(todo).contains("Panadería Ñuñoa & Cía.", "Año de la cigüeña: «comillas», “curvas” y 'simples'.",
                "<b>Negrita</b> & <script>x</script>", "Pan <i>rico</i> para todos", "Misión: «ñandú»",
                "Café & té", "Cañete");
    }

    // ================================================================== tiempo y metadatos

    @Test
    @DisplayName("se arma en menos de 3 segundos, con los textos al máximo, y lleva su título")
    void rapidoYConTitulo() throws IOException {
        ContenidoDelBrochure c = completo(PlantillaDelBrochure.PORTADA, ColorDelBrochure.AZUL);
        pdf.generar(c, logo(), portada());
        long inicio = System.nanoTime();
        byte[] archivo = pdf.generar(c, logo(), portada());
        long milisegundos = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(milisegundos).isLessThan(3000);
        try (PDDocument documento = Loader.loadPDF(archivo)) {
            assertThat(documento.getDocumentInformation().getTitle())
                    .isEqualTo("Brochure - Cafetalera Valle Alto");
        }
    }

    @Test
    @DisplayName("el velo de la portada va de casi transparente arriba a casi opaco abajo")
    void elVelo() {
        assertThat(PdfDelBrochure.opacidadDelVelo(0f)).isEqualTo(0.15f);
        assertThat(PdfDelBrochure.opacidadDelVelo(1f)).isEqualTo(0.96f);
        assertThat(PdfDelBrochure.opacidadDelVelo(0.72f)).isEqualTo(0.9f);
        assertThat(PdfDelBrochure.opacidadDelVelo(0.5f)).isBetween(0.35f, 0.9f);
    }

    // ================================================================== datos de prueba

    static ContenidoDelBrochure completo(PlantillaDelBrochure plantilla, ColorDelBrochure color) {
        return new ContenidoDelBrochure("Cafetalera Valle Alto", "Agroindustria",
                "51–200 colaboradores", 2004, QUIENES, PROPOSITO, MISION, VISION,
                List.of("Seguro EPS", "Movilidad a planta", "Almuerzo en planta"),
                List.of("Jaén", "San Ignacio", "Lima"), CONTACTO, 10L, 20L, plantilla, color,
                "17/11/2026");
    }

    private static ContenidoDelBrochure conProvincias(PlantillaDelBrochure plantilla,
                                                      List<String> provincias) {
        return new ContenidoDelBrochure("Cafetalera Valle Alto", "Agroindustria",
                "51–200 colaboradores", 2004, QUIENES, PROPOSITO, MISION, VISION,
                List.of("Seguro EPS"), provincias, contactoCompleto(), 10L, 20L, plantilla,
                ColorDelBrochure.AZUL, "17/11/2026");
    }

    private static List<Contacto> contactoCompleto() {
        List<Contacto> lista = new ArrayList<>(CONTACTO);
        lista.add(new Contacto("Instagram", "https://www.instagram.com/cafevallealto",
                "instagram.com/cafevallealto"));
        lista.add(new Contacto("LinkedIn", "https://www.linkedin.com/company/cafetalera-valle-alto",
                "linkedin.com/company/cafetalera-valle-alto"));
        lista.add(new Contacto("TikTok", "https://www.tiktok.com/@cafevallealto",
                "tiktok.com/@cafevallealto"));
        lista.add(new Contacto("YouTube", "https://www.youtube.com/@cafevallealto",
                "youtube.com/@cafevallealto"));
        return lista;
    }

    /** Un texto real (palabras con sus espacios) de exactamente {@code largo} caracteres. */
    static String largo(String frase, int largo) {
        StringBuilder salida = new StringBuilder();
        while (salida.length() < largo) {
            salida.append(frase);
        }
        return salida.substring(0, largo).strip() + ".";
    }

    static byte[] logo() throws IOException {
        BufferedImage imagen = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(new Color(0x0F4C3A));
        g.fillOval(64, 64, 384, 384);
        g.dispose();
        return codificar(imagen, "png");
    }

    static byte[] portada() throws IOException {
        BufferedImage imagen = new BufferedImage(1600, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(new Color(0xBCD3EA));
        g.fillRect(0, 0, 1600, 400);
        g.setColor(new Color(0x2E5E3E));
        g.fillRect(0, 250, 1600, 150);
        g.dispose();
        return codificar(imagen, "jpeg");
    }

    static byte[] codificar(BufferedImage imagen, String formato) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, formato, salida);
        return salida.toByteArray();
    }

    // ================================================================== lectura del PDF

    static String normalizado(String texto) {
        return texto.replaceAll("\\s+", " ").strip();
    }

    static String texto(byte[] archivo) throws IOException {
        try (PDDocument documento = Loader.loadPDF(archivo)) {
            return new PDFTextStripper().getText(documento).replaceAll("\\s+", " ");
        }
    }

    static String texto(byte[] archivo, int desde, int hasta) throws IOException {
        try (PDDocument documento = Loader.loadPDF(archivo)) {
            PDFTextStripper lector = new PDFTextStripper();
            lector.setStartPage(desde);
            lector.setEndPage(hasta);
            return lector.getText(documento).replaceAll("\\s+", " ");
        }
    }

    static int paginas(byte[] archivo) throws IOException {
        try (PDDocument documento = Loader.loadPDF(archivo)) {
            return documento.getNumberOfPages();
        }
    }

    /** Las imágenes que usa una hoja, como «ancho x alto». */
    static List<String> imagenes(byte[] archivo, int pagina) throws IOException {
        List<String> salida = new ArrayList<>();
        try (PDDocument documento = Loader.loadPDF(archivo)) {
            PDResources recursos = documento.getPage(pagina - 1).getResources();
            for (COSName nombre : recursos.getXObjectNames()) {
                if (recursos.getXObject(nombre) instanceof PDImageXObject imagen) {
                    salida.add(imagen.getWidth() + "x" + imagen.getHeight());
                }
            }
        }
        return salida;
    }

    static List<String> urls(byte[] archivo) throws IOException {
        List<String> salida = new ArrayList<>();
        try (PDDocument documento = Loader.loadPDF(archivo)) {
            for (PDPage pagina : documento.getPages()) {
                for (PDAnnotation a : pagina.getAnnotations()) {
                    if (a instanceof PDAnnotationLink link && link.getAction() instanceof PDActionURI uri) {
                        salida.add(uri.getURI());
                    }
                }
            }
        }
        return salida;
    }

    static void enOrden(String texto, String... partes) {
        int desde = -1;
        for (String parte : partes) {
            int donde = texto.indexOf(parte, desde + 1);
            assertThat(donde).as("«%s» después de la parte anterior en: %s", parte, texto).isGreaterThan(desde);
            desde = donde;
        }
    }

    static int cuantas(String texto, String buscado) {
        int n = 0;
        int desde = 0;
        while ((desde = texto.indexOf(buscado, desde)) >= 0) {
            n++;
            desde += buscado.length();
        }
        return n;
    }
}
