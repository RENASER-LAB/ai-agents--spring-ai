package com.renaser.ai.ai_engine.perfilintegral.dto;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosExcelRanking.ExcelDeRanking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Los tres métodos de ExcelDeRanking están escritos a mano, y un método escrito a mano sin
 * prueba es el que se rompe al refactorizar. Lo que se comprueba aquí no es que alguien
 * compare volcados —hoy nadie lo hace—, sino que si algún día lo hace no se encuentre con la
 * respuesta que da un record con un array dentro: que dos hojas idénticas son distintas
 * porque están en dos sitios distintos de la memoria.
 */
@DisplayName("El archivo volcado se compara por su contenido, no por dónde está")
class DtosExcelRankingTest {

    private static final byte[] HOJA = {1, 2, 3};

    @Test
    @DisplayName("dos volcados con el mismo contenido son iguales aunque sean dos arrays")
    void mismoContenidoEsIgual() {
        ExcelDeRanking uno = new ExcelDeRanking("ranking.xlsx", new byte[]{1, 2, 3});
        ExcelDeRanking otro = new ExcelDeRanking("ranking.xlsx", new byte[]{1, 2, 3});

        assertThat(uno).isEqualTo(otro);
        assertThat(uno).hasSameHashCodeAs(otro);
    }

    @Test
    @DisplayName("si el contenido cambia, deja de ser el mismo")
    void distintoContenidoNoEsIgual() {
        assertThat(new ExcelDeRanking("ranking.xlsx", new byte[]{1, 2, 3}))
                .isNotEqualTo(new ExcelDeRanking("ranking.xlsx", new byte[]{9, 9, 9}));
    }

    @Test
    @DisplayName("y si cambia el nombre, tampoco")
    void distintoNombreNoEsIgual() {
        assertThat(new ExcelDeRanking("perfil.xlsx", HOJA))
                .isNotEqualTo(new ExcelDeRanking("prueba.xlsx", HOJA));
    }

    /*
     * Un volcado real pesa decenas de miles de bytes. Si toString los escupiera, cualquier
     * registro que lo incluya se vuelve ilegible y de paso mete binario en los logs.
     */
    @Test
    @DisplayName("al escribirlo dice cuánto pesa, y no vuelca el binario")
    void alEscribirloNoVuelcaElBinario() {
        String dicho = new ExcelDeRanking("ranking.xlsx", HOJA).toString();

        assertThat(dicho).contains("ranking.xlsx").contains("3 bytes");
        assertThat(dicho).doesNotContain("[B@");
    }

    @Test
    @DisplayName("sin contenido lo dice, en vez de reventar")
    void sinContenidoLoDice() {
        assertThat(new ExcelDeRanking("ranking.xlsx", null).toString()).contains("ausente");
    }
}
