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
}
