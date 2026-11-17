package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Anexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Bloque;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Criterio;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Dato;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Detalle;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Enlace;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EntregableAnexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Evaluado;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Experiencia;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Nota;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.PreguntaAnexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Punto;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Recuento;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Resumen;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Senal;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El PDF de los tres informes, leído de vuelta: lo que dice cada página, sin base de datos.
 *
 * <p>Se arma el informe a mano —los datos que el armado sacaría de la base— y se comprueba lo
 * que el PDF enseña: los bloques en la primera página, nada recortado, la nota una sola vez, las
 * notas ajustadas, los huecos y los caracteres especiales.
 */
@DisplayName("El PDF del informe por etapa")
class PdfDelInformeTest {

    private final PdfDelInforme pdf = new PdfDelInforme();

    @Nested
    @DisplayName("Informe del CV")
    class DelCv {

        @Test
        @DisplayName("AC-01 · AC-02 · la primera página trae cabecera, experiencia, nota, criterios y los tres bloques con sus preguntas")
        void laPrimeraPaginaTraeTodo() throws IOException {
            byte[] archivo = pdf.generar(InformesDePrueba.cv(3));

            String primera = texto(archivo, 1, 1);
            // Las etiquetas de sección van en mayúsculas, como en la maqueta.
            assertThat(primera).contains("INFORME DEL CURRÍCULUM", "Ana Torres Quispe",
                    "Analista de Recursos Humanos", "RENASER CONSULTING", "EXPERIENCIA PROFESIONAL",
                    "4 años y 2 meses", "Analista de Selección", "QUÉ SE EVALUÓ", "Lo que el CV demuestra",
                    "No cuadra", "Por verificar en la evaluación", "74", "de 100", "GENERADO");
            // AC-02: las dos declaradas con su pregunta de validación.
            assertThat(primera).contains("«Manejo de planillas y nómina»",
                    "¿Cuántos colaboradores tenía la última planilla que cerraste?",
                    "«Excel avanzado»", "Cuéntame un reporte que armaste tú.");
            assertThat(texto(archivo)).doesNotContain("Por verificar en la entrevista");
            assertThat(paginas(archivo)).isEqualTo(1);
        }

        @Test
        @DisplayName("D-1 · F-1 · con un CV corriente (ocho explicaciones completas, 5 demostradas, 1 que no cuadra y 3 dudas) los tres bloques empiezan en la primera hoja, seguidos")
        void conUnCvCorrienteLosTresBloquesEmpiezanEnLaPrimera() throws IOException {
            byte[] archivo = pdf.generar(InformesDePrueba.cvCon(InformesDePrueba.EXPLICACION_CORRIENTE, 5, 1, 3));

            String primera = texto(archivo, 1, 1);
            assertThat(primera).contains("QUÉ SE EVALUÓ", "Lo que el CV demuestra", "No cuadra",
                    "Por verificar en la evaluación", "«Duda número 1»", "¿Pregunta número 1?");
            String todo = texto(archivo);
            IntStream.rangeClosed(1, 5).forEach(i -> assertThat(todo).contains("Logro demostrado número " + i));
            IntStream.rangeClosed(1, 3).forEach(i -> assertThat(todo).contains("«Duda número " + i + "»",
                    "¿Pregunta número " + i + "?"));
            assertThat(cuantas(todo, InformesDePrueba.EXPLICACION_CORRIENTE)).isEqualTo(8);
            // Cabe seguido: nada se aparta a la segunda hoja con «(sigue)».
            assertThat(todo.toLowerCase(java.util.Locale.ROOT)).doesNotContain("(sigue)");
            assertThat(todo).doesNotContain("…");
        }

        @Test
        @DisplayName("D-1 · con explicaciones larguísimas y listas largas, cada bloque empieza en la primera hoja con su primer punto y sigue en la segunda sin partir ninguno")
        void conMuchoCadaBloqueEmpiezaEnLaPrimeraYSigue() throws IOException {
            String larga = (InformesDePrueba.EXPLICACION_CORRIENTE + " ").repeat(3).strip();
            byte[] archivo = pdf.generar(InformesDePrueba.cvCon(larga, 12, 4, 6));

            String primera = texto(archivo, 1, 1);
            assertThat(primera).contains("QUÉ SE EVALUÓ", "Resultados demostrables",
                    "Lo que el CV demuestra", "Logro demostrado número 1 ",
                    "No cuadra", "Contradicción número 1",
                    "Por verificar en la evaluación", "«Duda número 1»", "¿Pregunta número 1?");
            assertThat(paginas(archivo)).isGreaterThanOrEqualTo(2);
            assertThat(texto(archivo, 2, 2).toLowerCase(java.util.Locale.ROOT)).contains("(sigue)");

            String todo = texto(archivo);
            assertThat(cuantas(todo, larga)).isEqualTo(8);
            IntStream.rangeClosed(1, 12).forEach(i -> assertThat(todo).contains("Logro demostrado número " + i + " "));
            IntStream.rangeClosed(1, 4).forEach(i -> assertThat(todo).contains("Contradicción número " + i));
            // Ningún punto se parte: cada duda y su pregunta van en la misma hoja.
            int total = paginas(archivo);
            for (int i = 1; i <= 6; i++) {
                String duda = "«Duda número " + i + "»";
                String pregunta = "¿Pregunta número " + i + "?";
                boolean juntas = false;
                for (int p = 1; p <= total; p++) {
                    String hoja = texto(archivo, p, p);
                    juntas |= hoja.contains(duda) && hoja.contains(pregunta);
                }
                assertThat(juntas).as("%s y su pregunta en la misma hoja", duda).isTrue();
            }
            assertThat(todo).doesNotContain("…").doesNotContainPattern("y \\d+ más");
        }

        @Test
        @DisplayName("D-1 · con «Qué buscar en el CV» como cuarto bloque y mucho de todo, los cuatro empiezan en la primera hoja")
        void conLoBuscadoLosCuatroEmpiezanEnLaPrimera() throws IOException {
            String larga = (InformesDePrueba.EXPLICACION_CORRIENTE + " ").repeat(2).strip();
            Informe informe = InformesDePrueba.conBloque(InformesDePrueba.cvCon(larga, 9, 3, 5),
                    new Bloque("BUSCADO", "Qué buscar en el CV", "NEUTRO", "Sin datos", List.of(
                            new Punto("Cierre de planilla", "«Cerré la planilla de 120 personas»", "CV", "Lo tiene", null),
                            new Punto("Inglés", null, "CV", "No aparece", null))));
            String primera = texto(pdf.generar(informe), 1, 1);
            assertThat(primera).contains("Lo que el CV demuestra", "No cuadra", "Por verificar en la evaluación",
                    "Qué buscar en el CV", "Cierre de planilla");
        }

        @Test
        @DisplayName("AC-01e · el bloque «Enlaces» trae el CV y LinkedIn como enlaces que se abren")
        void losEnlacesSeAbren() throws IOException {
            byte[] archivo = pdf.generar(InformesDePrueba.cv(2));
            assertThat(texto(archivo, 1, 1)).contains("Currículum (PDF)", "LinkedIn",
                    "Los enlaces caducan 8 horas después de generar el informe.");
            assertThat(urls(archivo)).containsExactlyInAnyOrder("https://almacen.example/cv.pdf?firma=1",
                    "https://www.linkedin.com/in/ana");
        }

        @Test
        @DisplayName("AC-06 · el criterio ajustado a mano dice «ajustada a mano» y su motivo")
        void laAjustadaDiceSuMotivo() throws IOException {
            String todo = texto(pdf.generar(InformesDePrueba.cv(2)));
            assertThat(todo).contains("Ajustada a mano: el puesto pide cierres de nómina desde el primer mes");
        }

        @Test
        @DisplayName("Una nota que falta se escribe «—», nunca 0")
        void huecoEnVezDeCero() throws IOException {
            String todo = texto(pdf.generar(InformesDePrueba.cv(2)));
            assertThat(todo).contains("Desarrollo de personas").contains("—").contains("Sin nota todavía");
        }

        @Test
        @DisplayName("Las fuentes Onest y Syne van incrustadas")
        void fuentesIncrustadas() throws IOException {
            try (PDDocument documento = Loader.loadPDF(pdf.generar(InformesDePrueba.cv(2)))) {
                Set<String> nombres = new HashSet<>();
                boolean todasIncrustadas = true;
                for (PDPage pagina : documento.getPages()) {
                    for (var nombre : pagina.getResources().getFontNames()) {
                        PDFont fuente = pagina.getResources().getFont(nombre);
                        nombres.add(fuente.getName());
                        todasIncrustadas &= fuente.isEmbedded();
                    }
                }
                assertThat(nombres).anyMatch(n -> n.contains("Onest"));
                assertThat(nombres).anyMatch(n -> n.contains("Syne"));
                assertThat(todasIncrustadas).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Forma común: nada se recorta")
    class NadaSeRecorta {

        @Test
        @DisplayName("AC-01h · un bloque con más de cinco puntos los trae todos, sin «…» ni «y N más»")
        void todosLosPuntos() throws IOException {
            String todo = texto(pdf.generar(InformesDePrueba.cv(9)));
            IntStream.rangeClosed(1, 9).forEach(i ->
                    assertThat(todo).contains("Logro demostrado número " + i));
            assertThat(todo).doesNotContain("…").doesNotContainPattern("y \\d+ más");
        }

        @Test
        @DisplayName("AC-12 · tildes, ñ, comillas, saltos de línea y texto parecido a HTML se leen tal cual")
        void caracteresEspeciales() throws IOException {
            String raro = "Peñaloza dijo: «sí, “claro”» y <b>negrita</b> & 'otra' — Ñandú";
            Informe informe = InformesDePrueba.prueba(List.of(new PreguntaAnexo(1, "¿Qué harías?", "Abierta",
                    raro + "\nSegunda línea", "Lo que demostró: algo", null)));
            String todo = texto(pdf.generar(informe));
            assertThat(todo).contains("Peñaloza dijo: «sí, “claro”» y <b>negrita</b> & 'otra' — Ñandú",
                    "Segunda línea");
        }

        @Test
        @DisplayName("AC-12 · una respuesta de varias páginas pasa a la siguiente sin perder una línea")
        void textoLargoSigueEnLaSiguiente() throws IOException {
            StringBuilder largo = new StringBuilder();
            for (int i = 1; i <= 400; i++) {
                largo.append("Frase número ").append(i).append(" de una respuesta larguísima. ");
            }
            Informe informe = InformesDePrueba.prueba(List.of(new PreguntaAnexo(1, "Cuéntalo todo", "Abierta",
                    largo.toString(), null, null)));
            byte[] archivo = pdf.generar(informe);
            assertThat(paginas(archivo)).isGreaterThan(3);
            String todo = texto(archivo);
            assertThat(todo).contains("Frase número 1 de una", "Frase número 400 de una respuesta larguísima.");
            IntStream.rangeClosed(1, 400).forEach(i -> assertThat(todo).contains("Frase número " + i + " "));
        }

        @Test
        @DisplayName("Cada página lleva su número y el nombre del candidato al pie")
        void pieEnCadaPagina() throws IOException {
            byte[] archivo = pdf.generar(InformesDePrueba.prueba(List.of(new PreguntaAnexo(1, "P", "Abierta", "R", null, null))));
            int total = paginas(archivo);
            assertThat(total).isGreaterThanOrEqualTo(2);
            assertThat(texto(archivo, 1, 1)).contains("PÁGINA 1 DE " + total, "ANA TORRES QUISPE");
            assertThat(texto(archivo, 2, 2)).contains("2/" + total, "ANA TORRES QUISPE");
        }
    }

    @Nested
    @DisplayName("Informe de la prueba")
    class DeLaPrueba {

        @Test
        @DisplayName("AC-01c · AC-05 · trae «Por verificar en la entrevista» con su origen, criterios con máximo y quién, y los entregables")
        void bloquesDeLaPrueba() throws IOException {
            String todo = texto(pdf.generar(InformesDePrueba.prueba(List.of(
                    new PreguntaAnexo(1, "¿Dónde está el problema?", "Abierta · Diagnóstico",
                            "En la inducción.", "Separó causa y síntoma.", null)))));
            assertThat(todo).contains("Por verificar en la entrevista", "DE LA PRUEBA", "DE LA EVALUACIÓN",
                    "DEL CV", "Diagnóstico del problema", "23 / 25", "IA", "Persona",
                    "ENTREGABLES PEDIDOS", "Plan de reclutamiento", "ENTREGADO", "NO ENTREGADO",
                    "Cronograma por tienda");
        }

        @Test
        @DisplayName("AC-12 · con algo con que abrirlo, el nombre del entregable entregado es un enlace: el firmado de un archivo o la dirección pegada")
        void losEntregablesSeAbren() throws IOException {
            Informe base = InformesDePrueba.prueba(List.of());
            Informe conEnlaces = conEntregables(base, List.of(
                    new EntregableAnexo("Documento de Recursos Humanos", "Subido el 14/11 a las 10:14", true, 77L,
                            "https://almacen.example/rrhh.pdf?firma=8h"),
                    new EntregableAnexo("Video de sustentación", "Subido el 14/11 a las 10:20", true, null,
                            "https://drive.example/video"),
                    // No entregado: aunque trajera una dirección, no se enlaza.
                    new EntregableAnexo("Cronograma", "No se subió ningún archivo", false, null,
                            "https://drive.example/nada")));

            byte[] archivo = pdf.generar(conEnlaces);

            assertThat(urls(archivo)).contains("https://almacen.example/rrhh.pdf?firma=8h",
                    "https://drive.example/video").doesNotContain("https://drive.example/nada");
            assertThat(texto(archivo)).contains("Documento de Recursos Humanos", "Video de sustentación");
        }

        @Test
        @DisplayName("AC-13 · sin nada con que abrirlos, los entregables salen sin enlace, como siempre")
        void sinEnlaceComoSiempre() throws IOException {
            byte[] archivo = pdf.generar(InformesDePrueba.prueba(List.of()));
            assertThat(urls(archivo)).noneMatch(u -> u.contains("drive") || u.contains("rrhh"));
        }

        @Test
        @DisplayName("AC-01i · la nota de cada criterio sale una sola vez y «En pocas líneas» no empieza por la nota")
        void notaUnaSolaVez() throws IOException {
            String todo = texto(pdf.generar(InformesDePrueba.prueba(List.of())));
            assertThat(cuantas(todo, "23 / 25")).isEqualTo(1);
            assertThat(cuantas(todo, "10 / 20")).isEqualTo(1);
            String resumen = todo.substring(todo.indexOf("EN POCAS LÍNEAS") + "EN POCAS LÍNEAS".length()).strip();
            assertThat(resumen).startsWith("Entregó el plan completo");
        }
    }

    @Nested
    @DisplayName("Informe de la evaluación")
    class DeLaEvaluacion {

        @Test
        @DisplayName("AC-01b · AC-03 · AC-04 · bloques, «Lo que pidió el CV», el porqué de cada abierta con señales y el anexo completo")
        void laEvaluacion() throws IOException {
            byte[] archivo = pdf.generar(InformesDePrueba.evaluacion());
            String primera = texto(archivo, 1, 1);
            assertThat(primera).contains("Puntos fuertes", "Puntos a fortalecer", "Debilidades a tener en cuenta",
                    "Lo que pidió el CV", "POR CONTRASTAR", "Por verificar en la prueba del puesto");
            String todo = texto(archivo);
            assertThat(todo).contains("El porqué de cada nota".toUpperCase(), "3,5 / 4",
                    "Episodio real: sí", "Fue suyo: sí", "Dato duro: no", "Incomodidad: no",
                    "Preguntas y respuestas", "Un proceso se retrasa", "«Pido una fecha concreta»",
                    "Sin respuesta.");
            assertThat(todo).doesNotContain("Por verificar en la entrevista");
        }

        @Test
        @DisplayName("D-1 · con muchos puntos en cada bloque, los cinco bloques empiezan en la primera hoja y ninguno se recorta")
        void conMuchosPuntosLosCincoEmpiezanEnLaPrimera() throws IOException {
            byte[] archivo = pdf.generar(InformesDePrueba.evaluacionCon(9));
            String primera = texto(archivo, 1, 1);
            for (String bloque : List.of("Puntos fuertes", "Puntos a fortalecer", "Debilidades a tener en cuenta",
                    "Lo que pidió el CV", "Por verificar en la prueba del puesto")) {
                assertThat(primera).as("«%s» en la primera hoja", bloque).contains(bloque + " · punto número 1:");
            }
            String todo = texto(archivo);
            for (String bloque : List.of("Puntos fuertes", "Puntos a fortalecer", "Debilidades a tener en cuenta",
                    "Lo que pidió el CV", "Por verificar en la prueba del puesto")) {
                IntStream.rangeClosed(1, 9).forEach(i -> assertThat(todo).contains(bloque + " · punto número " + i + ":"));
            }
            assertThat(todo).doesNotContain("…").contains("Preguntas y respuestas");
        }

        @Test
        @DisplayName("AC-04 · en un banco sin señales no aparece ninguna casilla de señales")
        void sinSenales() throws IOException {
            Informe base = InformesDePrueba.evaluacion();
            List<Detalle> sin = base.detalle().stream().map(d -> new Detalle(d.numero(), d.titulo(), d.meta(),
                    d.notaTexto(), d.ajustada(), d.motivoAjuste(), d.explicacion(), d.evidencia(), List.of(),
                    d.pendiente())).toList();
            Informe sinSenales = new Informe(base.etapa(), base.titulo(), base.empresa(), base.vacante(),
                    base.postulacionId(), base.candidato(), base.correo(), base.generadoEn(), base.nota(),
                    base.grupo(), base.datosClave(), base.recuentos(), base.experiencia(), base.resumen(),
                    base.evaluado(), base.bloques(), base.enlaces(), base.detalleTitulo(), sin,
                    base.detallePie(), base.anexo(), base.aviso(), false, base.nombreArchivo());
            assertThat(texto(pdf.generar(sinSenales))).doesNotContain("Episodio real", "Dato duro");
        }
    }

    // ================================================================== lectura del PDF

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

    private static Informe conEntregables(Informe i, List<EntregableAnexo> entregables) {
        Anexo a = i.anexo();
        return new Informe(i.etapa(), i.titulo(), i.empresa(), i.vacante(), i.postulacionId(), i.candidato(),
                i.correo(), i.generadoEn(), i.nota(), i.grupo(), i.datosClave(), i.recuentos(), i.experiencia(),
                i.resumen(), i.evaluado(), i.bloques(), i.enlaces(), i.detalleTitulo(), i.detalle(), i.detallePie(),
                new Anexo(a.titulo(), a.subtitulo(), a.caso(), a.preguntas(), a.entregablesResumen(), entregables),
                i.aviso(), i.textosEnCamino(), i.nombreArchivo());
    }

    private static List<String> urls(byte[] archivo) throws IOException {
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

    private static int cuantas(String texto, String buscado) {
        int n = 0;
        int desde = 0;
        while ((desde = texto.indexOf(buscado, desde)) >= 0) {
            n++;
            desde += buscado.length();
        }
        return n;
    }
}
