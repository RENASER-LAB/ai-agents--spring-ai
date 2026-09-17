package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosExcelRanking.ExcelDeRanking;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosExcelRanking.PedidoExcelRanking;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.DatosCandidato;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.FilaRanking;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.Ponderado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.NotaCriterioResponse;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.RankingVacante;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioPerfilIntegralPanel;
import com.renaser.ai.ai_engine.postulacion.dto.DtosPostulacion.EnlaceArchivo;
import com.renaser.ai.ai_engine.postulacion.service.ServicioPostulacionesPanel;
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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *       cero es un juicio que nadie hizo. Pero <b>una celda de criterio vacía sí se deja
 *       vacía</b>: en una columna por criterio el hueco ya es el mensaje.
 *   <li><b>Las columnas de criterio.</b> Salen de la rúbrica de ESA vacante, así que dos
 *       vacantes producen archivos con distinta forma. Una lista fija aquí haría pasar el test
 *       con un archivo que no se parece a la tabla.
 *   <li><b>El enlace del CV.</b> Caduca y no vuelve a preguntar nada. La hoja tiene que
 *       decirlo, y sin el permiso no se firma ni uno.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El ranking volcado a Excel")
class ServicioExcelRankingImplTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-08-31T14:00:00Z"), ZoneOffset.UTC);

    /** La única hoja. Escrito aquí para que renombrarla rompa las pruebas de una vez. */
    private static final String DATOS = "Datos";

    // Las columnas comunes a las dos etapas.
    private static final int NUMERO = 0;
    private static final int CANDIDATO = 1;
    private static final int CORREO = 2;
    private static final int CV = 3;
    private static final int TELEFONO = 4;

    @Mock
    private ServicioPerfilIntegralPanel tandas;
    @Mock
    private ServicioPostulacionesPanel archivos;

    private ServicioExcelRankingImpl servicio;

    @BeforeEach
    void armar() {
        servicio = new ServicioExcelRankingImpl(tandas, archivos, RELOJ);
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

        assertThat(columna(libro, CANDIDATO).subList(1, 4))
                .containsExactly("Carla Nunez", "Ana Quispe", "Bruno Diaz");
        // El «#» es el PUESTO DEL RANKING y viaja con su fila, igual que en la mesa: Carla
        // es la 3 del ranking aunque aquí salga la primera. Si dijera la posición de la
        // hoja, la misma descarga llamaría «#3» a quien la pantalla llama «#1».
        assertThat(columna(libro, NUMERO).subList(1, 4)).containsExactly("3", "1", "2");
    }

    // ========================================================================
    // Una hoja, y se llama Datos
    // ========================================================================

    @Test
    @DisplayName("el libro tiene UNA hoja, llamada Datos: ni Resumen, ni Detalle, ni Metodología")
    void unaSolaHojaLlamadaDatos() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", rubrica("Divisas", "12", "15"))));

        byte[] contenido = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        leyendo(contenido, libro -> {
            assertThat(libro.getNumberOfSheets()).isEqualTo(1);
            assertThat(libro.getSheetAt(0).getSheetName()).isEqualTo(DATOS);
            assertThat(libro.getSheet("Resumen")).isNull();
            assertThat(libro.getSheet("Detalle")).isNull();
            assertThat(libro.getSheet("Metodología")).isNull();
            return null;
        });
    }

    // ========================================================================
    // Las etapas que no se vuelcan
    // ========================================================================

    @Test
    @DisplayName("una etapa sin columnas se rechaza con su nombre dentro")
    void laEtapaQueNoTieneColumnasNoSeVuelca() {
        assertThatThrownBy(() -> servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("SIMULACION", List.of(427L), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SIMULACION");
        verifyNoInteractions(tandas);
    }

    @Test
    @DisplayName("sin lista de candidatos no se vuelca nada")
    void sinListaNoHayVolcado() {
        assertThatThrownBy(() -> servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(), null)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(tandas);
    }

    @Test
    @DisplayName("las postulaciones ajenas se quedan fuera y el pie las cuenta")
    void lasAjenasSeQuedanFueraYElPieLasDice() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L, 999L), null)).contenido();

        assertThat(columna(libro, CANDIDATO).subList(1, 2)).containsExactly("Ana Quispe");
        assertThat(pie(libro)).anyMatch(t -> t.contains("Fuera del volcado") && t.contains("999"));
    }

    @Test
    @DisplayName("si ninguna es de la vacante, es un error y no un archivo vacío")
    void ningunaDeLaVacanteEsUnError() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda());

        assertThatThrownBy(() -> servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(999L), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("13");
    }

    // ========================================================================
    // Las notas
    // ========================================================================

    @Test
    @DisplayName("una nota de etapa que falta lo dice; no se queda en blanco")
    void laNotaVaciaLoDice() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                sinNotaDeEtapa(delPerfil(427L, "Ana Quispe", nota("90")))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(celda(libro, 1, 5)).isEqualTo("rúbrica incompleta");
    }

    @Test
    @DisplayName("en la prueba van las tres notas: técnica, perfil integral y combinada")
    void laPruebaTraeLasTresNotas() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", rubrica("Divisas", "12", "15"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        // 5 técnica · 6 perfil integral · 7 el criterio · 8 combinada
        assertThat(celda(libro, 0, 5)).isEqualTo("Nota Examen Técnico /100");
        assertThat(celda(libro, 0, 6)).isEqualTo("Nota Perfil Integral /100");
        assertThat(celda(libro, 0, 8)).isEqualTo("Nota Combinada /100");
        assertThat(celda(libro, 1, 5)).isEqualTo("73");     // la nota de la etapa técnica
        assertThat(celda(libro, 1, 6)).isEqualTo("82");     // ponderado.perfil()
        assertThat(celda(libro, 1, 8)).isEqualTo("78.14");  // ponderado.sobre100()
    }

    /*
     * La cabecera dice «/100» y no «/40» como la plantilla del cliente, y es a propósito: sus
     * propias celdas llevaban `=0.55*F+0.45*G`, que da una cifra sobre 100, y su hoja de
     * Metodología hablaba de un 30 % y un 12 % que no son esos pesos. De las tres versiones,
     * la que coincide con `peso_etapa` —45 el perfil integral, 55 la prueba— es la fórmula.
     */
    @Test
    @DisplayName("la nota combinada se rotula sobre 100, que es lo que el sistema calcula")
    void laCombinadaVaSobreCien() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", rubrica("Divisas", "12", "15"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        // Contra la cabecera ENTERA: «no contiene /40» sobre una columna suelta pasaba
        // igual si la combinada se había desplazado y ahí había un criterio.
        assertThat(cabeceras(libro)).contains("Nota Combinada /100")
                .noneMatch(c -> c.contains("/40"));
    }

    @Test
    @DisplayName("sin las dos notas de etapa, la combinada dice qué falta")
    void sinPonderadoLaCeldaLoDice() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                sinPonderado(deLaPrueba(427L, "Ana Quispe", rubrica("Divisas", "12", "15")))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        assertThat(celda(libro, 1, 8)).isEqualTo("falta una nota de etapa");
        // Y la nota de la etapa que SÍ tiene se sigue escribiendo: no se saca del ponderado.
        assertThat(celda(libro, 1, 5)).isEqualTo("73");
    }

    /*
     * En la pestaña del perfil la mitad de la cuenta —la prueba— todavía no puede existir
     * para nadie: una columna combinada ahí se llenaría de huecos para la etapa más temprana
     * del embudo. Es la misma decisión que toma la tabla del panel.
     */
    @Test
    @DisplayName("el perfil integral no lleva ni nota técnica ni combinada")
    void elPerfilNoLlevaLaCombinada() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(celdas(libro).get(0)).doesNotContain("Nota Combinada /100")
                .doesNotContain("Nota Examen Técnico /100")
                .contains("Nota Perfil Integral /100");
    }

    // ========================================================================
    // Los criterios, una columna cada uno
    // ========================================================================

    @Test
    @DisplayName("cada criterio de la rúbrica es una columna, con su techo en la cabecera")
    void cadaCriterioEsUnaColumnaConSuTecho() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe",
                        rubrica("Conocimiento del negocio de divisas", "14", "15"),
                        rubrica("Control de caja", "19", "20"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        // Es la forma exacta de la plantilla del cliente: «Divisas (pts /15)».
        assertThat(celda(libro, 0, 7)).isEqualTo("Conocimiento del negocio de divisas (pts /15)");
        assertThat(celda(libro, 0, 8)).isEqualTo("Control de caja (pts /20)");
        assertThat(celda(libro, 1, 7)).isEqualTo("14");
        assertThat(celda(libro, 1, 8)).isEqualTo("19");
    }

    /*
     * Las columnas salen de las FILAS y no de un catálogo: en la prueba del puesto la rúbrica
     * es la de esa vacante. Con un candidato que no rindió un criterio, la columna tiene que
     * seguir existiendo —la trae el otro— y su celda quedarse vacía.
     */
    @Test
    @DisplayName("quien no tiene un criterio deja esa celda VACÍA, no «rúbrica incompleta»")
    void elCriterioQueFaltaSeQuedaEnBlanco() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe",
                        rubrica("Divisas", "14", "15"), rubrica("Control de caja", "19", "20")),
                deLaPrueba(422L, "Bruno Diaz", rubrica("Divisas", "8", "15"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L, 422L), null)).contenido();

        assertThat(celda(libro, 2, 7)).isEqualTo("8");
        assertThat(celda(libro, 2, 8)).isEmpty();
    }

    @Test
    @DisplayName("quien no rindió la prueba no pierde su fila: sale sin columnas de criterio")
    void sinRubricaLaFilaSigueSaliendo() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", rubrica("Divisas", "14", "15")),
                sinCriterios(deLaPrueba(422L, "Bruno Diaz"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L, 422L), null)).contenido();

        assertThat(columna(libro, CANDIDATO).subList(1, 3))
                .containsExactly("Ana Quispe", "Bruno Diaz");
        assertThat(celda(libro, 2, 7)).isEmpty();
    }

    /*
     * ⚠️ **Una tanda puede mezclar dos versiones de la misma plantilla**, y el mismo criterio
     * venir sobre 15 en unas filas y sobre 20 en otras. Juntarlos en una columna obliga a
     * elegir un techo para la cabecera, y entonces la mitad de los puntajes se leen contra un
     * máximo que no es el suyo: un 14 sobre 15 bajo «/20» es un 70 % donde hubo un 93 %. No es
     * una columna fea, es un número falso. Por eso el techo forma parte de la clave.
     */
    @Test
    @DisplayName("el mismo criterio con dos techos son DOS columnas, cada una con el suyo")
    void dosTechosSonDosColumnas() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", rubrica("Divisas", "14", "15")),
                deLaPrueba(422L, "Bruno Diaz", rubrica("Divisas", "18", "20"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L, 422L), null)).contenido();

        assertThat(celda(libro, 0, 7)).isEqualTo("Divisas (pts /15)");
        assertThat(celda(libro, 0, 8)).isEqualTo("Divisas (pts /20)");
        // Y cada puntaje cae bajo SU techo, no bajo el del otro.
        assertThat(celda(libro, 1, 7)).isEqualTo("14");
        assertThat(celda(libro, 1, 8)).isEmpty();
        assertThat(celda(libro, 2, 7)).isEmpty();
        assertThat(celda(libro, 2, 8)).isEqualTo("18");
    }

    /*
     * «Divisas», «divisas» y «Divisas » son el mismo criterio escrito de tres formas. Como
     * claves distintas producían tres columnas que en la hoja son indistinguibles a ojo —la
     * tercera lleva un doble espacio que nadie ve—.
     */
    @Test
    @DisplayName("mayúsculas y espacios no parten un criterio en varias columnas")
    void elNombreSeNormaliza() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", rubrica("Divisas", "14", "15")),
                deLaPrueba(422L, "Bruno Diaz", rubrica("  divisas ", "9", "15"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L, 422L), null)).contenido();

        assertThat(cabeceras(libro)).containsOnlyOnce("Divisas (pts /15)");
        assertThat(celda(libro, 2, 7)).isEqualTo("9");
    }

    /*
     * Descartar un criterio sin nombre le quitaba la columna a un puntaje que sí existe: la
     * nota desaparecía de la rejilla y solo asomaba en la justificación. Un criterio mal
     * nombrado es un problema de la plantilla; perder su nota es un problema de esta hoja.
     */
    @Test
    @DisplayName("un criterio sin nombre conserva su columna, con un rótulo de reserva")
    void elCriterioSinNombreNoPierdeSuColumna() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", rubrica("   ", "1", "5"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        assertThat(celda(libro, 0, 7)).isEqualTo("(criterio sin nombre) (pts /5)");
        assertThat(celda(libro, 1, 7)).isEqualTo("1");
    }

    /*
     * ⚠️ **Las columnas salen de la TANDA ENTERA y las filas del recorte pedido.** Con las
     * columnas sacadas del recorte, el orden de las columnas lo decidía el orden de las FILAS:
     * la misma tanda descargada dos veces con distinta ordenación salía con las columnas
     * cambiadas de sitio. Y la tabla del panel arma las suyas con las filas SIN filtrar, así
     * que el archivo dejaba de parecerse a la pantalla en cuanto un filtro escondía a la única
     * persona con un criterio puntuado.
     */
    @Test
    @DisplayName("las columnas no cambian de sitio al pedir las filas en otro orden")
    void elOrdenDeLasColumnasNoDependeDelDeLasFilas() {
        RankingVacante laTanda = tanda(
                deLaPrueba(427L, "Ana Quispe", rubrica("Divisas", "14", "15")),
                deLaPrueba(422L, "Bruno Diaz", rubrica("Caja", "18", "20")));
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(laTanda);

        byte[] enUnOrden = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L, 422L), null)).contenido();
        byte[] enElOtro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(422L, 427L), null)).contenido();

        assertThat(cabeceras(enUnOrden)).isEqualTo(cabeceras(enElOtro));
    }

    @Test
    @DisplayName("un filtro que deja fuera a alguien no le quita su columna a la hoja")
    void laColumnaSobreviveAlFiltro() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", rubrica("Divisas", "14", "15")),
                deLaPrueba(422L, "Bruno Diaz", rubrica("Caja", "18", "20"))));

        // Solo se pide a Ana; Bruno, el único con «Caja», se queda fuera del recorte.
        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        assertThat(cabeceras(libro)).contains("Caja (pts /20)");
        assertThat(columna(libro, CANDIDATO).subList(1, 2)).containsExactly("Ana Quispe");
    }

    // ========================================================================
    // Las dos justificaciones
    // ========================================================================

    @Test
    @DisplayName("la resumida es el retrato de la IA y la detallada une las explicaciones")
    void lasDosJustificaciones() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe",
                        rubrica("Divisas", "14", "15"), rubrica("Control de caja", "19", "20"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        // Dos criterios: 7 y 8 son suyos, 9 la combinada, 10 y 11 las justificaciones.
        assertThat(celda(libro, 0, 10)).isEqualTo("Justificación resumida");
        assertThat(celda(libro, 0, 11)).isEqualTo("Justificación detallada");
        assertThat(celda(libro, 1, 10)).isEqualTo("Un resumen corto");
        assertThat(celda(libro, 1, 11))
                .contains("Divisas (14/15): lo explicó bien")
                .contains("Control de caja (19/20): lo explicó bien");
    }

    /*
     * El motivo del ajuste va pegado a SU criterio y no aparte: solo significa algo junto a
     * la nota que corrigió. En la hoja de Detalle tenía columna propia; aquí, línea propia.
     */
    @Test
    @DisplayName("una nota corregida a mano lo dice junto a su criterio")
    void laNotaCorregidaLoDiceJuntoASuCriterio() {
        NotaCriterioResponse corregida = new NotaCriterioResponse("Divisas", "DIV",
                new BigDecimal("14"), new BigDecimal("15"), new BigDecimal("15"),
                "lo explicó bien", "AGENTE", new BigDecimal("80"),
                "la entrevista lo confirmó");
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", corregida)));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        assertThat(celda(libro, 1, 10)).contains("Corregida a mano: la entrevista lo confirmó");
    }

    @Test
    @DisplayName("sin rúbrica la detallada se queda vacía y no inventa una frase")
    void sinRubricaLaDetalladaVaVacia() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                sinCriterios(deLaPrueba(427L, "Ana Quispe"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        // Contra la cabecera: un `isEmpty()` suelto sobre el índice 9 pasaría igual si la
        // hoja hubiese perdido una columna y ahí no hubiera nada que leer.
        assertThat(cabeceras(libro)).hasSize(10).last().isEqualTo("Justificación detallada");
        assertThat(celda(libro, 1, 9)).isEmpty();
    }

    // ========================================================================
    // La columna CV: un enlace que caduca y no vuelve a preguntar nada
    // ========================================================================

    @Test
    @DisplayName("con el permiso, el CV lleva el enlace firmado detrás del nombre del archivo")
    void elCvLlevaElEnlaceFirmado() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));
        when(archivos.enlaceDeVolcado(any(), eq(4270L))).thenReturn(Optional.of(
                new EnlaceArchivo("https://almacen.example/firmado?token=abc",
                        Instant.parse("2026-08-31T22:00:00Z"), "cv.pdf")));

        byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        // El texto es el NOMBRE, no la URL: doscientos caracteres de ruido en una columna
        // que se lee de un vistazo, y el nombre sirve cuando el enlace ya caducó.
        assertThat(celda(contenido, 1, CV)).isEqualTo("cv.pdf");
        assertThat(enlaceDe(contenido, 1, CV)).isEqualTo("https://almacen.example/firmado?token=abc");
    }

    /*
     * ⚠️ **Un almacén puede devolver una url que ningún navegador abre.** El de memoria —el
     * del perfil local— firma `memoria://…`, que está perfectamente formada y no lleva a
     * ninguna parte. Sin mirar el esquema, la hoja salía con enlaces azules muertos y un pie
     * prometiendo que «abren el currículum sin pedir sesión»: las dos cosas falsas a la vez, y
     * ninguna se nota hasta que alguien pulsa. Se vio al descargar el archivo de verdad contra
     * el backend en local, no en ninguna prueba.
     */
    @Test
    @DisplayName("una url que el navegador no sabe abrir no se escribe como enlace")
    void unaUrlQueNoAbreNoSeEscribe() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));
        when(archivos.enlaceDeVolcado(any(), eq(4270L))).thenReturn(Optional.of(
                new EnlaceArchivo("memoria://1/abc.pdf",
                        Instant.parse("2026-08-31T21:59:05Z"), "cv.pdf")));

        byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(celda(contenido, 1, CV)).isEqualTo("cv.pdf");
        assertThat(enlaceDe(contenido, 1, CV)).isNull();
        // Y el pie cuenta lo que pasa: no hay enlaces, en vez de prometer unos que no abren.
        assertThat(pie(contenido))
                .anyMatch(t -> t.contains("Ninguno de los 1 currículums pudo firmarse"))
                .noneMatch(t -> t.contains("SIN pedir sesión"));
    }

    @Test
    @DisplayName("sin «descargar_entregables» no se firma ni un enlace, y el pie lo explica")
    void sinPermisoNoSeFirmaNada() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));

        byte[] contenido = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        // El nombre sí va: es lo que permite dar con el archivo en la carpeta donde vive.
        assertThat(celda(contenido, 1, CV)).isEqualTo("cv.pdf");
        assertThat(enlaceDe(contenido, 1, CV)).isNull();
        assertThat(pie(contenido)).anyMatch(t -> t.contains("descargar_entregables"));
        // Y no se le pregunta al almacén ni una vez: preguntar ochenta veces para recibir
        // ochenta negativas es el error que esta comprobación existe para cazar.
        verifyNoInteractions(archivos);
    }

    /*
     * Un archivo borrado, un almacén que no firma —el de disco no— o un alcance de permiso
     * más estrecho que el de esta pantalla son tres motivos para lo mismo, y ninguno
     * justifica dejar sin hoja a las otras filas.
     */
    @Test
    @DisplayName("un enlace que no se puede firmar no tumba el volcado, y el pie lo cuenta")
    void unEnlaceQueFallaNoTumbaLaHoja() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90")),
                delPerfil(422L, "Bruno Diaz", nota("80"))));
        when(archivos.enlaceDeVolcado(any(), eq(4270L))).thenReturn(Optional.of(
                new EnlaceArchivo("https://almacen.example/firmado?token=abc",
                        Instant.parse("2026-08-31T22:00:00Z"), "cv.pdf")));
        when(archivos.enlaceDeVolcado(any(), eq(4220L)))
                .thenThrow(new IllegalStateException("el almacén de disco no reparte enlaces"));

        byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L, 422L), null)).contenido();

        assertThat(enlaceDe(contenido, 1, CV)).isNotNull();
        assertThat(enlaceDe(contenido, 2, CV)).isNull();
        assertThat(celda(contenido, 2, CV)).isEqualTo("cv.pdf");
        assertThat(pie(contenido)).anyMatch(t -> t.contains("1 de los 2 currículums"));
    }

    /*
     * ⚠️ La rama del almacén caído: hay permiso y hay currículums, pero no se firmó ni uno.
     * Es justo cuando alguien lee el pie de verdad, y era la única frase del archivo que
     * ninguna prueba había visto nunca.
     */
    @Test
    @DisplayName("con el almacén caído el pie dice que no hay ningún enlace")
    void conElAlmacenCaidoElPieLoDice() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90")),
                delPerfil(422L, "Bruno Diaz", nota("80"))));
        when(archivos.enlaceDeVolcado(any(), eq(4270L)))
                .thenThrow(new IllegalStateException("el almacén no responde"));
        when(archivos.enlaceDeVolcado(any(), eq(4220L)))
                .thenThrow(new IllegalStateException("el almacén no responde"));

        byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L, 422L), null)).contenido();

        assertThat(pie(contenido))
                .anyMatch(t -> t.contains("Ninguno de los 2 currículums pudo firmarse"))
                // Y no se cuela el aviso de los enlaces, que aquí no describiría nada.
                .noneMatch(t -> t.contains("SIN pedir sesión"));
        // Los nombres sí van escritos: es lo que queda para dar con el archivo.
        assertThat(celda(contenido, 1, CV)).isEqualTo("cv.pdf");
    }

    @Test
    @DisplayName("el pie avisa de que el enlace abre sin sesión, y dice cuánto dura")
    void elPieAvisaDeLoQueCuestaElEnlace() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));
        /*
          ⚠️ **La caducidad NO es una hora exacta, y esa es la gracia del caso.** En producción
          nunca lo es: el almacén la sella al responder la firma y la cuenta se hace después de
          escribir la hoja, así que ocho horas configuradas llegan como 7 h 59 min y pico. Con
          una caducidad redonda —22:00 en punto contra un reloj fijo en las 14:00— este test
          pasaba aunque el código truncara, que es justo lo que hacía: decía «7 horas» en todas
          las descargas de verdad. El reloj marca las 14:00 y esto muere a las 21:59:05.
        */
        when(archivos.enlaceDeVolcado(any(), eq(4270L))).thenReturn(Optional.of(
                new EnlaceArchivo("https://almacen.example/firmado?token=abc",
                        Instant.parse("2026-08-31T21:59:05Z"), "cv.pdf")));

        byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(pie(contenido)).anyMatch(t -> t.contains("SIN pedir sesión")
                && t.contains("8 horas"))
                .noneMatch(t -> t.contains("7 horas"));
    }

    /*
     * ⚠️ El plazo sale del PROPIO enlace y no de un número escrito en el código. Con un «8
     * horas» literal, bajar `horas-enlace-volcado` a dos dejaba la hoja prometiendo un plazo
     * que ya no era el suyo, y ninguna prueba se enteraba porque también tenían el ocho a
     * mano. Por eso este caso firma para dos horas y exige que la frase cambie.
     */
    @Test
    @DisplayName("si el enlace dura menos, la hoja dice ese plazo y no el de siempre")
    void elPlazoSaleDelEnlaceYNoDeUnLiteral() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));
        // Dos horas menos unos segundos, como llegaría de verdad.
        when(archivos.enlaceDeVolcado(any(), eq(4270L))).thenReturn(Optional.of(
                new EnlaceArchivo("https://almacen.example/firmado?token=abc",
                        Instant.parse("2026-08-31T15:59:12Z"), "cv.pdf")));

        byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(pie(contenido)).anyMatch(t -> t.contains("2 horas"))
                .noneMatch(t -> t.contains("8 horas") || t.contains("1 hora"));
    }

    @Test
    @DisplayName("una hora entera se dice «una hora», no «unas 1 hora»")
    void unaHoraSeDiceComoUnaHora() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));
        when(archivos.enlaceDeVolcado(any(), eq(4270L))).thenReturn(Optional.of(
                new EnlaceArchivo("https://almacen.example/firmado?token=abc",
                        Instant.parse("2026-08-31T15:00:00Z"), "cv.pdf")));

        byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(pie(contenido)).anyMatch(t -> t.contains("una hora después de generado"))
                .noneMatch(t -> t.contains("unas 1 hora"));
    }

    /*
     * ⚠️ **Por debajo de la hora el plazo se TRUNCA, y es a propósito.** La holgura existe
     * para recuperar un número redondo que se configuró en horas —ocho horas llegan aquí como
     * 7 h 59 min y pico, y sin ella se anunciaban como siete—. Por debajo de la hora no hay
     * ningún número redondo que recuperar, y lo que importa es no exagerar: con 59 min 59 s
     * por delante, «a los 59 minutos» es la verdad. Aplicarle holgura a este tramo fue lo que
     * hizo que minuto y medio se anunciara como «a los 2 minutos».
     */
    @Test
    @DisplayName("por debajo de la hora el plazo se trunca y no se infla")
    void pordebajoDeLaHoraElPlazoSeTrunca() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));
        when(archivos.enlaceDeVolcado(any(), eq(4270L))).thenReturn(Optional.of(
                new EnlaceArchivo("https://almacen.example/firmado?token=abc",
                        Instant.parse("2026-08-31T14:59:59Z"), "cv.pdf")));

        byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(pie(contenido)).anyMatch(t -> t.contains("a los 59 minutos"))
                .noneMatch(t -> t.contains("una hora"));
    }

    /*
     * Pasa con el reloj torcido o con una firma más lenta que su propia vida. Decir «dentro
     * de un minuto» sobre enlaces que ya no abren es la peor salida posible: quien lea la hoja
     * culpará al enlace de su propio retraso en vez de volver a descargarla.
     */
    @Test
    @DisplayName("un enlace que nace caducado lo dice, no promete un minuto")
    void unEnlaceQueNaceCaducadoLoDice() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));
        when(archivos.enlaceDeVolcado(any(), eq(4270L))).thenReturn(Optional.of(
                new EnlaceArchivo("https://almacen.example/firmado?token=abc",
                        Instant.parse("2026-08-31T13:58:00Z"), "cv.pdf")));

        byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(pie(contenido)).anyMatch(t -> t.contains("nacieron caducados")
                        && t.contains("Vuelve a descargar la hoja"))
                .noneMatch(t -> t.contains("dentro de un minuto")
                        // La coletilla del aviso normal aquí se contradice: no hay plazo
                        // dentro del cual nadie pueda abrir nada.
                        || t.contains("dentro de ese plazo puede abrirlos"));
    }

    /*
     * Con varios enlaces manda el que muere antes: es el primero que deja de abrir, y un
     * aviso de caducidad que anuncia el más largo deja media hoja muerta sin avisar.
     */
    @Test
    @DisplayName("con varias caducidades el pie anuncia la más corta")
    void mandaElPlazoMasCorto() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90")),
                delPerfil(422L, "Bruno Diaz", nota("80"))));
        when(archivos.enlaceDeVolcado(any(), eq(4270L))).thenReturn(Optional.of(
                new EnlaceArchivo("https://almacen.example/a",
                        Instant.parse("2026-08-31T21:59:05Z"), "cv.pdf")));
        when(archivos.enlaceDeVolcado(any(), eq(4220L))).thenReturn(Optional.of(
                new EnlaceArchivo("https://almacen.example/b",
                        Instant.parse("2026-08-31T16:59:05Z"), "cv.pdf")));

        byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L, 422L), null)).contenido();

        assertThat(pie(contenido)).anyMatch(t -> t.contains("unas 3 horas"))
                .noneMatch(t -> t.contains("8 horas"));
    }

    @Test
    @DisplayName("un enlace de menos de una hora se dice en minutos")
    void elPlazoCortoSeDiceEnMinutos() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));
        // 44 min 59 s, no 45 clavados: con una cifra redonda no se distinguiría truncar de
        // inflar, y este tramo trunca a propósito — «a los 44 minutos» se queda corto, que es
        // el lado por el que un aviso de caducidad puede equivocarse sin hacer daño.
        when(archivos.enlaceDeVolcado(any(), eq(4270L))).thenReturn(Optional.of(
                new EnlaceArchivo("https://almacen.example/firmado?token=abc",
                        Instant.parse("2026-08-31T14:44:59Z"), "cv.pdf")));

        byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(pie(contenido)).anyMatch(t -> t.contains("a los 44 minutos"))
                .noneMatch(t -> t.contains("45 minutos"));
    }

    /*
     * ⚠️ **Un aviso de caducidad nunca puede prometer más tiempo del que hay.** Redondear al
     * más cercano se pasaba por arriba hasta media hora: hora y media se anunciaba como «unas
     * 2 horas», y quien lo leyera descubriría que el enlace no abre cuando ya contaba con él.
     * Por eso se redondea hacia abajo, con la holgura justa para no volver a perder una hora
     * entera por los segundos que tarda la descarga en generarse.
     */
    /*
     * ⚠️ **La invariante, comprobada BARRIENDO y no con un caso suelto.** Un aviso de
     * caducidad que promete más tiempo del que hay es el único que hace daño: quien lo lea
     * descubrirá que el enlace no abre cuando ya contaba con él. Un solo caso —hora y media—
     * acotaba la holgura a media hora sin que nadie se enterara; el barrido la acota a lo que
     * de verdad promete el código, y por eso caza que alguien la suba «un poco».
     *
     * Los segundos elegidos son los que duelen: justo por debajo y por encima de cada valor
     * redondo, y la mitad de cada paso, que es donde el redondeo al más cercano se pasaba.
     */
    @Test
    @DisplayName("el plazo NUNCA promete más tiempo del que queda, en ningún tramo")
    void elPlazoNuncaSePasaPorArriba() {
        List<Long> segundos = new ArrayList<>();
        for (long base : List.of(60L, 90L, 119L, 120L, 1799L, 1800L, 2699L, 2700L, 3299L,
                3300L, 3540L, 3599L, 3600L, 5400L, 5500L, 7199L, 7200L, 10_800L, 28_799L,
                28_800L, 86_400L, 129_600L, 172_799L, 172_800L, 259_200L)) {
            segundos.add(base);
        }

        for (long quedan : segundos) {
            when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                    delPerfil(427L, "Ana Quispe", nota("90"))));
            when(archivos.enlaceDeVolcado(any(), eq(4270L))).thenReturn(Optional.of(
                    new EnlaceArchivo("https://almacen.example/x",
                            Instant.parse("2026-08-31T14:00:00Z").plusSeconds(quedan), "cv.pdf")));

            byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                    new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

            long prometidos = segundosPrometidos(pie(contenido));
            /*
              ⚠️ **La holgura ES lo que el aviso puede pasarse, y no hay forma de que sea
              cero**: sin ella el redondeo hacia abajo vuelve a decir «siete horas» donde hay
              ocho. Lo que esta prueba sujeta es que no sea MÁS. Con «≤ lo que queda» a secas
              la holgura no podría existir; con «≤ el tope de la celda» —que fue la primera
              versión— cabía una sobre-promesa de media hora sin que nadie se enterara.
            */
            /*
              ⚠️ **Y que la frase sea de verdad un plazo.** `segundosPrometidos` devuelve 0
              para cualquier frase que no lo sea, así que sin esto el barrido entero pasaba en
              vacío: bastaba con que el código mandara todos los casos a «ya caducados» para
              que ninguna comparación fallara. Comprobado con un mutante.
            */
            assertThat(pie(contenido))
                    .withFailMessage("Con %d s por delante la hoja no anuncia ningún plazo",
                            quedan)
                    .anyMatch(l -> l.contains("dentro de ese plazo puede abrirlos"));
            assertThat(prometidos)
                    .withFailMessage("Con %d s por delante la hoja promete %d s, que se pasa "
                            + "más de los %d s de holgura", quedan, prometidos, HOLGURA_DICHA)
                    .isLessThanOrEqualTo(quedan + HOLGURA_DICHA);
            // Y que no se quede absurdamente corta: nunca menos de la mitad de lo que queda.
            assertThat(prometidos)
                    .withFailMessage("Con %d s por delante la hoja solo promete %d s", quedan,
                            prometidos)
                    .isGreaterThan(quedan / 2);
        }
    }

    /** Lo que {@code ServicioExcelRankingImpl.HOLGURA} promete como máximo desvío. */
    private static final long HOLGURA_DICHA = 300;

    /**
     * Cuántos segundos promete el pie, leídos de su propia frase.
     *
     * <p>Se lee del texto a propósito: es lo que ve quien abre la hoja, y comprobarlo contra
     * la cifra interna dejaría pasar justo los fallos de redacción que ya aparecieron.
     */
    private static long segundosPrometidos(List<String> pie) {
        String frase = pie.stream().filter(l -> l.contains("caducan")).findFirst().orElse("");
        Matcher minutos = Pattern.compile("a los (\\d+) minutos").matcher(frase);
        if (minutos.find()) {
            return Long.parseLong(minutos.group(1)) * 60;
        }
        if (frase.contains("dentro de un minuto")) {
            return 60;
        }
        if (frase.contains("una hora después")) {
            return 3600;
        }
        Matcher horas = Pattern.compile("unas (\\d+) horas").matcher(frase);
        if (horas.find()) {
            return Long.parseLong(horas.group(1)) * 3600;
        }
        Matcher dias = Pattern.compile("unos (\\d+) días").matcher(frase);
        if (dias.find()) {
            return Long.parseLong(dias.group(1)) * 86_400;
        }
        // Sin plazo medible —caducado, inminente o desconocido— no promete nada.
        return 0;
    }

    @Test
    @DisplayName("por debajo del minuto no se redondea a un minuto")
    void pordebajoDelMinutoSeDiceAsi() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));
        when(archivos.enlaceDeVolcado(any(), eq(4270L))).thenReturn(Optional.of(
                new EnlaceArchivo("https://almacen.example/firmado?token=abc",
                        Instant.parse("2026-08-31T14:00:30Z"), "cv.pdf")));

        byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(pie(contenido)).anyMatch(t -> t.contains("en menos de un minuto")
                        && t.contains("a efectos prácticos ya no abren"))
                // Tercer caso degenerado de la coletilla, y por el mismo motivo que los otros.
                .noneMatch(t -> t.contains("dentro de ese plazo puede abrirlos"));
    }

    /*
     * «Unas 720 horas» no lo lee nadie. `horas-enlace-volcado` es un entero sin tope, así que
     * nada impide configurar días.
     */
    @Test
    @DisplayName("a partir de dos días el plazo se dice en días")
    void losPlazosLargosSeDicenEnDias() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));
        // Dos días menos un minuto: con dos días CLAVADOS, truncar y redondear coinciden y
        // el test pasaría con la holgura del tramo equivocado, que es como se coló ya una vez.
        when(archivos.enlaceDeVolcado(any(), eq(4270L))).thenReturn(Optional.of(
                new EnlaceArchivo("https://almacen.example/firmado?token=abc",
                        Instant.parse("2026-09-02T13:59:00Z"), "cv.pdf")));

        byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(pie(contenido)).anyMatch(t -> t.contains("unos 2 días"))
                .noneMatch(t -> t.contains("48 horas") || t.contains("47 horas"));
    }

    /*
     * ⚠️ El tramo de los minutos tenía una holgura de 30 s sobre un paso de 60: media unidad,
     * o sea redondeo al más cercano disfrazado. Minuto y medio se anunciaba como «a los 2
     * minutos», que es la misma sobre-promesa que el tramo de las horas ya evitaba.
     */
    @Test
    @DisplayName("minuto y medio no se anuncia como dos minutos")
    void elTramoDeLosMinutosTampocoSePasa() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));
        when(archivos.enlaceDeVolcado(any(), eq(4270L))).thenReturn(Optional.of(
                new EnlaceArchivo("https://almacen.example/x",
                        Instant.parse("2026-08-31T14:01:30Z"), "cv.pdf")));

        byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(pie(contenido)).anyMatch(t -> t.contains("dentro de un minuto"))
                .noneMatch(t -> t.contains("2 minutos"));
    }

    @Test
    @DisplayName("la frontera de los sesenta segundos cae del lado del minuto")
    void laFronteraDelMinuto() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));
        when(archivos.enlaceDeVolcado(any(), eq(4270L))).thenReturn(Optional.of(
                new EnlaceArchivo("https://almacen.example/x",
                        Instant.parse("2026-08-31T14:00:59Z"), "cv.pdf")));

        byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        // 59 segundos: por debajo del minuto, y eso NO es «dentro de un minuto».
        assertThat(pie(contenido)).anyMatch(t -> t.contains("en menos de un minuto"))
                .noneMatch(t -> t.contains("dentro de un minuto"));
    }

    /*
     * Un enlace que muere exactamente ahora ya no abre. Tratarlo como si le quedara algo es
     * la frontera que separa «caducado» de «inminente», y no la probaba nadie.
     */
    @Test
    @DisplayName("un enlace que muere justo ahora cuenta como caducado")
    void laFronteraDelCero() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));
        when(archivos.enlaceDeVolcado(any(), eq(4270L))).thenReturn(Optional.of(
                new EnlaceArchivo("https://almacen.example/x",
                        Instant.parse("2026-08-31T14:00:00Z"), "cv.pdf")));

        byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(pie(contenido)).anyMatch(t -> t.contains("nacieron caducados"))
                .noneMatch(t -> t.contains("en menos de un minuto"));
    }

    /*
     * ⚠️ Una sola caducidad desconocida invalida la frase entera. Saltársela y anunciar la del
     * vecino promete un plazo sobre un enlace del que no se sabe nada.
     */
    @Test
    @DisplayName("una caducidad desconocida no se sustituye por la del vecino")
    void unaCaducidadDesconocidaNoSeTapa() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90")),
                delPerfil(422L, "Bruno Diaz", nota("80"))));
        when(archivos.enlaceDeVolcado(any(), eq(4270L))).thenReturn(Optional.of(
                new EnlaceArchivo("https://almacen.example/a",
                        Instant.parse("2026-08-31T21:59:05Z"), "cv.pdf")));
        when(archivos.enlaceDeVolcado(any(), eq(4220L))).thenReturn(Optional.of(
                new EnlaceArchivo("https://almacen.example/b", null, "cv.pdf")));

        byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L, 422L), null)).contenido();

        assertThat(pie(contenido)).anyMatch(t -> t.contains("no se sabe cuándo dejan de hacerlo"))
                .noneMatch(t -> t.contains("8 horas")
                        // La coletilla del aviso normal aquí también se contradice: no hay
                        // plazo del que hablar.
                        || t.contains("dentro de ese plazo puede abrirlos"));
    }

    /*
     * Con permiso pero sin un solo currículum subido, la columna va vacía entera. El párrafo
     * de «esto abre sin sesión, no lo reenvíes» avisaba igual de un riesgo que no existía: la
     * hoja se trataba como confidencial sin motivo, y un aviso que sale donde no toca es un
     * aviso que se deja de leer donde sí toca.
     */
    @Test
    @DisplayName("sin currículum subido no se pide enlace, y el pie no avisa de nada")
    void sinCurriculumNoSePideEnlace() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                sinCurriculum(delPerfil(427L, "Ana Quispe", nota("90")))));

        byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(celda(contenido, 1, CV)).isEmpty();
        assertThat(pie(contenido)).noneMatch(t -> t.contains("SIN pedir sesión"));
        verifyNoInteractions(archivos);
    }

    /*
     * ⚠️ Con el archivo subido pero sin nombre, antes se pedía la firma y luego se tiraba: una
     * URL pública viva durante horas y una celda en blanco que nadie podía usar ni sabía que
     * existía. Si se firmó, el enlace va; el rótulo es lo de menos.
     */
    @Test
    @DisplayName("un currículum sin nombre conserva su enlace, con un rótulo de reserva")
    void elCurriculumSinNombreNoPierdeSuEnlace() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                sinNombreDeArchivo(delPerfil(427L, "Ana Quispe", nota("90")))));
        when(archivos.enlaceDeVolcado(any(), eq(4270L))).thenReturn(Optional.of(
                new EnlaceArchivo("https://almacen.example/firmado?token=abc",
                        Instant.parse("2026-08-31T22:00:00Z"), "curriculum-ana.pdf")));

        byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        // El nombre lo pone el almacén, que sí lo tiene.
        assertThat(celda(contenido, 1, CV)).isEqualTo("curriculum-ana.pdf");
        assertThat(enlaceDe(contenido, 1, CV)).isEqualTo("https://almacen.example/firmado?token=abc");
        // Y el pie no cuenta como perdido un enlace que sí se escribió.
        assertThat(pie(contenido)).noneMatch(t -> t.contains("se quedaron sin enlace"));
    }

    // ========================================================================
    // Lo que se lee sin ser una nota
    // ========================================================================

    @Test
    @DisplayName("el correo y el teléfono salen del currículum, no de la cuenta")
    void elCorreoYElTelefonoSonLosDelCurriculum() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        // La de la cuenta —cuenta@cv-convocatoria.local— se la inventó el cargador.
        assertThat(celda(libro, 1, CORREO)).isEqualTo("ana@correo.pe");
        assertThat(celda(libro, 1, TELEFONO)).isEqualTo("999 888 777");
    }

    @Test
    @DisplayName("el pie dice de dónde sale «Nota Perfil Integral» según la vacante")
    void elPieExplicaQueEsLaNotaDelPerfil() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(pie(libro)).anyMatch(t -> t.contains("banco de preguntas apagado")
                && t.contains("currículum"));
    }

    // ========================================================================
    // La forma del archivo
    // ========================================================================

    /*
     * ⚠️ **La única prueba que caza un desplazamiento de columnas.** Las de criterio son
     * variables en número, así que todo lo que va detrás se mueve con ellas; comprobar celdas
     * sueltas por índice no basta, porque fuera de rango se lee «» y un `isEmpty()` pasa.
     */
    @Test
    @DisplayName("la cabecera de la prueba es exactamente la de la plantilla, en su orden")
    void laCabeceraEnteraDeLaPrueba() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe",
                        rubrica("Divisas", "14", "15"), rubrica("Control de caja", "19", "20"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        assertThat(cabeceras(libro)).containsExactly(
                "#", "Candidato", "Correo", "CV", "Teléfono",
                "Nota Examen Técnico /100", "Nota Perfil Integral /100",
                "Divisas (pts /15)", "Control de caja (pts /20)",
                "Nota Combinada /100", "Justificación resumida", "Justificación detallada");
    }

    @Test
    @DisplayName("la cabecera del perfil integral es la misma sin las dos notas que no existen")
    void laCabeceraEnteraDelPerfil() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(cabeceras(libro)).containsExactly(
                "#", "Candidato", "Correo", "CV", "Teléfono",
                "Nota Perfil Integral /100", "Experiencia (pts /100)",
                "Justificación resumida", "Justificación detallada");
    }

    @Test
    @DisplayName("sin ningún criterio la cabecera sigue cuadrando, sin huecos ni sobras")
    void laCabeceraSinCriterios() {
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                sinCriterios(deLaPrueba(427L, "Ana Quispe"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        assertThat(cabeceras(libro)).containsExactly(
                "#", "Candidato", "Correo", "CV", "Teléfono",
                "Nota Examen Técnico /100", "Nota Perfil Integral /100",
                "Nota Combinada /100", "Justificación resumida", "Justificación detallada");
        // Y el cuerpo escribe exactamente tantas celdas como cabeceras hay.
        assertThat(celdas(libro).get(1)).hasSameSizeAs(cabeceras(libro));
    }

    /*
     * ⚠️ Una celda de Excel no admite más de 32.767 caracteres y POI no recorta: lanza. Sin
     * guarda, UN candidato con explicaciones largas dejaba sin archivo a los otros setenta y
     * nueve, con un 500 que no decía por qué. Y no es rebuscado: la justificación detallada
     * junta la explicación de cada criterio, una prueba puede tener doce, y el prompt con el
     * que la IA las escribe se edita desde el panel.
     */
    @Test
    @DisplayName("una justificación enorme se recorta con un aviso; no tumba la descarga")
    void unaJustificacionEnormeNoTumbaLaDescarga() {
        NotaCriterioResponse kilometrica = new NotaCriterioResponse("Divisas", "DIV",
                new BigDecimal("14"), new BigDecimal("15"), new BigDecimal("15"),
                "x".repeat(40_000), "AGENTE", new BigDecimal("80"), null);
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", kilometrica)));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        String detallada = celda(libro, 1, 10);
        assertThat(detallada).hasSizeLessThanOrEqualTo(32_767)
                .endsWith("Está completo en la ficha del candidato.");
        // Y el resto de la fila sale entero: el recorte es de una celda, no del archivo.
        assertThat(celda(libro, 1, CANDIDATO)).isEqualTo("Ana Quispe");
    }

    /*
     * ⚠️ Un emoji ocupa DOS unidades y el corte puede caer justo en medio. La mitad suelta no
     * revienta —POI la cambia por un «?»— pero deja un signo de interrogación huérfano justo
     * donde el texto ya está recortado, que es el sitio exacto donde uno se pregunta si falta
     * algo. Con texto ASCII este camino no se pisa nunca, por eso hace falta su propio caso.
     */
    @Test
    @DisplayName("el recorte no parte un emoji por la mitad")
    void elRecorteNoParteUnEmoji() {
        // Con un emoji al principio, la mitad de los desplazamientos hacen que el corte caiga
        // entre las dos unidades del par; se prueban los dos.
        for (String relleno : List.of("", "x")) {
            NotaCriterioResponse conEmojis = new NotaCriterioResponse("Divisas", "DIV",
                    new BigDecimal("14"), new BigDecimal("15"), new BigDecimal("15"),
                    relleno + "😀".repeat(20_000), "AGENTE", new BigDecimal("80"), null);
            when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                    deLaPrueba(427L, "Ana Quispe", conEmojis)));

            byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                    new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

            String detallada = celda(libro, 1, 10);
            assertThat(detallada).hasSizeLessThanOrEqualTo(32_767)
                    .endsWith("Está completo en la ficha del candidato.")
                    .doesNotContain("?");
        }
    }

    /*
     * ⚠️ `BigDecimal.equals` compara la escala: 15 y 15.00 son objetos distintos, pero la
     * cabecera los escribe igual. Con el objeto en la clave salían DOS columnas con el mismo
     * rótulo, cada una con el hueco de la otra. Hoy no pasa por la vía normal —`puntos` es
     * `numeric(5,2)` y todo vuelve con la misma escala—, pero el arreglo de los dos techos
     * apoya su corrección entera en esa igualdad.
     */
    @Test
    @DisplayName("el mismo techo escrito con otra escala es la MISMA columna")
    void laEscalaDelTechoNoParteLaColumna() {
        NotaCriterioResponse conEscala = new NotaCriterioResponse("Divisas", "DIV",
                new BigDecimal("9"), new BigDecimal("15.00"), new BigDecimal("15"),
                "lo explicó bien", "AGENTE", new BigDecimal("80"), null);
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", rubrica("Divisas", "14", "15")),
                deLaPrueba(422L, "Bruno Diaz", conEscala)));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L, 422L), null)).contenido();

        assertThat(cabeceras(libro)).containsOnlyOnce("Divisas (pts /15)");
        assertThat(celda(libro, 1, 7)).isEqualTo("14");
        assertThat(celda(libro, 2, 7)).isEqualTo("9");
    }

    /*
     * ⚠️ `filtroDescrito` llega del cliente y no tiene tope, y la lista de ajenas crece con lo
     * que se pida. El pie escribe por un camino distinto al de las celdas de la tabla, así que
     * la guarda del tamaño no le llegaba: un filtro largo tumbaba la descarga entera con el
     * mismo 500 que esa guarda existe para evitar.
     */
    @Test
    @DisplayName("un filtro larguísimo no tumba la descarga")
    void unFiltroEnormeNoTumbaLaDescarga() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), "f".repeat(40_000)))
                .contenido();

        /*
          ⚠️ **Lo que importa no es que no reviente, es que no se coma lo de detrás.** El
          filtro se acota ANTES de componer la frase justamente por esto: recortando la línea
          ya compuesta, la hoja sobrevivía pero perdía de qué día es y cuántos candidatos
          trae, que es lo único que la hace legible dentro de un mes.
        */
        assertThat(pie(libro)).anyMatch(t -> t.startsWith("Filtro")
                // Acotado de verdad y no solo «por debajo del tope de la celda»: con un tope
                // de treinta mil la línea seguiría cabiendo y llevando la fecha, así que esa
                // comprobación no sujetaba nada.
                && t.length() < 2_500
                && t.contains("filtro recortado")
                && t.contains("2026-08-31")
                && t.contains("1 candidatos"));
        assertThat(celda(libro, 1, CANDIDATO)).isEqualTo("Ana Quispe");
    }

    /*
     * ⚠️ El filtro lo escribe el cliente y puede traer emojis. Su recorte es otro sitio y otro
     * tope que el de las celdas, y cortaba en seco: partía el par por la mitad y dejaba un
     * «?» huérfano pegado al aviso de recorte. Con texto ASCII este camino no se pisa nunca.
     */
    @Test
    @DisplayName("el recorte del filtro tampoco parte un emoji")
    void elRecorteDelFiltroNoParteUnEmoji() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));

        for (int relleno : new int[]{1998, 1999, 2000}) {
            byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                    new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L),
                            "f".repeat(relleno) + "😀".repeat(50))).contenido();

            // `filteredOn(...).allSatisfy(...)` sobre una lista VACÍA pasa: sin el `hasSize`
            // este test seguía verde aunque la línea del filtro dejara de escribirse.
            assertThat(pie(libro)).filteredOn(t -> t.startsWith("Filtro"))
                    .hasSize(1)
                    .allSatisfy(t -> assertThat(t).doesNotContain("?"));
        }
    }

    /*
     * ⚠️ El nombre de un criterio es `text` en la base y lo escribe quien redacta la rúbrica;
     * el nombre de un archivo, igual. Los dos van a celdas que no pasaban por la guarda del
     * tope, así que uno solo mal escrito tumbaba la descarga entera con un 400 y el mensaje
     * de POI en inglés.
     */
    @Test
    @DisplayName("un nombre de criterio larguísimo no tumba la descarga")
    void unNombreDeCriterioEnormeNoTumbaLaDescarga() {
        NotaCriterioResponse conNombrazo = new NotaCriterioResponse("C".repeat(40_000), "COD",
                new BigDecimal("14"), new BigDecimal("15"), new BigDecimal("15"),
                "lo explicó bien", "AGENTE", new BigDecimal("80"), null);
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", conNombrazo)));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        assertThat(cabeceras(libro)).hasSize(11)
                .allMatch(c -> c.length() <= 32_767);
        assertThat(celda(libro, 1, 7)).isEqualTo("14");
    }

    @Test
    @DisplayName("un nombre de archivo larguísimo tampoco la tumba")
    void unNombreDeArchivoEnormeNoTumbaLaDescarga() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                conArchivoLlamado(delPerfil(427L, "Ana Quispe", nota("90")), "N".repeat(40_000))));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(celda(libro, 1, CV)).hasSizeLessThanOrEqualTo(32_767);
        assertThat(celda(libro, 1, CANDIDATO)).isEqualTo("Ana Quispe");
    }

    /*
     * ⚠️ La celda del CV se escribe por DOS caminos —con enlace y sin él— y solo uno estaba
     * probado. El de arriba va sin el permiso, así que nunca pisaba la rama del hipervínculo,
     * que es la que escribe el rótulo de verdad.
     */
    @Test
    @DisplayName("un nombre de archivo larguísimo tampoco la tumba llevando enlace")
    void unNombreDeArchivoEnormeConEnlaceTampocoLaTumba() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                conArchivoLlamado(delPerfil(427L, "Ana Quispe", nota("90")), "N".repeat(40_000))));
        when(archivos.enlaceDeVolcado(any(), eq(4270L))).thenReturn(Optional.of(
                new EnlaceArchivo("https://almacen.example/firmado?token=abc",
                        Instant.parse("2026-08-31T21:59:05Z"), "cv.pdf")));

        byte[] contenido = servicio.generar(quien("ver_embudo", "descargar_entregables"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(celda(contenido, 1, CV)).hasSizeLessThanOrEqualTo(32_767);
        // Y el enlace sigue puesto: el recorte es del rótulo, no del hipervínculo.
        assertThat(enlaceDe(contenido, 1, CV)).isEqualTo("https://almacen.example/firmado?token=abc");
    }

    /*
     * ⚠️ **Dos criterios distintos que se llamen igual NO pueden compartir columna.** Una
     * rúbrica con «Comunicación» oral y «Comunicación» escrita es una rúbrica normal, y
     * cuando la clave era el rótulo las dos caían en la misma columna: se veía la primera
     * nota y la segunda **desaparecía de la hoja** sin que nada lo dijera. Lo que los
     * distingue ya venía en los datos —el código de la rúbrica— y se estaba tirando.
     */
    @Test
    @DisplayName("dos criterios homónimos son dos columnas, y el rótulo dice cuál es cuál")
    void dosHomonimosSonDosColumnas() {
        NotaCriterioResponse oral = new NotaCriterioResponse("Comunicación", "COM_ORAL",
                new BigDecimal("18"), new BigDecimal("20"), new BigDecimal("20"),
                "clara de palabra", "AGENTE", new BigDecimal("80"), null);
        NotaCriterioResponse escrita = new NotaCriterioResponse("Comunicación", "COM_ESCRITA",
                new BigDecimal("5"), new BigDecimal("20"), new BigDecimal("20"),
                "flojo por escrito", "AGENTE", new BigDecimal("80"), null);
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", oral, escrita)));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        // Los dos puntajes están en la rejilla, cada uno en su columna, y las cabeceras se
        // distinguen por el código en vez de repetirse palabra por palabra.
        assertThat(cabeceras(libro)).doesNotHaveDuplicates()
                .contains("Comunicación [COM_ORAL] (pts /20)",
                        "Comunicación [COM_ESCRITA] (pts /20)");
        assertThat(celda(libro, 1, 7)).isEqualTo("18");
        assertThat(celda(libro, 1, 8)).isEqualTo("5");
    }

    /*
     * ⚠️ **Y el desempate importa**: si dos notas de la misma fila cayeran en la misma
     * columna, se vería una y la otra se perdería. Esta comprueba que no hay tal columna
     * compartida ni siquiera cuando los dos criterios llegan sin explicación —el caso en que
     * la nota perdida no asomaría ni en la justificación detallada—.
     */
    @Test
    @DisplayName("ninguna nota se pierde aunque los dos criterios lleguen mudos")
    void ningunaNotaSePierdeSinExplicacion() {
        NotaCriterioResponse muda1 = new NotaCriterioResponse("Comunicación", "COM_ORAL",
                new BigDecimal("18"), new BigDecimal("20"), new BigDecimal("20"),
                null, "AGENTE", null, null);
        NotaCriterioResponse muda2 = new NotaCriterioResponse("Comunicación", "COM_ESCRITA",
                new BigDecimal("5"), new BigDecimal("20"), new BigDecimal("20"),
                null, "AGENTE", null, null);
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", muda1, muda2)));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        assertThat(columna(libro, 7).get(1)).isEqualTo("18");
        assertThat(columna(libro, 8).get(1)).isEqualTo("5");
        /*
          ⚠️ Y además van las dos en la justificación, aunque no traigan nada que explicar.
          Es la garantía de que ningún número se cae de la hoja: dos criterios que la rejilla
          no pudiera separar compartirían columna, y si además llegaran mudos, el puntaje que
          no se ve no estaría en ninguna parte del archivo.
        */
        assertThat(celda(libro, 1, 11))
                .contains("Comunicación (18/20): sin explicación")
                .contains("Comunicación (5/20): sin explicación");
    }

    /*
     * ⚠️ **El código solo es único dentro de UNA versión de plantilla.** Lo declara el índice
     * de la V10 y lo comprueba el servicio al publicar, así que en una tanda que mezcle dos
     * versiones —el caso que esta hoja tiene que soportar— el mismo «C2» puede ser
     * «Comunicación» en una y «Control de caja» en la otra. Con la clave puesta solo en
     * (código, techo) las dos notas caían en la misma columna y una se leía bajo el nombre del
     * otro criterio: no es una nota perdida, es un número contado como si midiera otra cosa.
     */
    @Test
    @DisplayName("el mismo código en dos versiones de plantilla no junta criterios distintos")
    void elCodigoRepetidoEntreVersionesNoJuntaCriterios() {
        NotaCriterioResponse deLaV1 = new NotaCriterioResponse("Comunicación", "C2",
                new BigDecimal("18"), new BigDecimal("20"), new BigDecimal("20"),
                "clara", "AGENTE", null, null);
        NotaCriterioResponse deLaV2 = new NotaCriterioResponse("Control de caja", "C2",
                new BigDecimal("5"), new BigDecimal("20"), new BigDecimal("20"),
                "flojo", "AGENTE", null, null);
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", deLaV1),
                deLaPrueba(422L, "Bruno Diaz", deLaV2)));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L, 422L), null)).contenido();

        assertThat(cabeceras(libro)).contains("Comunicación (pts /20)", "Control de caja (pts /20)");
        // Y cada puntaje bajo el nombre de SU criterio, con el hueco del otro.
        assertThat(celda(libro, 1, 7)).isEqualTo("18");
        assertThat(celda(libro, 1, 8)).isEmpty();
        assertThat(celda(libro, 2, 7)).isEmpty();
        assertThat(celda(libro, 2, 8)).isEqualTo("5");
    }

    /*
     * ⚠️ **Sin código, el rótulo se desambigua con un ordinal.** El caso realista: un criterio
     * llamado «Divisas» con techo 15 junto a otro al que alguien pegó el rótulo entero como
     * nombre, «Divisas (pts /15)», sin techo. Los dos renderizan el mismo texto y ninguno
     * tiene código con el que distinguirse, así que la desambiguación por código no hace nada
     * y quedaban dos cabeceras idénticas — que obligan a adivinar cuál es cuál.
     */
    @Test
    @DisplayName("sin código, dos rótulos iguales se separan con un ordinal")
    void sinCodigoElRotuloSeSeparaConUnOrdinal() {
        NotaCriterioResponse conRotuloPegado = new NotaCriterioResponse("Divisas (pts /15)", null,
                new BigDecimal("9"), null, null, "lo explicó bien", "AGENTE", null, null);
        NotaCriterioResponse normal = new NotaCriterioResponse("Divisas", null,
                new BigDecimal("14"), new BigDecimal("15"), new BigDecimal("15"),
                "lo explicó bien", "AGENTE", null, null);
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", normal),
                deLaPrueba(422L, "Bruno Diaz", conRotuloPegado)));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L, 422L), null)).contenido();

        assertThat(cabeceras(libro)).doesNotHaveDuplicates();
        assertThat(celda(libro, 1, 7)).isEqualTo("14");
        assertThat(celda(libro, 2, 8)).isEqualTo("9");
    }

    /*
     * ⚠️ **Los ocho criterios del currículum no tienen techo en producción**: ninguna migración
     * les pone `puntos`, así que su máximo llega nulo y la cabecera sale sin «(pts /N)». El
     * resto de pruebas de esta etapa usan un fixture con techo, que es una forma que el sistema
     * no emite; esta comprueba la de verdad.
     */
    @Test
    @DisplayName("un criterio sin techo se rotula sin «(pts /N)»")
    void elCriterioSinTechoSeRotulaSinTecho() {
        NotaCriterioResponse sinTecho = new NotaCriterioResponse("Experiencia", "CV_EXPERIENCIA",
                new BigDecimal("90"), null, null, "lo dice su currículum", "AGENTE",
                new BigDecimal("80"), null);
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", sinTecho)));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", List.of(427L), null)).contenido();

        assertThat(cabeceras(libro)).containsExactly(
                "#", "Candidato", "Correo", "CV", "Teléfono",
                "Nota Perfil Integral /100", "Experiencia",
                "Justificación resumida", "Justificación detallada");
        assertThat(celda(libro, 1, 6)).isEqualTo("90");
    }

    /*
     * Sin código que los distinga —una rúbrica vieja— no hay con qué separarlos, y entonces sí
     * comparten columna. Se comprueba para dejar dicho dónde está el límite.
     */
    @Test
    @DisplayName("sin código, dos homónimos sí comparten columna")
    void sinCodigoLosHomonimosComparten() {
        NotaCriterioResponse sinCodigo1 = new NotaCriterioResponse("Comunicación", null,
                new BigDecimal("18"), new BigDecimal("20"), new BigDecimal("20"),
                "clara", "AGENTE", null, null);
        NotaCriterioResponse sinCodigo2 = new NotaCriterioResponse("Comunicación", "  ",
                new BigDecimal("5"), new BigDecimal("20"), new BigDecimal("20"),
                "floja", "AGENTE", null, null);
        when(tandas.ranking(any(), eq(13L), eq("PRUEBA_PUESTO"))).thenReturn(tanda(
                deLaPrueba(427L, "Ana Quispe", sinCodigo1, sinCodigo2)));

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PRUEBA_PUESTO", List.of(427L), null)).contenido();

        assertThat(cabeceras(libro)).containsOnlyOnce("Comunicación (pts /20)");
        assertThat(celda(libro, 1, 7)).isEqualTo("18");
        // La que no cabe en la rejilla sigue estando en la justificación, que es lo único
        // que queda cuando la rúbrica no trae con qué separarlas.
        assertThat(celda(libro, 1, 10)).contains("floja");
    }

    @Test
    @DisplayName("miles de postulaciones ajenas se cuentan todas y se nombran unas pocas")
    void muchasAjenasNoTumbanLaDescarga() {
        when(tandas.ranking(any(), eq(13L), eq("PERFIL_INTEGRAL"))).thenReturn(tanda(
                delPerfil(427L, "Ana Quispe", nota("90"))));
        List<Long> pedidas = new ArrayList<>(List.of(427L));
        for (long id = 100_000L; id < 106_000L; id++) {
            pedidas.add(id);
        }

        byte[] libro = servicio.generar(quien("ver_embudo"), 13L,
                new PedidoExcelRanking("PERFIL_INTEGRAL", pedidas, null)).contenido();

        // Lo que acota esta línea es `idsDichos`, no el recorte de celda: se nombran 50 y se
        // cuenta el resto, así que nunca se acerca al tope. Comprobar «cabe» sería una
        // aserción que ya no puede fallar; lo que se comprueba es que cuenta TODAS y nombra
        // unas pocas.
        assertThat(pie(libro)).anyMatch(t -> t.contains("6000 postulaciones")
                && t.contains("y 5950 más") && t.length() < 1_000);
    }

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
    @DisplayName("el pie lleva el filtro recibido y la fecha")
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
            Sheet hoja = libro.getSheet(DATOS);
            XSSFCellStyle estilo = (XSSFCellStyle) hoja.getRow(0).getCell(0).getCellStyle();
            assertThat(estilo.getFillForegroundColorColor().getARGBHex()).endsWith("4338CA");
            assertThat(libro.getFontAt(estilo.getFontIndex()).getBold()).isTrue();
            PaneInformation panel = hoja.getPaneInformation();
            assertThat(panel).isNotNull();
            assertThat(panel.getHorizontalSplitPosition()).isEqualTo((short) 1);
            assertThat(hoja.getColumnWidth(CANDIDATO)).isGreaterThan(20 * 256);
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
                filas.length, filas.length, filas.length, 0, 0, true, true, true,
                List.of(filas));
    }

    /** Un criterio del currículum: los ocho globales, que valen sobre 100. */
    private static NotaCriterioResponse nota(String puntaje) {
        return new NotaCriterioResponse("Experiencia", "CV_EXPERIENCIA", new BigDecimal(puntaje),
                new BigDecimal("100"), new BigDecimal("25"), "lo dice su currículum",
                "AGENTE", new BigDecimal("80"), null);
    }

    /** Un criterio de la rúbrica de una prueba: su techo lo reparte la propia plantilla. */
    private static NotaCriterioResponse rubrica(String nombre, String puntaje, String maximo) {
        String codigo = nombre.trim().isEmpty() ? "SIN"
                : nombre.trim().substring(0, Math.min(3, nombre.trim().length()))
                        .toUpperCase(Locale.ROOT);
        return new NotaCriterioResponse(nombre, codigo,
                new BigDecimal(puntaje), new BigDecimal(maximo), new BigDecimal(maximo),
                "lo explicó bien", "AGENTE", new BigDecimal("80"), null);
    }

    /** Una fila del ranking del perfil: la nota de la etapa sale del criterio que se le pasa. */
    private static FilaRanking delPerfil(Long postulacionId, String candidato,
                                         NotaCriterioResponse suNota) {
        return fila(postulacionId, candidato, suNota.puntaje(), List.of(suNota));
    }

    /** Una fila de la prueba: la nota de la etapa es la técnica, y los criterios su rúbrica. */
    private static FilaRanking deLaPrueba(Long postulacionId, String candidato,
                                          NotaCriterioResponse... suRubrica) {
        return fila(postulacionId, candidato, new BigDecimal("73"), List.of(suRubrica));
    }

    private static FilaRanking fila(Long postulacionId, String candidato, BigDecimal notaEtapa,
                                    List<NotaCriterioResponse> notas) {
        return new FilaRanking(puestoDe(postulacionId), postulacionId,
                UUID.randomUUID().toString(), candidato,
                "cuenta@cv-convocatoria.local", "PERFIL_POR_CONFIRMAR", "Perfil por confirmar",
                "TERMINADA", "FINA", "cv.pdf", postulacionId * 10,
                new DatosCandidato(candidato, "ana@correo.pe", "999 888 777", null, null,
                        48, "Analista", "Empresa", "Universidad"),
                "A", notaEtapa, notaEtapa, new BigDecimal("70"), new BigDecimal("65"),
                new BigDecimal("60"), new BigDecimal("75"), "Un resumen corto", 0, 2, 1,
                Instant.parse("2026-08-30T10:00:00Z"), notas,
                "Lima — Lima", "1501", null, null, null, null, null,
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

    /** La misma fila para quien todavía no tiene las dos notas que se mezclan. */
    private static FilaRanking sinPonderado(FilaRanking f) {
        return copia(f, f.notaEtapa(), f.notasCriterio(), f.archivoNombre(), f.archivoId(), null);
    }

    /** La misma fila sin nota de etapa: la IA aún no la calculó. */
    private static FilaRanking sinNotaDeEtapa(FilaRanking f) {
        return copia(f, null, f.notasCriterio(), f.archivoNombre(), f.archivoId(), f.ponderado());
    }

    /** La misma fila sin rúbrica: quien todavía no ha rendido la prueba. */
    private static FilaRanking sinCriterios(FilaRanking f) {
        return copia(f, f.notaEtapa(), List.of(), f.archivoNombre(), f.archivoId(), f.ponderado());
    }

    /** La misma fila sin currículum subido: no hay nada que firmar. */
    private static FilaRanking sinCurriculum(FilaRanking f) {
        return copia(f, f.notaEtapa(), f.notasCriterio(), null, null, f.ponderado());
    }

    /** La misma fila con un nombre de archivo cualquiera. */
    private static FilaRanking conArchivoLlamado(FilaRanking f, String nombre) {
        return copia(f, f.notaEtapa(), f.notasCriterio(), nombre, f.archivoId(), f.ponderado());
    }

    /** El archivo está, pero la tanda no trajo su nombre: sí hay algo que firmar. */
    private static FilaRanking sinNombreDeArchivo(FilaRanking f) {
        return copia(f, f.notaEtapa(), f.notasCriterio(), null, f.archivoId(), f.ponderado());
    }

    /**
     * Copiar campo a campo es lo que exige un record para cambiar uno solo, y es frágil: dos
     * campos seguidos del mismo tipo intercambiados compilan sin una queja. Por eso hay UNA
     * copia y no cuatro, con lo variable por parámetro.
     */
    private static FilaRanking copia(FilaRanking f, BigDecimal notaEtapa,
                                     List<NotaCriterioResponse> notas, String archivoNombre,
                                     Long archivoId, Ponderado ponderado) {
        return new FilaRanking(f.puesto(), f.postulacionId(), f.uuid(), f.candidato(), f.correo(),
                f.estado(), f.estadoNombre(), f.estadoCalificacion(), f.pasada(), archivoNombre,
                archivoId, f.datos(), f.grupoPrioridad(), notaEtapa, f.notaCurriculum(),
                f.adecuacion(), f.potencial(), f.altoRendimiento(), f.confianzaEvidencia(),
                f.resumen(), f.riesgosCriticos(), f.fortalezas(), f.alertas(), f.actualizadoEn(),
                notas, f.ciudad(), f.ciudadCodigo(), f.pretensionMin(), f.pretensionMax(),
                f.pretensionMoneda(), f.pretensionDeclarada(), f.pretensionDeclaradaMoneda(),
                ponderado);
    }

    // ---- Leer el libro que se acaba de escribir ----

    private static <T> T leyendo(byte[] contenido, Function<XSSFWorkbook, T> lector) {
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
            return lector.apply(libro);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Todas las celdas de la hoja como texto, con el formato con que se verían. */
    private static List<List<String>> celdas(byte[] contenido) {
        return leyendo(contenido, libro -> {
            DataFormatter formateador = new DataFormatter(Locale.US);
            Sheet hoja = libro.getSheet(DATOS);
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
        });
    }

    /**
     * La fila 0 entera, que es la única forma de cazar un desplazamiento de columnas.
     *
     * <p>⚠️ Existe porque `celda()` devuelve «» cuando el índice se sale de la fila: con
     * aserciones sueltas por índice, una columna de más o de menos deja pasar un `isEmpty()`
     * sobre una columna que ni siquiera existe.
     */
    private static List<String> cabeceras(byte[] contenido) {
        return celdas(contenido).get(0);
    }

    private static String celda(byte[] contenido, int fila, int columna) {
        List<String> suya = celdas(contenido).get(fila);
        return columna < suya.size() ? suya.get(columna) : "";
    }

    private static List<String> columna(byte[] contenido, int columna) {
        return celdas(contenido).stream()
                .map(fila -> columna < fila.size() ? fila.get(columna) : "")
                .toList();
    }

    /** La dirección detrás de una celda, o null si no lleva ninguna. */
    private static String enlaceDe(byte[] contenido, int fila, int columna) {
        return leyendo(contenido, libro -> {
            Row f = libro.getSheet(DATOS).getRow(fila);
            if (f == null || f.getCell(columna) == null
                    || f.getCell(columna).getHyperlink() == null) {
                return null;
            }
            return f.getCell(columna).getHyperlink().getAddress();
        });
    }

    /** Las líneas sueltas del final: filtro, fecha y avisos. */
    private static List<String> pie(byte[] contenido) {
        return columna(contenido, 0).stream().filter(t -> t.length() > 12).toList();
    }
}
