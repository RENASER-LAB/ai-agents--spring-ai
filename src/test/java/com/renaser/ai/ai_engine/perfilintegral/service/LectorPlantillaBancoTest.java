package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.BancoLeido;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.ErrorDeImportacion;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El lector del Excel del banco, contra archivos fabricados aquí mismo con POI.
 *
 * <p>Los archivos de estas pruebas se construyen en memoria porque lo que se prueba es
 * el lector, no la plantilla: cada caso monta exactamente el defecto que quiere ver
 * rechazado. La plantilla real completa la prueba de integración, que importa
 * {@code docs/insumos/banco-v3-directivo.xlsx} tal cual.
 */
@DisplayName("El lector de la plantilla Excel del banco")
class LectorPlantillaBancoTest {

    private final LectorPlantillaBanco lector = new LectorPlantillaBanco();

    // Un índice como el que armará el servicio: nombre y código, ya normalizados.
    private static final Map<String, String> DIMENSIONES = Map.of(
            "int", "INT", "integridad", "INT",
            "own", "OWN", "sentido de dueno", "OWN");

    // ============ Cómo se fabrican los archivos ============

    /** Una hoja al estilo «banco lleno»: encabezado en la fila 3, guía en la 4, datos. */
    private static void hojaLlena(XSSFWorkbook libro, String nombre, String ancla,
                                  String[][] filas) {
        XSSFSheet hoja = libro.createSheet(nombre);
        hoja.createRow(0).createCell(0).setCellValue(nombre + " — prueba");
        hoja.createRow(2).createCell(0).setCellValue(ancla);
        hoja.createRow(3).createCell(0).setCellValue("fila de guía");
        int n = 4;
        for (String[] fila : filas) {
            XSSFRow r = hoja.createRow(n++);
            for (int c = 0; c < fila.length; c++) {
                if (fila[c] != null) {
                    r.createCell(c).setCellValue(fila[c]);
                }
            }
        }
    }

    /** Una hoja al estilo «plantilla vacía»: encabezado en la 4, ejemplos y centinela. */
    private static void hojaConCentinela(XSSFWorkbook libro, String nombre, String ancla,
                                         String[][] ejemplos, String[][] datos) {
        XSSFSheet hoja = libro.createSheet(nombre);
        hoja.createRow(0).createCell(0).setCellValue(nombre + " — plantilla");
        hoja.createRow(1).createCell(0).setCellValue("subtítulo");
        hoja.createRow(3).createCell(0).setCellValue(ancla);
        hoja.createRow(4).createCell(0).setCellValue("fila de guía");
        int n = 5;
        for (String[] fila : ejemplos) {
            XSSFRow r = hoja.createRow(n++);
            for (int c = 0; c < fila.length; c++) {
                if (fila[c] != null) {
                    r.createCell(c).setCellValue(fila[c]);
                }
            }
        }
        hoja.createRow(n++).createCell(0)
                .setCellValue("⬇  Escribe aquí lo tuyo. Las filas grises son ejemplos.");
        for (String[] fila : datos) {
            XSSFRow r = hoja.createRow(n++);
            for (int c = 0; c < fila.length; c++) {
                if (fila[c] != null) {
                    r.createCell(c).setCellValue(fila[c]);
                }
            }
        }
    }

    private static byte[] bytesDe(XSSFWorkbook libro) {
        try (libro; ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            libro.write(salida);
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Un archivo sano con los 8 formatos, en el traje de banco lleno. */
    private static byte[] archivoSano() {
        XSSFWorkbook libro = new XSSFWorkbook();
        hojaLlena(libro, "Preguntas", "Código", new String[][]{
                {"X01", "EF-4", "Elige la frase que MÁS te describe.", null, "1", "no",
                        "Integridad, OWN"},
                {"X02", "SJT-R", "Una tarea no se cumplió. Califica cada acción.", "Contexto.",
                        "1", "no"},
                {"X03", "SEC", "Ordena los pasos.", null, "1", "no"},
                {"X04", "INV", "Marca lo que usabas.", null, "1", "no"},
                {"X05", "DE", "Marca las afirmaciones correctas.", null, "2", "no"},
                {"X06", "CD", "Tu caso. (2 campos)", null, "1", "no", null, "2",
                        "solo cuentan los completos"},
                {"X07", "V", "Personas a tu cargo:", null, "1", "no"},
                {"X08", "PC", "Autorizo la verificación.", null, "0", "sí"},
        });
        hojaLlena(libro, "Opciones", "Código de la pregunta", new String[][]{
                {"X01", "Digo lo que hay que decir", null, "2"},
                {"X01", "Evito el conflicto", null, "-2"},
                {"X02", "Revisar antes de reclamar", "5"},
                {"X02", "Escalarlo de inmediato", "1"},
                {"X03", "Cerrar el día anterior", null, null, null, "1"},
                {"X03", "Fijar la meta de hoy", null, null, null, "2"},
                {"X04", "Registro de asistencia", null, null, "no"},
                {"X04", "Planilla de holgura", null, null, "sí"},
                {"X05", "El control exige un estándar", null, null, "no"},
                {"X05", "Supervisar es no delegar", null, null, "sí"},
                {"X08", "Sí"},
                {"X08", "No"},
        });
        hojaLlena(libro, "Campos de caso (CD)", "Código de la pregunta", new String[][]{
                {"X06", "Nombre de la tarea (texto ≤ 40 car.)"},
                {"X06", "Cuánto te toma (menos de 1 h / más)", "si ambas vacías → inválido"},
        });
        hojaLlena(libro, "Rangos (V)", "Código de la pregunta", new String[][]{
                {"X07", "5 o más", "3", "no"},
                {"X07", "Ninguna", "0", "sí"},
        });
        hojaLlena(libro, "Pares", "Pregunta A", new String[][]{
                {"X01", "X02", "5", "3", "dicen lo contrario"},
        });
        return bytesDe(libro);
    }

    private static List<String> mensajes(BancoLeido leido) {
        return leido.errores().stream().map(ErrorDeImportacion::mensaje).toList();
    }

    private BancoLeido leer(byte[] archivo) {
        return lector.leer(new ByteArrayInputStream(archivo), DIMENSIONES);
    }

    // ============ Las pruebas ============

    @Nested
    @DisplayName("Con un archivo sano")
    class ConUnArchivoSano {

        @Test
        @DisplayName("lee los 8 formatos con sus hojas y no inventa ningún error")
        void leeLosOchoFormatos() {
            BancoLeido leido = leer(archivoSano());

            assertThat(leido.errores()).isEmpty();
            assertThat(leido.preguntas()).hasSize(8);
            assertThat(leido.opciones()).hasSize(12);
            assertThat(leido.camposCaso()).hasSize(2);
            assertThat(leido.rangos()).hasSize(2);
            assertThat(leido.pares()).hasSize(1);
        }

        @Test
        @DisplayName("deriva lo que la plantilla no pregunta: sí/no, dimensiones, nota interna")
        void derivaLoQueLaPlantillaNoPregunta() {
            BancoLeido leido = leer(archivoSano());

            var ef4 = leido.preguntas().get(0);
            // «Integridad, OWN» se resuelve contra el catálogo por nombre o por código
            assertThat(ef4.dimensiones()).containsExactly("INT", "OWN");
            var pc = leido.preguntas().get(7);
            assertThat(pc.esEliminatoria()).isTrue();
            assertThat(pc.peso()).isZero();
            var cd = leido.preguntas().get(5);
            assertThat(cd.casosPedidos()).isEqualTo((short) 2);
            assertThat(cd.logicaInterna()).isEqualTo("solo cuentan los completos");
            // La clave de cada formato cae en su campo y no en otro
            assertThat(leido.opciones().get(1).valor()).isEqualByComparingTo("-2");
            assertThat(leido.opciones().get(2).puntaje()).isEqualTo(5.0);
            assertThat(leido.opciones().get(5).ordenCorrecto()).isEqualTo((short) 2);
            assertThat(leido.opciones().get(7).esDistractor()).isTrue();
        }

        @Test
        @DisplayName("la plantilla vacía con ejemplos y centinela importa solo lo escrito debajo")
        void laPlantillaVaciaImportaSoloLoEscrito() {
            XSSFWorkbook libro = new XSSFWorkbook();
            hojaConCentinela(libro, "Preguntas", "Código",
                    // los ejemplos grises de la plantilla: no deben entrar
                    new String[][]{
                            {"D51", "EF-4", "Ejemplo gris que se borra.", null, "1", "no"},
                            {"O03", "CD", "Otro ejemplo. (6 campos)", null, "1", "no", null, "6"},
                    },
                    new String[][]{
                            {"Z01", "PC", "¿Aceptas?", null, "0", "no"},
                    });
            BancoLeido leido = leer(bytesDe(libro));

            assertThat(leido.errores()).isEmpty();
            assertThat(leido.preguntas()).hasSize(1);
            assertThat(leido.preguntas().get(0).codigo()).isEqualTo("Z01");
        }
    }

    @Nested
    @DisplayName("Con un archivo roto")
    class ConUnArchivoRoto {

        @Test
        @DisplayName("junta todos los problemas en una pasada, no se rinde en el primero")
        void juntaTodosLosProblemas() {
            XSSFWorkbook libro = new XSSFWorkbook();
            hojaLlena(libro, "Preguntas", "Código", new String[][]{
                    {"X01", "EF-4", "Repetida.", null, "1", "no"},
                    {"X01", "SJT-R", "Código duplicado.", null, "1", "no"},
                    {"X02", "ESTILO", "Formato del v0.1.", null, "1", "no"},
                    {"X03", "SJT-R", "Peso imposible.", null, "7", "no"},
                    {"X04", "CD", "CD sin N° de campos.", null, "1", "no"},
                    {"X05", "SJT-R", "Sí/no ilegible.", null, "1", "quizás"},
                    {"X06", "EF-4", "Dimensión desconocida.", null, "1", "no", "CARISMA"},
            });
            hojaLlena(libro, "Opciones", "Código de la pregunta", new String[][]{
                    {"X99", "De una pregunta que no existe"},
                    {"X03", "Respuesta esperada fuera de 1..5", "9"},
            });
            BancoLeido leido = leer(bytesDe(libro));

            assertThat(mensajes(leido))
                    .anySatisfy(m -> assertThat(m).contains("X01", "único"))
                    .anySatisfy(m -> assertThat(m).contains("ESTILO"))
                    .anySatisfy(m -> assertThat(m).contains("peso"))
                    .anySatisfy(m -> assertThat(m).contains("declara cuántos campos"))
                    .anySatisfy(m -> assertThat(m).contains("quizás"))
                    .anySatisfy(m -> assertThat(m).contains("CARISMA"))
                    .anySatisfy(m -> assertThat(m).contains("X99"))
                    .anySatisfy(m -> assertThat(m).contains("1 a 5"));
            assertThat(leido.errores()).hasSizeGreaterThanOrEqualTo(8);
        }

        @Test
        @DisplayName("cada error dice su hoja y su fila, que es lo que el admin necesita")
        void cadaErrorDiceSuHojaYSuFila() {
            XSSFWorkbook libro = new XSSFWorkbook();
            hojaLlena(libro, "Preguntas", "Código", new String[][]{
                    {"X01", "EF-4", "Sana.", null, "1", "no"},
                    {"X02", "ZZZ", "Tipo inválido.", null, "1", "no"},
            });
            BancoLeido leido = leer(bytesDe(libro));

            assertThat(leido.errores()).singleElement().satisfies(e -> {
                assertThat(e.hoja()).isEqualTo("Preguntas");
                assertThat(e.fila()).isEqualTo(6);   // 3 encabezado + 4 guía + X01 en la 5
                assertThat(e.mensaje()).contains("ZZZ");
            });
        }

        @Test
        @DisplayName("un CD cuyo N° de campos no cuadra con su hoja se rechaza")
        void unCdQueNoCuadraSeRechaza() {
            XSSFWorkbook libro = new XSSFWorkbook();
            hojaLlena(libro, "Preguntas", "Código", new String[][]{
                    {"X01", "CD", "Declara 3. (3 campos)", null, "1", "no", null, "3"},
            });
            hojaLlena(libro, "Campos de caso (CD)", "Código de la pregunta", new String[][]{
                    {"X01", "Único campo"},
            });
            BancoLeido leido = leer(bytesDe(libro));

            assertThat(mensajes(leido)).singleElement()
                    .satisfies(m -> assertThat(m).contains("declara 3", "trae 1"));
        }

        @Test
        @DisplayName("campos, rangos y opciones solo valen para el formato que los usa")
        void cadaHojaEsDeSuFormato() {
            XSSFWorkbook libro = new XSSFWorkbook();
            hojaLlena(libro, "Preguntas", "Código", new String[][]{
                    {"X01", "V", "Un dato verificable:", null, "1", "no"},
                    {"X02", "EF-4", "Un elige más y menos.", null, "1", "no"},
            });
            hojaLlena(libro, "Opciones", "Código de la pregunta", new String[][]{
                    {"X01", "Una opción para un V"},
            });
            hojaLlena(libro, "Campos de caso (CD)", "Código de la pregunta", new String[][]{
                    {"X02", "Un campo para un EF-4"},
            });
            hojaLlena(libro, "Rangos (V)", "Código de la pregunta", new String[][]{
                    {"X02", "Un rango para un EF-4", "2", "no"},
            });
            BancoLeido leido = leer(bytesDe(libro));

            assertThat(mensajes(leido))
                    .anySatisfy(m -> assertThat(m).contains("X01", "no lleva opciones"))
                    .anySatisfy(m -> assertThat(m).contains("solo para CD"))
                    .anySatisfy(m -> assertThat(m).contains("solo para V"));
        }

        @Test
        @DisplayName("un par con una pregunta fantasma o consigo misma no pasa")
        void unParTorcidoNoPasa() {
            XSSFWorkbook libro = new XSSFWorkbook();
            hojaLlena(libro, "Preguntas", "Código", new String[][]{
                    {"X01", "EF-4", "La única.", null, "1", "no"},
            });
            hojaLlena(libro, "Pares", "Pregunta A", new String[][]{
                    {"X01", "X99", "5", "3", "fantasma"},
                    {"X01", "X01", "5", "3", "consigo misma"},
            });
            BancoLeido leido = leer(bytesDe(libro));

            assertThat(mensajes(leido))
                    .anySatisfy(m -> assertThat(m).contains("X99"))
                    .anySatisfy(m -> assertThat(m).contains("ambas columnas"));
        }

        @Test
        @DisplayName("sin la hoja Preguntas no hay nada que importar")
        void sinLaHojaPreguntasNoHayNada() {
            XSSFWorkbook libro = new XSSFWorkbook();
            libro.createSheet("Otra cosa");
            BancoLeido leido = leer(bytesDe(libro));

            assertThat(mensajes(leido)).singleElement()
                    .satisfies(m -> assertThat(m).contains("Preguntas", "obligatoria"));
        }

        @Test
        @DisplayName("unos bytes que no son un xlsx dan un error del archivo, nunca un 500")
        void unosBytesQueNoSonXlsx() {
            BancoLeido leido = leer("esto es un docx renombrado".getBytes());

            assertThat(leido.preguntas()).isEmpty();
            assertThat(mensajes(leido)).singleElement()
                    .satisfies(m -> assertThat(m).contains(".xlsx"));
        }

        @Test
        @DisplayName("un «⬇» en medio de los datos se rechaza: si no, se tragaría lo de encima")
        void unCentinelaEnMedioSeRechaza() {
            XSSFWorkbook libro = new XSSFWorkbook();
            hojaConCentinela(libro, "Preguntas", "Código",
                    new String[][]{{"D51", "EF-4", "Ejemplo gris.", null, "1", "no"}},
                    new String[][]{
                            {"Z01", "PC", "La primera.", null, "0", "no"},
                            {"⬇  otra centinela copiada sin querer"},
                            {"Z02", "PC", "La segunda.", null, "0", "no"},
                    });
            BancoLeido leido = leer(bytesDe(libro));

            // Lo escrito ANTES del intruso sigue ahí: nada se pierde en silencio
            assertThat(leido.preguntas()).extracting("codigo").containsExactly("Z01", "Z02");
            assertThat(mensajes(leido)).singleElement()
                    .satisfies(m -> assertThat(m).contains("en medio de los datos"));
        }

        @Test
        @DisplayName("la plantilla sin llenar no crea un banco vacío: lo dice")
        void laPlantillaSinLlenarNoCreaUnBancoVacio() {
            XSSFWorkbook libro = new XSSFWorkbook();
            hojaConCentinela(libro, "Preguntas", "Código",
                    new String[][]{{"D51", "EF-4", "Solo el ejemplo gris.", null, "1", "no"}},
                    new String[][]{});
            BancoLeido leido = leer(bytesDe(libro));

            assertThat(leido.preguntas()).isEmpty();
            assertThat(mensajes(leido)).singleElement()
                    .satisfies(m -> assertThat(m).contains("no tiene ninguna pregunta"));
        }

        @Test
        @DisplayName("la fórmula y la tabla prestada son solo de los ítems V, y nunca las dos")
        void laFormulaYLaTablaPrestadaSonSoloDeLosV() {
            XSSFWorkbook libro = new XSSFWorkbook();
            hojaLlena(libro, "Preguntas", "Código", new String[][]{
                    {"X01", "V", "Con fórmula.", null, "1", "no", null, null, null,
                            "(campos llenos ÷ 5) × 3"},
                    {"X02", "V", "Con tabla prestada.", null, "1", "no", null, null, null,
                            null, "X01"},
                    {"X03", "V", "Con las dos cosas.", null, "1", "no", null, null, null,
                            "una fórmula", "X01"},
                    {"X04", "EF-4", "Con fórmula sin ser V.", null, "1", "no", null, null,
                            null, "una fórmula"},
            });
            BancoLeido leido = leer(bytesDe(libro));

            assertThat(leido.preguntas()).extracting("codigo").containsExactly("X01", "X02");
            assertThat(leido.preguntas().get(0).formulaPuntaje()).isEqualTo("(campos llenos ÷ 5) × 3");
            assertThat(leido.preguntas().get(1).rangosDePreguntaCodigo()).isEqualTo("X01");
            assertThat(mensajes(leido))
                    .anySatisfy(m -> assertThat(m).contains("elige una"))
                    .anySatisfy(m -> assertThat(m).contains("solo del tipo V"));
        }
    }
}
