package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.BancoLeido;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.FilaPregunta;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El lector del banco CAZATALENTOS, contra los archivos reales de la clienta.
 *
 * <p>No hay fixtures sintéticos a propósito: los xlsx corregidos de docs/insumos SON el
 * instrumento, y si el lector no los traga tal cual están, el que se equivoca es el lector.
 */
@DisplayName("El lector del banco CAZATALENTOS")
class LectorBancoCazatalentosTest {

    private static final Path INSUMOS = Path.of("docs", "insumos");

    private final LectorBancoCazatalentos lector = new LectorBancoCazatalentos();

    private BancoLeido lee(String archivo) throws Exception {
        byte[] bytes = Files.readAllBytes(INSUMOS.resolve(archivo));
        assertThat(lector.esSuyo(bytes)).as("detección por hoja de " + archivo).isTrue();
        return lector.leer(new ByteArrayInputStream(bytes));
    }

    @Nested
    @DisplayName("Los tres archivos de la clienta se leen sin un solo error")
    class LosTresArchivos {

        @Test
        @DisplayName("DIR: 18 puntuables y 3 de cierre")
        void dir() throws Exception {
            BancoLeido leido = lee("CAZATALENTOS-DIR.xlsx");
            assertThat(leido.errores()).isEmpty();
            assertThat(leido.preguntas()).hasSize(21);
            assertThat(puntuables(leido)).hasSize(18);
        }

        @Test
        @DisplayName("SUP: 15 puntuables y 3 de cierre")
        void sup() throws Exception {
            BancoLeido leido = lee("CAZATALENTOS-SUP.xlsx");
            assertThat(leido.errores()).isEmpty();
            assertThat(puntuables(leido)).hasSize(15);
        }

        @Test
        @DisplayName("OPE: 12 puntuables y 3 de cierre")
        void ope() throws Exception {
            BancoLeido leido = lee("CAZATALENTOS-OPE.xlsx");
            assertThat(leido.errores()).isEmpty();
            assertThat(puntuables(leido)).hasSize(12);
        }

        private List<FilaPregunta> puntuables(BancoLeido leido) {
            return leido.preguntas().stream().filter(p -> p.peso() > 0).toList();
        }
    }

    @Nested
    @DisplayName("Lo que cada fila trae puesto")
    class LoQueTrae {

        @Test
        @DisplayName("Toda puntuable sale con tipo ABIERTA, su pilar y sus tres declaraciones")
        void completas() throws Exception {
            for (String archivo : List.of("CAZATALENTOS-DIR.xlsx", "CAZATALENTOS-SUP.xlsx",
                    "CAZATALENTOS-OPE.xlsx")) {
                for (FilaPregunta p : lee(archivo).preguntas()) {
                    assertThat(p.tipo()).isEqualTo("ABIERTA");
                    if (p.peso() > 0) {
                        assertThat(p.dimensiones()).as(p.codigo() + " lleva su pilar").hasSize(1);
                        assertThat(p.c3Esperado()).as(p.codigo() + " C3").isNotBlank();
                        assertThat(p.c4Esperado()).as(p.codigo() + " C4").isNotBlank();
                        assertThat(p.senalDeCero()).as(p.codigo() + " señal").isNotBlank();
                    }
                }
            }
        }

        @Test
        @DisplayName("DIR: R18 y Z03 eliminatorias, R11/R14/R15 con peso 2, y el pilar correcto")
        void marcasDeDir() throws Exception {
            Map<String, FilaPregunta> por = lee("CAZATALENTOS-DIR.xlsx").preguntas().stream()
                    .collect(Collectors.toMap(FilaPregunta::codigo, Function.identity()));

            assertThat(por.get("R18").esEliminatoria()).isTrue();
            assertThat(por.get("Z03").esEliminatoria()).isTrue();
            assertThat(por.get("Z03").peso()).isEqualTo((short) 0);
            assertThat(por.get("R11").peso()).isEqualTo((short) 2);
            assertThat(por.get("R14").peso()).isEqualTo((short) 2);
            assertThat(por.get("R15").peso()).isEqualTo((short) 2);
            assertThat(por.get("R01").dimensiones()).containsExactly("PIL_INICIATIVA");
            assertThat(por.get("R15").dimensiones()).containsExactly("PIL_DIRECCION");
            assertThat(por.get("R18").dimensiones()).containsExactly("PIL_INTEGRIDAD");
        }

        @Test
        @DisplayName("La regla dura de R11 llega como marcador legible por el motor")
        void reglaDuraDeR11() throws Exception {
            Map<String, FilaPregunta> por = lee("CAZATALENTOS-DIR.xlsx").preguntas().stream()
                    .collect(Collectors.toMap(FilaPregunta::codigo, Function.identity()));
            assertThat(LectorBancoCazatalentos.topeSinDato(por.get("R11").logicaInterna()))
                    .isEqualTo(2);
            // Y solo ella: las demás notas internas no declaran tope.
            assertThat(LectorBancoCazatalentos.topeSinDato(por.get("R15").logicaInterna()))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("El marcador de la regla dura, por sí solo")
    class MarcaDeTope {

        @Test
        @DisplayName("La prosa del Excel produce el marcador, y el marcador devuelve su número")
        void idaYVuelta() {
            String marcada = LectorBancoCazatalentos.conMarcaDeTope(
                    "REGLA DURA: sin ninguna cifra, el máximo de esta pregunta es 2.");
            assertThat(marcada).startsWith("[TOPE_SIN_DATO=2] ");
            assertThat(LectorBancoCazatalentos.topeSinDato(marcada)).isEqualTo(2);
        }

        @Test
        @DisplayName("Una nota interna cualquiera pasa intacta")
        void notaNormal() {
            String nota = "Señal de 0 levanta bandera REACTIVO.";
            assertThat(LectorBancoCazatalentos.conMarcaDeTope(nota)).isEqualTo(nota);
            assertThat(LectorBancoCazatalentos.topeSinDato(nota)).isNull();
        }
    }

    @Test
    @DisplayName("Un libro sin la hoja «Prueba RENASER» no es de este lector")
    void otroFormatoNoEsSuyo() {
        // Bytes cualquiera: ni siquiera es un xlsx. esSuyo responde que no, sin reventar.
        assertThat(lector.esSuyo("no soy un xlsx".getBytes())).isFalse();
    }

    @Nested
    @DisplayName("Los errores se acumulan con su fila, y con uno solo no se usa nada")
    class LosErrores {

        private BancoLeido leeFilas(String[][] filas) throws Exception {
            try (var libro = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                 var salida = new java.io.ByteArrayOutputStream()) {
                var hoja = libro.createSheet(LectorBancoCazatalentos.HOJA);
                hoja.createRow(0).createCell(0).setCellValue("Prueba RENASER — de prueba");
                hoja.createRow(2).createCell(0).setCellValue("Código");
                hoja.createRow(3).createCell(0).setCellValue("guía de la columna");
                int n = 4;
                for (String[] fila : filas) {
                    var r = hoja.createRow(n++);
                    for (int c = 0; c < fila.length; c++) {
                        if (fila[c] != null) {
                            r.createCell(c).setCellValue(fila[c]);
                        }
                    }
                }
                libro.write(salida);
                return lector.leer(new ByteArrayInputStream(salida.toByteArray()));
            }
        }

        private String[] fila(String codigo, String pilar, String enunciado, String c3,
                              String c4, String senal, String peso, String elim) {
            return new String[]{codigo, pilar, enunciado, c3, c4, senal, peso, elim};
        }

        private final String[] sana =
                fila("R01", "1 Iniciativa", "¿Qué mejoraste?", "el dato", "lo feo", "nada", "1", "no");

        @Test
        @DisplayName("Una puntuable sin C3, C4 ni señal junta las tres faltas de una vez")
        void sinLaGuiaDelEvaluador() throws Exception {
            BancoLeido leido = leeFilas(new String[][]{
                    fila("R01", "1 Iniciativa", "¿Qué mejoraste?", null, null, null, "1", "no")});
            assertThat(leido.errores()).extracting(e -> e.mensaje())
                    .anyMatch(m -> m.contains("C3"))
                    .anyMatch(m -> m.contains("C4"))
                    .anyMatch(m -> m.contains("SEÑAL DE 0"));
            assertThat(leido.preguntas()).as("con errores, lo leído no se usa").isEmpty();
        }

        @Test
        @DisplayName("Una de cierre (peso 0) no necesita la guía: puntúa el peso, no la columna")
        void elCierreNoLaNecesita() throws Exception {
            BancoLeido leido = leeFilas(new String[][]{sana,
                    fila("Z01", "CIERRE", "¿Por qué tú?", null, null, null, "0", "no")});
            assertThat(leido.errores()).isEmpty();
            assertThat(leido.preguntas()).hasSize(2);
        }

        @Test
        @DisplayName("Código repetido, enunciado ausente, peso y eliminatoria inválidos: cada uno con su fila")
        void lasFaltasClasicas() throws Exception {
            BancoLeido leido = leeFilas(new String[][]{
                    sana,
                    fila("R01", "1 Iniciativa", "¿Otra vez R01?", "d", "f", "s", "1", "no"),
                    fila("R03", "1 Iniciativa", null, "d", "f", "s", "1", "no"),
                    fila("R04", "1 Iniciativa", "¿Peso raro?", "d", "f", "s", "7", "no"),
                    fila("R05", "1 Iniciativa", "¿Eliminatoria rara?", "d", "f", "s", "1", "quizás")});
            assertThat(leido.errores()).extracting(e -> e.mensaje())
                    .anyMatch(m -> m.contains("ya apareció"))
                    .anyMatch(m -> m.contains("falta la pregunta"))
                    .anyMatch(m -> m.contains("peso debe ser 0, 1 o 2"))
                    .anyMatch(m -> m.contains("«sí» o «no»"));
        }

        @Test
        @DisplayName("Un pilar que no es ninguno de los 7 no se adivina")
        void pilarDesconocido() throws Exception {
            BancoLeido leido = leeFilas(new String[][]{
                    fila("R01", "9 Carisma", "¿...?", "d", "f", "s", "1", "no")});
            assertThat(leido.errores()).extracting(e -> e.mensaje())
                    .anyMatch(m -> m.contains("no es ninguno de los 7"));
        }

        @Test
        @DisplayName("Sin encabezado «Código» o sin ninguna pregunta, el error lo dice")
        void hojaVaciaOSinAncla() throws Exception {
            assertThat(leeFilas(new String[][]{}).errores())
                    .extracting(e -> e.mensaje())
                    .anyMatch(m -> m.contains("ninguna pregunta"));
            // Y un libro con la hoja pero sin la fila de encabezados:
            try (var libro = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                 var salida = new java.io.ByteArrayOutputStream()) {
                libro.createSheet(LectorBancoCazatalentos.HOJA)
                        .createRow(0).createCell(0).setCellValue("sin ancla");
                libro.write(salida);
                BancoLeido leido = lector.leer(new ByteArrayInputStream(salida.toByteArray()));
                assertThat(leido.errores()).extracting(e -> e.mensaje())
                        .anyMatch(m -> m.contains("encabezados"));
            }
        }

        @Test
        @DisplayName("Un archivo ilegible es un error del archivo, no una excepción al aire")
        void archivoIlegible() {
            BancoLeido leido = lector.leer(new ByteArrayInputStream("basura".getBytes()));
            assertThat(leido.errores()).isNotEmpty();
            assertThat(leido.preguntas()).isEmpty();
        }
    }
}
