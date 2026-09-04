package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosExcelRanking.ExcelDeRanking;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosExcelRanking.PedidoExcelRanking;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.DatosCandidato;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.FilaRanking;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.Ponderado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.NotaCriterioResponse;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.RankingVacante;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioPerfilIntegralPanel;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba;
import com.renaser.ai.ai_engine.prueba.service.ServicioCalificacionPrueba;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.PaneInformation;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lo que este volcado tiene que hacer bien, y que ninguna hoja delata si lo hace mal.
 *
 * <ul>
 *   <li><b>El orden.</b> La tanda del ranking llega ordenada por grupo y nota; el pedido trae
 *       otro orden, el que eligió quien mira. Escribir el primero y no el segundo produce un
 *       archivo perfectamente válido que no se parece a la pantalla desde la que se exportó, y
 *       nadie lo nota hasta que alguien compara. Por eso las pruebas de aquí abajo piden un
 *       orden <b>distinto</b> del que devuelve el mock: con los dos iguales, no probarían nada.
 *   <li><b>Una nota que falta.</b> Un hueco en una columna de números se lee como un cero, y un
 *       cero es un juicio que nadie hizo.
 *   <li><b>Una columna muda.</b> La pretensión va bajo llave: para media plantilla llega vacía
 *       siempre, y eso no significa que nadie pidiera sueldo.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El ranking volcado a Excel")
class ServicioExcelRankingImplTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-08-31T14:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ServicioPerfilIntegralPanel tandas;
    @Mock
    private ServicioCalificacionPrueba notasDeLaPrueba;

    private ServicioExcelRankingImpl servicio;

    @BeforeEach
    void armar() {
        servicio = new ServicioExcelRankingImpl(tandas, notasDeLaPrueba, RELOJ);
    }

    // ========================================================================
    // El orden: la trampa que este archivo existe para no volver a pisar
    // ========================================================================

    @Test
    @DisplayName("las filas salen en el orden PEDIDO, no en el que ordenó el ranking")
    void elOrdenQueMandaEsElDelPedido() {
        // La tanda, como la devuelve el ranking: por grupo de prioridad y nota.
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90")),
                delPerfil(422L, "Bruno Diaz", nota("80")),
                delPerfil(450L, "Carla Nunez", nota("70"))));

        // Y el pedido, como lo ordenó quien mira: al revés. Los dos órdenes DIFIEREN a
        // propósito; con los dos iguales esta prueba pasaría escribiendo el orden equivocado.
        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(450L, 427L, 422L),
                        "Ciudad: Lima · Nota ≥ 60")).contenido();

        assertThat(columna(libro, "Resumen", 1).subList(1, 4))
                .containsExactly("Carla Nunez", "Ana Quispe", "Bruno Diaz");
        // El «#» es el PUESTO DEL RANKING y viaja con su fila, igual que en la mesa: Carla
        // es la 3 del ranking aunque aquí salga la primera. Si dijera la posición de la
        // hoja, la misma descarga llamaría «#3» a quien la pantalla llama «#1».
        assertThat(columna(libro, "Resumen", 0).subList(1, 4)).containsExactly("3", "1", "2");
        // El Detalle es el segundo sitio donde el orden se revierte solo, si se recorre la
        // tanda en vez del pedido. Se comprueba aparte.
        assertThat(columna(libro, "Detalle", 0).subList(1, 4))
                .containsExactly("Carla Nunez", "Ana Quispe", "Bruno Diaz");
    }

    @Test
    @DisplayName("en la prueba del puesto el Detalle también respeta el orden pedido")
    void elDetalleDeLaPruebaTambienVaEnElOrdenPedido() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", nota("90")),
                deLaPrueba(450L, "Carla Nunez", nota("70"))));
        when(notasDeLaPrueba.verNotas(any(), eq(427L)))
                .thenReturn(List.of(deRubrica("Comprensión", 8.0, 10.0)));
        when(notasDeLaPrueba.verNotas(any(), eq(450L)))
                .thenReturn(List.of(deRubrica("Comprensión", 6.0, 10.0)));

        byte[] libro = servicio.generar(quien("ver_embudo", "abrir_ficha_candidato"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(450L, 427L), null)).contenido();

        assertThat(columna(libro, "Resumen", 1).subList(1, 3))
                .containsExactly("Carla Nunez", "Ana Quispe");
        assertThat(columna(libro, "Detalle", 0).subList(1, 3))
                .containsExactly("Carla Nunez", "Ana Quispe");
    }

    // ========================================================================
    // Qué etapas se pueden volcar y qué postulaciones entran
    // ========================================================================

    @Test
    @DisplayName("una etapa sin columnas no se vuelca, y el error dice cuáles valen")
    void laEtapaQueNoTieneColumnasNoSeVuelca() {
        assertThatThrownBy(() -> servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("SIMULACION", List.of(1L), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PERFIL_INTEGRAL")
                .hasMessageContaining("PRUEBA_PUESTO")
                .hasMessageContaining("SIMULACION");
    }

    @Test
    @DisplayName("sin lista de candidatos no se arma nada, y lo dice como regla y no como 500")
    void sinListaNoHayVolcado() {
        assertThatThrownBy(() -> servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ninguna postulación");
        verifyNoInteractions(tandas);
    }

    @Test
    @DisplayName("las postulaciones que no son de la vacante se quedan fuera y el pie las nombra")
    void lasAjenasSeQuedanFueraYElPieLasDice() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L, 9999L), null)).contenido();

        assertThat(columna(libro, "Resumen", 1).subList(1, 2)).containsExactly("Ana Quispe");
        assertThat(pie(libro)).anyMatch(t -> t.contains("Fuera del volcado") && t.contains("9999"));
    }

    @Test
    @DisplayName("si ninguna de las pedidas es de la vacante no sale una hoja vacía")
    void ningunaDeLaVacanteEsUnError() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));

        assertThatThrownBy(() -> servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(9999L), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vacante 13");
    }

    // ========================================================================
    // Una nota que falta
    // ========================================================================

    @Test
    @DisplayName("la nota vacía dice «rúbrica incompleta», ni en blanco ni cero")
    void laNotaVaciaLoDice() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", new NotaCriterioResponse(
                        "Experiencia", "CV_EXPERIENCIA", null, new BigDecimal("100"),
                        new BigDecimal("25"), "no se pudo puntuar", "AGENTE", null, null))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        // La nota de la etapa de esa fila viene nula, y el puntaje del criterio también.
        assertThat(celda(libro, "Resumen", 1, 6)).isEqualTo("rúbrica incompleta");
        assertThat(celda(libro, "Detalle", 1, 2)).isEqualTo("rúbrica incompleta");
    }

    // ========================================================================
    // El cuestionario técnico y el permiso de la rúbrica
    // ========================================================================

    @Test
    @DisplayName("el Resumen de la prueba cierra con el ponderado y su desglose")
    void elResumenDeLaPruebaTraeElPonderado() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", nota("90"))));
        when(notasDeLaPrueba.verNotas(any(), eq(427L)))
                .thenReturn(List.of(deRubrica("Comprensión", 9.0, 10.0)));

        byte[] libro = servicio.generar(quien("ver_embudo", "abrir_ficha_candidato"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        // Las tres van DESPUÉS de las columnas de siempre —la última era «Nota /100», la 6—,
        // y no intercaladas: así ninguna columna vieja se mueve de sitio.
        assertThat(celda(libro, "Resumen", 0, 7)).isEqualTo("Currículum /100");
        assertThat(celda(libro, "Resumen", 0, 8)).isEqualTo("Perfil integral /100");
        assertThat(celda(libro, "Resumen", 0, 9)).isEqualTo("Ponderado /100");
        assertThat(celda(libro, "Resumen", 1, 7)).isEqualTo("76.5");
        assertThat(celda(libro, "Resumen", 1, 8)).isEqualTo("82");
        assertThat(celda(libro, "Resumen", 1, 9)).isEqualTo("78.14");
    }

    /**
     * Sin ponderado la celda dice por qué, como cualquier otra nota de estas hojas.
     *
     * <p>En blanco se leería como un cero, y un cero es un juicio que aquí nadie ha emitido:
     * lo que pasa es que a esa persona le falta una de las dos etapas.
     */
    @Test
    @DisplayName("sin ponderado la celda no se queda en blanco")
    void sinPonderadoLaCeldaLoDice() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                sinPonderado(deLaPrueba(427L, "Ana Quispe", nota("90")))));
        when(notasDeLaPrueba.verNotas(any(), eq(427L)))
                .thenReturn(List.of(deRubrica("Comprensión", 9.0, 10.0)));

        byte[] libro = servicio.generar(quien("ver_embudo", "abrir_ficha_candidato"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        assertThat(celda(libro, "Resumen", 1, 9)).isEqualTo("rúbrica incompleta");
    }

    @Test
    @DisplayName("sin rúbrica —el cuestionario técnico— el Detalle lo dice en una fila")
    void sinRubricaElDetalleNoSeQuedaMudo() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", nota("90"))));
        // Es lo que devuelve verNotas cuando la vacante rinde CUESTIONARIO_TECNICO:
        // laRubricaDe corta con List.of() porque ese instrumento no se puntúa por criterios.
        when(notasDeLaPrueba.verNotas(any(), eq(427L))).thenReturn(List.of());

        byte[] libro = servicio.generar(quien("ver_embudo", "abrir_ficha_candidato"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        assertThat(celda(libro, "Detalle", 1, 0)).isEqualTo("Ana Quispe");
        assertThat(celda(libro, "Detalle", 1, 1))
                .contains("Sin rúbrica")
                .contains("cuestionario técnico");
    }

    @Test
    @DisplayName("quien todavía no tiene prueba no tumba el volcado de los demás")
    void elQueNoTienePruebaNoTumbaElVolcado() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", nota("90")),
                deLaPrueba(450L, "Carla Nunez", nota("70"))));
        // verNotas no devuelve vacío para quien no tiene intento de prueba: revienta con 404.
        when(notasDeLaPrueba.verNotas(any(), eq(427L))).thenThrow(
                new ResourceNotFoundException("Prueba del puesto", "postulación", 427L));
        when(notasDeLaPrueba.verNotas(any(), eq(450L)))
                .thenReturn(List.of(deRubrica("Comprensión", 6.0, 10.0)));

        byte[] libro = servicio.generar(quien("ver_embudo", "abrir_ficha_candidato"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L, 450L), null)).contenido();

        assertThat(celda(libro, "Detalle", 1, 1)).contains("Todavía no tiene prueba del puesto");
        // Y el siguiente sigue saliendo, en su sitio.
        assertThat(celda(libro, "Detalle", 2, 0)).isEqualTo("Carla Nunez");
        assertThat(celda(libro, "Detalle", 2, 1)).isEqualTo("Comprensión");
    }

    @Test
    @DisplayName("sin el permiso de la ficha el Detalle lo explica en vez de tumbar el archivo")
    void sinElPermisoDeLaRubricaElDetalleLoExplica() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", nota("90"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        /*
          Ni se le pregunta: verNotas exige «abrir_ficha_candidato» y lanzaría
          AccessDeniedException en el primer candidato, dejando sin Excel a quien sí puede
          ver el embudo.

          ⚠️ **Este permiso tiene que ser el mismo que pide verNotas.** Cuando eran
          distintos, este Excel negaba un detalle que la pantalla sí enseñaba.
        */
        verifyNoInteractions(notasDeLaPrueba);
        assertThat(celda(libro, "Detalle", 1, 1)).contains("abrir_ficha_candidato");
        // El Resumen sale entero igual.
        assertThat(celda(libro, "Resumen", 1, 1)).isEqualTo("Ana Quispe");
    }

    // ========================================================================
    // La pretensión, que viene bajo llave
    // ========================================================================

    @Test
    @DisplayName("sin «ver_pretension» la columna vacía dice por qué lo está")
    void laPretensionBajoLlaveSeExplica() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(celda(libro, "Resumen", 0, 5)).contains("sin permiso");
        assertThat(pie(libro)).anyMatch(t -> t.contains("ver_pretension")
                && t.contains("NO significa"));
    }

    @Test
    @DisplayName("con el permiso y nadie que la declare, el pie dice eso otro")
    void conPermisoYNadieQueLaDeclaraElPieLoDice() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));

        byte[] libro = servicio.generar(quien("ver_embudo", "ver_pretension"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(celda(libro, "Resumen", 0, 5)).isEqualTo("Pretensión");
        assertThat(pie(libro)).anyMatch(t -> t.contains("declaró pretensión salarial"));
    }

    @Test
    @DisplayName("la pretensión declarada se escribe con su moneda")
    void laPretensionDeclaradaSeEscribe() {
        FilaRanking conSueldo = conPretension(delPerfil(427L, "Ana Quispe", nota("90")),
                new BigDecimal("3000.00"), new BigDecimal("4500.00"), "PEN");
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(conSueldo));

        byte[] libro = servicio.generar(quien("ver_embudo", "ver_pretension"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        // Escrito como en la mesa: mismo símbolo y mismo separador de millares. La hoja se
        // reenvía fuera del panel y era el sitio donde peor se leía la misma cifra.
        assertThat(celda(libro, "Resumen", 1, 5)).isEqualTo("S/ 3,000 – 4,500");
        assertThat(pie(libro)).noneMatch(t -> t.contains("declaró pretensión salarial"));
    }

    /*
     * Sin moneda declarada van las cifras solas. NO se supone soles: alguien que pide 3.000
     * dólares y aparece leído como «S/ 3,000» es una llamada perdida. La misma regla que
     * `pretensionDicha` documenta en el panel, y ahora las dos la cumplen.
     */
    @Test
    @DisplayName("sin moneda no se supone soles: van las cifras solas")
    void sinMonedaNoSeSuponenSoles() {
        FilaRanking sinMoneda = conPretension(delPerfil(427L, "Ana Quispe", nota("90")),
                new BigDecimal("3000.00"), new BigDecimal("4500.00"), null);
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(sinMoneda));

        byte[] libro = servicio.generar(quien("ver_embudo", "ver_pretension"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(celda(libro, "Resumen", 1, 5)).isEqualTo("3,000 – 4,500");
    }

    @Test
    @DisplayName("una moneda que no se conoce viaja con su código, no se descarta")
    void monedaDesconocidaViajaConSuCodigo() {
        FilaRanking enEuros = conPretension(delPerfil(427L, "Ana Quispe", nota("90")),
                new BigDecimal("3000.00"), null, "EUR");
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(enEuros));

        byte[] libro = servicio.generar(quien("ver_embudo", "ver_pretension"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(celda(libro, "Resumen", 1, 5)).isEqualTo("desde EUR 3,000");
    }

    // ========================================================================
    // La forma del archivo
    // ========================================================================

    @Test
    @DisplayName("el nombre del archivo lleva la etapa, la vacante y la fecha")
    void elNombreDelArchivoLlevaLaFecha() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));

        ExcelDeRanking libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null));

        assertThat(libro.nombreArchivo())
                .isEqualTo("ranking-perfil-integral-vacante-13-2026-08-31.xlsx");
    }

    @Test
    @DisplayName("el pie del Resumen lleva el filtro recibido y la fecha")
    void elPieLlevaElFiltroYLaFecha() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L),
                        "Ciudad: Lima · Nota ≥ 60")).contenido();

        assertThat(pie(libro)).anyMatch(t -> t.contains("Ciudad: Lima · Nota ≥ 60")
                && t.contains("2026-08-31"));
    }

    @Test
    @DisplayName("la cabecera es índigo con letra blanca y la primera fila se queda fija")
    void laCabeceraYElPanelFijo() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));

        byte[] contenido = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        leyendo(contenido, libro -> {
            for (String nombre : List.of("Resumen", "Detalle")) {
                Sheet hoja = libro.getSheet(nombre);
                XSSFCellStyle estilo = (XSSFCellStyle) hoja.getRow(0).getCell(0).getCellStyle();
                assertThat(estilo.getFillForegroundColorColor().getARGBHex())
                        .endsWith("4338CA");
                assertThat(libro.getFontAt(estilo.getFontIndex()).getBold()).isTrue();
                PaneInformation panel = hoja.getPaneInformation();
                assertThat(panel).isNotNull();
                assertThat(panel.getHorizontalSplitPosition()).isEqualTo((short) 1);
                assertThat(hoja.getColumnWidth(1)).isGreaterThan(20 * 256);
            }
            return null;
        });
    }

    // ========================================================================
    // Andamio
    // ========================================================================

    private static ContextoUsuario quien(String... permisos) {
        Map<String, String> mapa = new LinkedHashMap<>();
        for (String permiso : permisos) {
            mapa.put(permiso, "TODO");
        }
        return new ContextoUsuario(7L, 3L, 1L, "EQUIPO", List.of(1L), mapa);
    }

    private static RankingVacante tanda(FilaRanking... filas) {
        return new RankingVacante(13L, "Analista de datos", "Analista", "JUNIOR",
                filas.length, filas.length, filas.length, 0, 0, true, List.of(filas));
    }

    private static NotaCriterioResponse nota(String puntaje) {
        return new NotaCriterioResponse("Experiencia", "CV_EXPERIENCIA", new BigDecimal(puntaje),
                new BigDecimal("100"), new BigDecimal("25"), "lo dice su currículum",
                "AGENTE", new BigDecimal("80"), null);
    }

    private static DtosCalificacionPrueba.NotaCriterioResponse deRubrica(
            String nombre, Double puntaje, Double maximo) {
        return new DtosCalificacionPrueba.NotaCriterioResponse(1L, nombre, maximo, puntaje,
                "lo explicó bien", "AGENTE");
    }

    /** Una fila del ranking del perfil: la nota de la etapa sale del criterio que se le pasa. */
    private static FilaRanking delPerfil(Long postulacionId, String candidato,
                                         NotaCriterioResponse suNota) {
        return fila(postulacionId, candidato, suNota.puntaje(), List.of(suNota));
    }

    /** Una fila de la prueba: sin notas del currículum, la nota es la de la etapa técnica. */
    private static FilaRanking deLaPrueba(Long postulacionId, String candidato,
                                          NotaCriterioResponse suNota) {
        return fila(postulacionId, candidato, suNota.puntaje(), List.of());
    }

    private static FilaRanking fila(Long postulacionId, String candidato, BigDecimal notaEtapa,
                                    List<NotaCriterioResponse> notas) {
        return new FilaRanking(puestoDe(postulacionId), postulacionId,
                UUID.randomUUID().toString(), candidato,
                "cuenta@cv-convocatoria.local", "PERFIL_POR_CONFIRMAR", "Perfil por confirmar",
                "TERMINADA", "FINA", "cv.pdf",
                new DatosCandidato(candidato, "ana@correo.pe", "999 888 777", null, null,
                        48, "Analista", "Empresa", "Universidad"),
                "A", notaEtapa, notaEtapa, new BigDecimal("70"), new BigDecimal("65"),
                new BigDecimal("60"), new BigDecimal("75"), "Un resumen corto", 0, 2, 1,
                Instant.parse("2026-08-30T10:00:00Z"), notas,
                "Lima — Lima", "1501", null, null, null,
                // Con valores distintos entre sí a propósito: cuatro cifras iguales dejarían
                // pasar que la hoja las escribiera en el orden equivocado.
                new Ponderado(new BigDecimal("78.14"), new BigDecimal("76.50"),
                        new BigDecimal("82.00"), new BigDecimal("73.00")));
    }

    /**
     * El puesto que ocuparía en el ranking, distinto por candidato.
     *
     * <p>Estaba fijo en 1 para todas las filas, y con el «#» siendo la posición de la hoja
     * eso no se notaba. Ahora el «#» ES el puesto, así que un fixture con tres unos haría
     * pasar el test escribiendo cualquier cosa.
     */
    private static int puestoDe(Long postulacionId) {
        return switch (postulacionId.intValue()) {
            case 427 -> 1;
            case 422 -> 2;
            case 450 -> 3;
            default -> 9;
        };
    }

    private static FilaRanking conPretension(FilaRanking f, BigDecimal min, BigDecimal max,
                                             String moneda) {
        return new FilaRanking(f.puesto(), f.postulacionId(), f.uuid(), f.candidato(), f.correo(),
                f.estado(), f.estadoNombre(), f.estadoCalificacion(), f.pasada(), f.archivoNombre(),
                f.datos(), f.grupoPrioridad(), f.notaEtapa(), f.notaCurriculum(), f.adecuacion(),
                f.potencial(), f.altoRendimiento(), f.confianzaEvidencia(), f.resumen(),
                f.riesgosCriticos(), f.fortalezas(), f.alertas(), f.actualizadoEn(),
                f.notasCriterio(), f.ciudad(), f.ciudadCodigo(), min, max, moneda,
                f.ponderado());
    }

    // ---- Leer el libro que se acaba de escribir ----

    private static <T> T leyendo(byte[] contenido, Function<XSSFWorkbook, T> lector) {
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
            return lector.apply(libro);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Todas las celdas de una hoja como texto, con el formato con que se verían. */
    private static List<List<String>> celdas(byte[] contenido, String nombre) {
        return leyendo(contenido, libro -> {
            DataFormatter formateador = new DataFormatter(Locale.US);
            Sheet hoja = libro.getSheet(nombre);
            List<List<String>> filas = new ArrayList<>();
            for (int i = 0; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                List<String> celdas = new ArrayList<>();
                if (fila != null) {
                    for (int c = 0; c < fila.getLastCellNum(); c++) {
                        celdas.add(formateador.formatCellValue(fila.getCell(c)));
                    }
                }
                filas.add(celdas);
            }
            return filas;
        });
    }

    private static String celda(byte[] contenido, String hoja, int fila, int columna) {
        List<String> suya = celdas(contenido, hoja).get(fila);
        return columna < suya.size() ? suya.get(columna) : "";
    }

    private static List<String> columna(byte[] contenido, String hoja, int columna) {
        return celdas(contenido, hoja).stream()
                .map(fila -> columna < fila.size() ? fila.get(columna) : "")
                .toList();
    }

    /** Las líneas sueltas del final del Resumen: filtro, fecha y avisos. */
    private static List<String> pie(byte[] contenido) {
        return columna(contenido, "Resumen", 0).stream().filter(t -> t.length() > 12).toList();
    }

    /*
     * Un cero en «Riesgos» es una conclusión; una casilla vacía es la falta de una. Cuando
     * la IA no ha hecho el retrato, las tres cuentas llegan en 0 porque son int —no porque
     * nadie le encontrara riesgos—, y volcarlas escribiría en una hoja un juicio que nadie
     * ha emitido. Se comprueba con el formateador, que es lo que vería quien la abre.
     */
    @Test
    @DisplayName("sin retrato del CV, las cuentas van en blanco y no en cero")
    void sinRetratoLasCuentasVanEnBlanco() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL")))
                .thenReturn(tanda(sinRetrato(delPerfil(427L, "Ana Quispe", nota("90")))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), "Toda la tanda"))
                .contenido();

        assertThat(celda(libro, "Resumen", 1, 13)).isEmpty();   // Riesgos
        assertThat(celda(libro, "Resumen", 1, 14)).isEmpty();   // Alertas
        assertThat(celda(libro, "Resumen", 1, 16)).isEmpty();   // Fortalezas
    }

    @Test
    @DisplayName("con retrato, las cuentas sí se escriben aunque alguna sea cero")
    void conRetratoElCeroSiEsUnaConclusion() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL")))
                .thenReturn(tanda(delPerfil(427L, "Ana Quispe", nota("90"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), "Toda la tanda"))
                .contenido();

        // La fila de `delPerfil` trae 0 riesgos, 2 fortalezas y 1 alerta, y con retrato
        // ese 0 SÍ significa «no se le encontró ninguno»: se escribe.
        assertThat(celda(libro, "Resumen", 1, 13)).isEqualTo("0");
        assertThat(celda(libro, "Resumen", 1, 14)).isEqualTo("1");
        assertThat(celda(libro, "Resumen", 1, 16)).isEqualTo("2");
    }

    /** La misma fila para quien todavía no tiene las dos notas que se mezclan. */
    private static FilaRanking sinPonderado(FilaRanking f) {
        return new FilaRanking(f.puesto(), f.postulacionId(), f.uuid(), f.candidato(), f.correo(),
                f.estado(), f.estadoNombre(), f.estadoCalificacion(), f.pasada(), f.archivoNombre(),
                f.datos(), f.grupoPrioridad(), f.notaEtapa(), f.notaCurriculum(), f.adecuacion(),
                f.potencial(), f.altoRendimiento(), f.confianzaEvidencia(), f.resumen(),
                f.riesgosCriticos(), f.fortalezas(), f.alertas(), f.actualizadoEn(),
                f.notasCriterio(), f.ciudad(), f.ciudadCodigo(), f.pretensionMin(),
                f.pretensionMax(), f.pretensionMoneda(), null);
    }

    /** La misma fila, pero como la deja el ranking cuando la IA aún no la ha mirado. */
    private static FilaRanking sinRetrato(FilaRanking f) {
        return new FilaRanking(f.puesto(), f.postulacionId(), f.uuid(), f.candidato(), f.correo(),
                f.estado(), f.estadoNombre(), f.estadoCalificacion(), f.pasada(), f.archivoNombre(),
                f.datos(), f.grupoPrioridad(), f.notaEtapa(), f.notaCurriculum(), null,
                null, f.altoRendimiento(), f.confianzaEvidencia(), null,
                0, 0, 0, f.actualizadoEn(),
                f.notasCriterio(), f.ciudad(), f.ciudadCodigo(), f.pretensionMin(),
                // El ponderado NO es parte del retrato: sale de las notas de etapa, no de lo
                // que la IA dibuja del currículum, y sigue estando cuando el retrato falta.
                f.pretensionMax(), f.pretensionMoneda(), f.ponderado());
    }
}
