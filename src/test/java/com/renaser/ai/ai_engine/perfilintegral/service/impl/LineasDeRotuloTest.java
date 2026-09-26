package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cuántas líneas pide un rótulo de la cabecera del Excel.
 *
 * <p>Lo que importa es no quedarse corto: una línea de menos es un rótulo cortado al abrir
 * el archivo, que es justo lo que la altura escrita existe para evitar. Por eso los casos
 * de aquí comprueban los rótulos reales de las dos hojas, y los bordes: la palabra que no
 * cabe, el salto de línea escrito y el tope.
 */
@DisplayName("Las líneas que ocupa un rótulo de la cabecera")
class LineasDeRotuloTest {

    private static final int TOPE = 8;

    @Test
    @DisplayName("el rótulo más largo de la vacante 13 pide cinco líneas en 14 de ancho")
    void elRotuloDeLaVacante13() {
        // «Orientación a / resultados y / plan de / crecimiento / (pts /10)».
        assertThat(LineasDeRotulo.cuantas(
                "Orientación a resultados y plan de crecimiento (pts /10)", 14, TOPE))
                .isEqualTo(5);
    }

    @Test
    @DisplayName("lo que cabe en su columna es una línea")
    void loQueCabeEsUnaLinea() {
        assertThat(LineasDeRotulo.cuantas("#", 5, TOPE)).isEqualTo(1);
        assertThat(LineasDeRotulo.cuantas("Candidato", 34, TOPE)).isEqualTo(1);
        assertThat(LineasDeRotulo.cuantas("Justificación detallada", 90, TOPE)).isEqualTo(1);
        assertThat(LineasDeRotulo.cuantas("Caja (pts /50)", 14, TOPE)).isEqualTo(1);
    }

    @Test
    @DisplayName("las notas fijas de la cabecera piden dos líneas")
    void lasNotasFijasSonDosLineas() {
        assertThat(LineasDeRotulo.cuantas("Nota Examen Técnico /100", 15, TOPE)).isEqualTo(2);
        assertThat(LineasDeRotulo.cuantas("Nota Perfil Integral /100", 16, TOPE)).isEqualTo(2);
        assertThat(LineasDeRotulo.cuantas("Nota Combinada /100", 17, TOPE)).isEqualTo(2);
    }

    @Test
    @DisplayName("nunca pide menos de las que exige el número de caracteres")
    void nuncaSeQuedaCorto() {
        // Una cota que no depende de la tabla de anchos: en una columna de N caracteres no
        // caben más de N letras por línea, así que un rótulo de L letras pide al menos L/N.
        String[] rotulos = {
                "Experiencia y magnitud de lo administrado (pts /15)",
                "Manejo y control de caja (pts /20)",
                "Conocimiento del negocio de divisas (pts /15)",
                "Supervisión de múltiples sedes (pts /15)",
                "Coordinación contable y financiera (pts /10)",
                "Sistemas o procesos creados",
                "MMMMMMMMMMMMMMMMMMMMMMMMMMMM"};
        for (String rotulo : rotulos) {
            int cota = (int) Math.ceil(rotulo.length() / 14.0);
            assertThat(LineasDeRotulo.cuantas(rotulo, 14, TOPE))
                    .as(rotulo).isGreaterThanOrEqualTo(cota);
        }
    }

    @Test
    @DisplayName("una palabra más larga que la columna se parte en las líneas que pida")
    void laPalabraQueNoCabeSeParte() {
        // Veintiséis «a» a 1,05 en 13 de sitio útil: tres líneas, no una.
        assertThat(LineasDeRotulo.cuantas("a".repeat(26), 14, TOPE)).isEqualTo(3);
    }

    @Test
    @DisplayName("un salto de línea escrito en el rótulo empieza otra línea")
    void elSaltoDeLineaCuenta() {
        assertThat(LineasDeRotulo.cuantas("Caja\nTrato\nDivisas", 30, TOPE)).isEqualTo(3);
    }

    @Test
    @DisplayName("el tope se respeta aunque el rótulo pida muchas más, y sin recorrerlo entero")
    void elTope() {
        assertThat(LineasDeRotulo.cuantas("Capacidad demostrada para ".repeat(200), 14, TOPE))
                .isEqualTo(TOPE);
        assertThat(LineasDeRotulo.cuantas("x".repeat(32_767), 14, TOPE)).isEqualTo(TOPE);
        assertThat(LineasDeRotulo.cuantas("\n".repeat(50), 14, TOPE)).isEqualTo(TOPE);
    }

    @Test
    @DisplayName("un rótulo vacío o nulo ocupa su línea")
    void elVacioOcupaUnaLinea() {
        assertThat(LineasDeRotulo.cuantas("", 14, TOPE)).isEqualTo(1);
        assertThat(LineasDeRotulo.cuantas(null, 14, TOPE)).isEqualTo(1);
    }

    @Test
    @DisplayName("un tope por debajo de una línea es un error de quien llama")
    void elTopeTieneQueSerPositivo() {
        assertThatThrownBy(() -> LineasDeRotulo.cuantas("Caja", 14, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
