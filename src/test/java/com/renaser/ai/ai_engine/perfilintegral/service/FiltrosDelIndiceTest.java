package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosIndiceDePruebas.FilaDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("«Pruebas»: los filtros se combinan y el orden pone las publicadas primero (AC-06)")
class FiltrosDelIndiceTest {

    private static FilaDePrueba fila(long id, String titulo, String nivel, String situacion, String estado,
                                     boolean laRinde, Instant publicadaEn) {
        return new FilaDePrueba(id, titulo, null, nivel, situacion, estado, laRinde, false, 1, 1, null, null,
                null, null, publicadaEn, false);
    }

    private static final FilaDePrueba CONTADOR = fila(1, "Asistente Contable", "EJECUCION", "PUBLICADA",
            "PUBLICADA", true, Instant.parse("2026-10-14T15:00:00Z"));
    private static final FilaDePrueba CAJERO = fila(2, "Cajero", "EJECUCION", "SIN_PUBLICAR", "BORRADOR",
            false, null);
    private static final FilaDePrueba JEFE = fila(3, "Jefe de Recursos Humanos", "DIRECCION", "CERRADA",
            "PUBLICADA", false, Instant.parse("2026-07-28T15:00:00Z"));
    private static final FilaDePrueba SIN_NIVEL = fila(4, "Recepcionista bilingüe", null, "ARCHIVADA",
            "PUBLICADA", false, Instant.parse("2026-11-01T15:00:00Z"));

    private static List<Long> filtrar(FiltrosDelIndice filtros) {
        return Stream.of(CONTADOR, CAJERO, JEFE, SIN_NIVEL).filter(filtros::admite).map(FilaDePrueba::vacanteId)
                .toList();
    }

    private static FiltrosDelIndice con(String buscar, List<String> niveles, List<String> estados,
                                        List<String> situaciones) {
        return FiltrosDelIndice.de(buscar, niveles, estados, situaciones);
    }

    @Test
    @DisplayName("El buscador mira solo el título, sin tildes ni mayúsculas")
    void buscador() {
        assertThat(filtrar(con("CONTÁBLE", null, null, null))).containsExactly(1L);
        assertThat(filtrar(con("  bilingue ", null, null, null))).containsExactly(4L);
        assertThat(filtrar(con("", null, null, null))).containsExactly(1L, 2L, 3L, 4L);
        // Lo que no es el título no se busca: ni el nivel ni la situación
        assertThat(filtrar(con("ejecucion", null, null, null))).isEmpty();
    }

    @Test
    @DisplayName("Dentro de un grupo vale cualquiera; entre grupos, todos; sin nivel no coincide con ningún nivel")
    void seCombinan() {
        assertThat(filtrar(con(null, List.of("EJECUCION", "DIRECCION"), null, null))).containsExactly(1L, 2L, 3L);
        assertThat(filtrar(con(null, List.of("ejecucion"), List.of("PUBLICADA"), null))).containsExactly(1L);
        assertThat(filtrar(con(null, null, List.of("EN_USO"), null))).containsExactly(1L);
        assertThat(filtrar(con(null, null, List.of("BORRADOR", "EN_USO"), null))).containsExactly(1L, 2L);
        assertThat(filtrar(con(null, null, null, List.of("CERRADA", "ARCHIVADA")))).containsExactly(3L, 4L);
        assertThat(filtrar(con("jefe", List.of("DIRECCION"), List.of("PUBLICADA"), List.of("CERRADA"))))
                .containsExactly(3L);
        assertThat(filtrar(con("jefe", List.of("EJECUCION"), null, null))).isEmpty();
        assertThat(filtrar(con(null, null, List.of("OTRA_COSA"), null))).isEmpty();
        // Huecos y nulos de la dirección no filtran
        assertThat(filtrar(con(null, Arrays.asList(" ", null), null, null))).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("Las publicadas por su fecha, la más reciente arriba; los borradores al final, por título")
    void orden() {
        FilaDePrueba otroBorrador = fila(5, "Analista de Marketing", "EJECUCION", "SIN_PUBLICAR", "BORRADOR",
                false, null);
        assertThat(Stream.of(CAJERO, JEFE, otroBorrador, CONTADOR, SIN_NIVEL).sorted(FiltrosDelIndice.ORDEN)
                .map(FilaDePrueba::vacanteId)).containsExactly(4L, 1L, 3L, 5L, 2L);
    }
}
