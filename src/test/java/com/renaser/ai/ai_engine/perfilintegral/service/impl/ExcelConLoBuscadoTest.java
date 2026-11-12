package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosExcelRanking.PedidoExcelRanking;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.BusquedaEnElCv;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.FilaRanking;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.RankingVacante;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioPerfilIntegralPanel;
import com.renaser.ai.ai_engine.postulacion.service.ServicioPostulacionesPanel;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** AC-20: el Excel del ranking con la columna «Qué buscar en el CV». */
@ExtendWith(MockitoExtension.class)
@DisplayName("El Excel del ranking: la columna «Qué buscar en el CV»")
class ExcelConLoBuscadoTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneOffset.UTC);
    private static final ContextoUsuario QUIEN = new ContextoUsuario(7L, 3L, 1L, "EQUIPO",
            List.of(1L), Map.of("ver_embudo", "TODO"));

    @Mock private ServicioPerfilIntegralPanel tandas;
    @Mock private ServicioPostulacionesPanel archivos;

    private ServicioExcelRankingImpl servicio;

    @BeforeEach
    void armar() {
        servicio = new ServicioExcelRankingImpl(tandas, archivos, RELOJ);
    }

    private static BusquedaEnElCv busqueda(String estado, int tiene) {
        return new BusquedaEnElCv(estado, tiene, 0, 4, null, null, List.of());
    }

    private static FilaRanking fila(int puesto, long id, String candidato, BusquedaEnElCv busqueda) {
        return new FilaRanking(puesto, id, UUID.randomUUID().toString(), candidato, "correo@x.pe",
                "PERFIL_POR_CONFIRMAR", "Perfil por confirmar", "TERMINADA", "FINA", null, null,
                null, "ALTA", null, null, null, null, null, null, null, 0, 0, 0, null, List.of(),
                null, null, null, null, null, null, null, null, null, null, null, false, busqueda);
    }

    private static RankingVacante tanda(List<String> lineas, FilaRanking... filas) {
        return new RankingVacante(13L, "Chef de cocina", "Chef", "EJECUCION", filas.length,
                filas.length, filas.length, 0, 0, false, false, true, false, List.of(filas),
                lineas, java.math.BigDecimal.valueOf(25), false);
    }

    @Test
    @DisplayName("Después de «Teléfono», en el orden pedido, con «3 de 4» y los estados")
    void laColumnaDespuesDelTelefono() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                List.of("A", "B", "C", "D"),
                fila(1, 1L, "Lucía", busqueda("TERMINADA", 3)),
                fila(2, 2L, "Mariana", busqueda("TERMINADA", 4)),
                fila(3, 3L, "Renzo", busqueda("BUSCANDO", 0)),
                fila(4, 4L, "Carla", busqueda("SIN_TEXTO", 0)),
                fila(5, 5L, "Pedro", busqueda("FALLIDA", 0))));

        byte[] libro = servicio.generar(QUIEN, 13L, new PedidoExcelRanking("PERFIL_INTEGRAL",
                List.of(2L, 1L, 3L, 4L, 5L), "Perfil integral · Orden: por lo buscado")).contenido();
        List<List<String>> celdas = celdas(libro);

        assertThat(celdas.get(0).subList(0, 7))
                .containsExactly("#", "Candidato", "Correo", "CV", "Teléfono", "Qué buscar en el CV",
                        "Nota Perfil Integral /100");
        assertThat(celdas.subList(1, 6)).extracting(f -> f.get(1))
                .containsExactly("Mariana", "Lucía", "Renzo", "Carla", "Pedro");
        assertThat(celdas.subList(1, 6)).extracting(f -> f.get(5))
                .containsExactly("4 de 4", "3 de 4", "Pendiente", "CV sin texto", "No se pudo buscar");
        assertThat(columnaCero(celdas)).anyMatch(t -> t.contains("Orden: por lo buscado"))
                .anyMatch(t -> t.contains("«Qué buscar en el CV» dice cuántas"));
    }

    @Test
    @DisplayName("Sin líneas en la vacante, la columna no sale")
    void sinLineasNoSale() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                List.of(), fila(1, 1L, "Lucía", null)));

        byte[] libro = servicio.generar(QUIEN, 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(1L), null)).contenido();

        assertThat(celdas(libro).get(0)).doesNotContain("Qué buscar en el CV");
        assertThat(columnaCero(celdas(libro))).noneMatch(t -> t.contains("«Qué buscar en el CV»"));
    }

    private static List<String> columnaCero(List<List<String>> celdas) {
        return celdas.stream().filter(f -> !f.isEmpty()).map(f -> f.get(0)).toList();
    }

    private static List<List<String>> celdas(byte[] contenido) {
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
            DataFormatter formateador = new DataFormatter(Locale.US);
            Sheet hoja = libro.getSheet("Datos");
            List<List<String>> filas = new ArrayList<>();
            for (int i = 0; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                List<String> suyas = new ArrayList<>();
                if (fila != null) {
                    for (int c = 0; c < fila.getLastCellNum(); c++) {
                        suyas.add(formateador.formatCellValue(fila.getCell(c)));
                    }
                }
                filas.add(suyas);
            }
            return filas;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
