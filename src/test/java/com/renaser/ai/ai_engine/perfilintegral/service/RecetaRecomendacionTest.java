package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioDelBorrador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioPropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.InsumoRecomendador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.OpcionPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PreguntaDelBorrador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PreguntaPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.ResultadoRecomendador;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("La aduana de lo que propone la IA")
class RecetaRecomendacionTest {

    /** Un borrador con 50 puntos y un criterio «Manejo de Excel» vacío (AC-14b). */
    private static InsumoRecomendador conCincuentaYExcelVacio() {
        return new InsumoRecomendador(null, 50, "énfasis en Excel",
                List.of("ABIERTA", "OPCION_UNICA", "OPCION_MULTIPLE", "ESCALA"),
                List.of(new CriterioDelBorrador(7L, "Contabilidad", null, 50,
                                List.of(new PreguntaDelBorrador("ABIERTA", "Cuéntanos un cierre difícil", 50))),
                        new CriterioDelBorrador(8L, "Manejo de Excel", null, 0, List.of())),
                List.of());
    }

    private static PreguntaPropuesta abierta(String enunciado, int puntos) {
        return new PreguntaPropuesta("ABIERTA", enunciado, BigDecimal.valueOf(puntos),
                "El dato concreto", List.of());
    }

    private static PreguntaPropuesta unica(String enunciado, int puntos) {
        return new PreguntaPropuesta("OPCION_UNICA", enunciado, BigDecimal.valueOf(puntos), null,
                List.of(new OpcionPropuesta("Sí", BigDecimal.valueOf(puntos)),
                        new OpcionPropuesta("No", BigDecimal.ZERO)));
    }

    @Test
    @DisplayName("Suma los 50 que faltan, llena «Manejo de Excel» y trae un criterio nuevo: pasa")
    void unaPropuestaBuenaPasa() {
        ResultadoRecomendador propuesta = new ResultadoRecomendador(List.of(
                new CriterioPropuesto(8L, null, null, List.of(unica("¿Usas BUSCARV?", 10),
                        abierta("¿Cómo armas una tabla dinámica de ventas?", 20))),
                new CriterioPropuesto(null, "Orden", "Cómo organiza su trabajo",
                        List.of(abierta("¿Cómo priorizas los cierres?", 20)))));
        assertThat(RecetaRecomendacion.validar(conCincuentaYExcelVacio(), propuesta)).isEmpty();
    }

    @Test
    @DisplayName("Si no suma lo que faltaba, o va a un criterio que no existe, no pasa (AC-14)")
    void sumaMalYCriterioInexistente() {
        ResultadoRecomendador propuesta = new ResultadoRecomendador(List.of(
                new CriterioPropuesto(99L, null, null, List.of(abierta("¿Cómo usas Excel?", 30)))));
        List<String> errores = RecetaRecomendacion.validar(conCincuentaYExcelVacio(), propuesta);
        assertThat(errores).anyMatch(e -> e.contains("suman 30") && e.contains("exactamente 50"))
                .anyMatch(e -> e.contains("no tiene ese criterio"));
    }

    @Test
    @DisplayName("Una pregunta sin criterio (ni uno del borrador ni uno nuevo con nombre) no pasa (AC-14)")
    void preguntaSinCriterio() {
        ResultadoRecomendador propuesta = new ResultadoRecomendador(List.of(
                new CriterioPropuesto(null, null, null, List.of(abierta("¿Cómo usas las tablas dinámicas?", 30))),
                new CriterioPropuesto(null, "  ", null, List.of(abierta("¿Cómo validas un BUSCARV?", 20)))));
        List<String> errores = RecetaRecomendacion.validar(conCincuentaYExcelVacio(), propuesta);
        assertThat(errores).filteredOn(e -> e.contains("no tiene nombre")).hasSize(2);
    }

    @Test
    @DisplayName("Un criterio nuevo vacío, un tipo inválido y una pregunta repetida del borrador no pasan")
    void vacioTipoYRepetida() {
        ResultadoRecomendador propuesta = new ResultadoRecomendador(List.of(
                new CriterioPropuesto(null, "Vacío", null, List.of()),
                new CriterioPropuesto(null, "Otro", null, List.of(
                        abierta("Cuéntanos un cierre difícil.", 25),
                        new PreguntaPropuesta("SJT-R", "¿?", BigDecimal.valueOf(25), null, List.of())))));
        List<String> errores = RecetaRecomendacion.validar(conCincuentaYExcelVacio(), propuesta);
        assertThat(errores).anyMatch(e -> e.contains("vacío no sirve"))
                .anyMatch(e -> e.contains("el tipo"))
                .anyMatch(e -> e.contains("ya está en el borrador"));
    }

    @Test
    @DisplayName("Los puntos con decimales no pasan")
    void decimales() {
        ResultadoRecomendador propuesta = new ResultadoRecomendador(List.of(
                new CriterioPropuesto(null, "Uno", null, List.of(
                        new PreguntaPropuesta("ABIERTA", "¿A?", new BigDecimal("33.3"), null, List.of()),
                        new PreguntaPropuesta("ABIERTA", "¿B?", new BigDecimal("16.7"), null, List.of())))));
        assertThat(RecetaRecomendacion.validar(conCincuentaYExcelVacio(), propuesta))
                .anyMatch(e -> e.contains("enteros"));
    }

    @Test
    @DisplayName("Sin criterios no hay propuesta")
    void sinCriterios() {
        assertThat(RecetaRecomendacion.validar(conCincuentaYExcelVacio(), new ResultadoRecomendador(List.of())))
                .containsExactly("no devolviste ningún criterio");
        assertThat(RecetaRecomendacion.validar(conCincuentaYExcelVacio(), null)).hasSize(1);
    }
}
