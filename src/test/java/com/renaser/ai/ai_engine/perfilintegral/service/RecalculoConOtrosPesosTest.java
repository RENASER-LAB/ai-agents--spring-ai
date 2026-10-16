package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.entity.PerfilTalento;
import com.renaser.ai.ai_engine.perfilintegral.entity.PesoCriterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PerfilTalentoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PesoCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.impl.CalificacionCriterios;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rehacer las notas de toda una vacante con otra versión de pesos (V74, parte A).
 *
 * <p>Lo que se defiende: cada nota se rehace por el camino que la escribió, sin IA y sin la
 * máquina de estados (esta clase ni siquiera la conoce); la prueba técnica no cambia de
 * valor; las notas de la simulación y la validación pasan a la escala de los pesos nuevos; y
 * quien no tenía ninguna nota no cuenta ni se toca.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Recalcular a todos con otros pesos")
class RecalculoConOtrosPesosTest {

    private static final Long VACANTE = 40L;
    private static final Long ANTES = 2L;
    private static final Long AHORA = 3L;
    private static final String NIVEL = "EJECUCION";
    private static final Long SIM_EJECUCION = 501L;
    private static final Long VAL_RESULTADO = 601L;
    private static final Long CV_PERSONAS = 101L;

    @Mock private PostulacionRepository postulaciones;
    @Mock private NotaEtapaRepository notasEtapa;
    @Mock private NotaCriterioRepository notasCriterio;
    @Mock private PesoCriterioRepository pesosCriterio;
    @Mock private PerfilTalentoRepository perfiles;
    @Mock private CalificacionPorCriterio porCriterio;
    @Mock private CalificacionCriterios bancoCriterios;
    @Mock private PuenteCalificacionIa puente;

    private RecalculoConOtrosPesos recalculo;
    private final Vacante vacante = Vacante.builder().id(VACANTE).versionPesosId(AHORA).build();

    @BeforeEach
    void armar() {
        recalculo = new RecalculoConOtrosPesos(postulaciones, notasEtapa, notasCriterio,
                pesosCriterio, perfiles, porCriterio, bancoCriterios, puente);
        // Los criterios globales de la simulación y la validación: valen su peso del nivel.
        lenient().when(porCriterio.rubricaGlobalDe("SIMULACION"))
                .thenReturn(List.of(Criterio.builder().id(SIM_EJECUCION).nombre("Ejecución").build()));
        lenient().when(porCriterio.rubricaGlobalDe("VALIDACION"))
                .thenReturn(List.of(Criterio.builder().id(VAL_RESULTADO).nombre("Resultado").build()));
        // En la versión nueva la ejecución de la simulación pasa de 10 a 15 y el resultado de
        // la validación de 25 a 20; «Gestión de personas» del currículum, de 0 a 15 (AC-02).
        lenient().when(pesosCriterio.findByVersionPesosIdAndNivelPuestoCodigo(ANTES, NIVEL))
                .thenReturn(List.of(peso(SIM_EJECUCION, "10"), peso(VAL_RESULTADO, "25"),
                        peso(CV_PERSONAS, "0")));
        lenient().when(pesosCriterio.findByVersionPesosIdAndNivelPuestoCodigo(AHORA, NIVEL))
                .thenReturn(List.of(peso(SIM_EJECUCION, "15"), peso(VAL_RESULTADO, "20"),
                        peso(CV_PERSONAS, "15")));
    }

    @Test
    @DisplayName("tres calificados: Perfil Integral y grupo por el recálculo sin IA, la prueba "
            + "técnica igual pero con la versión nueva, y el retrato también la dice (AC-01)")
    void tresCalificados() {
        List<Postulacion> tres = List.of(postulacion(11L, 900L), postulacion(12L, 901L),
                postulacion(13L, 902L));
        when(postulaciones.findByVacanteIdOrderByCreadoEnDesc(VACANTE)).thenReturn(tres);
        NotaEtapa prueba11 = nota(11L, "PRUEBA_PUESTO", "72");
        when(notasEtapa.findByPostulacionIdIn(anyList())).thenReturn(List.of(
                nota(11L, "PERFIL_INTEGRAL", "60"), prueba11,
                nota(12L, "PERFIL_INTEGRAL", "80"), nota(13L, "PERFIL_INTEGRAL", "40")));
        PerfilTalento perfil11 = perfil(11L);
        when(perfiles.findByPostulacionIdIn(anyList())).thenReturn(List.of(perfil11, perfil(12L),
                perfil(13L)));
        // Preguntas propias: la nota del Perfil Integral la rehace el recálculo
        when(bancoCriterios.metodoDe(any())).thenReturn(CalificacionPorPuntos.METODO);

        int recalculadas = recalculo.recalcular(vacante, ANTES, NIVEL);

        assertThat(recalculadas).isEqualTo(3);
        for (Long id : List.of(11L, 12L, 13L)) {
            verify(puente).recalcularConOtrosPesos(id);
        }
        // La prueba técnica lleva sus propios puntos: vale lo mismo, pero ya dice la versión
        assertThat(prueba11.getPuntaje()).isEqualByComparingTo("72");
        assertThat(prueba11.getVersionPesosId()).isEqualTo(AHORA);
        verify(notasEtapa).save(prueba11);
        assertThat(perfil11.getVersionPesosId()).isEqualTo(AHORA);
        verify(perfiles).save(perfil11);
        // Ni la IA de la recalificación ni el recálculo que avisa al pase automático
        verify(puente, never()).recalcularSinMover(any());
        verify(bancoCriterios, never()).calificarEtapa(any());
    }

    @Test
    @DisplayName("con el banco CRITERIOS se repondera su índice con las dimensiones nuevas antes "
            + "que el Perfil Integral, como en el flujo de siempre (AC-03)")
    void conElBancoCriterios() {
        Postulacion conRetrato = postulacion(11L, 900L);
        Postulacion sinRetrato = postulacion(12L, 901L);
        when(postulaciones.findByVacanteIdOrderByCreadoEnDesc(VACANTE))
                .thenReturn(List.of(conRetrato, sinRetrato));
        NotaEtapa indice = nota(12L, "PERFIL_INTEGRAL", "55");
        when(notasEtapa.findByPostulacionIdIn(anyList()))
                .thenReturn(List.of(nota(11L, "PERFIL_INTEGRAL", "70"), indice));
        when(perfiles.findByPostulacionIdIn(anyList())).thenReturn(List.of(perfil(11L)));
        when(bancoCriterios.metodoDe(any())).thenReturn(CalificacionCriterios.METODO);

        recalculo.recalcular(vacante, ANTES, NIVEL);

        InOrder orden = inOrder(bancoCriterios, puente);
        orden.verify(bancoCriterios).calificarEtapa(conRetrato);
        orden.verify(puente).recalcularConOtrosPesos(11L);
        // Sin retrato, la nota del Perfil Integral ES el índice: no se pisa ni se re-firma
        verify(bancoCriterios).calificarEtapa(sinRetrato);
        verify(puente, never()).recalcularConOtrosPesos(12L);
        verify(notasEtapa, never()).save(indice);
    }

    @Test
    @DisplayName("la simulación y la validación pasan a la escala de los pesos nuevos y se vuelven "
            + "a sumar; a quien está a mitad de la rúbrica también se le reescala (AC-03)")
    void simulacionYValidacionSeReponderan() {
        Postulacion conSimulacion = postulacion(11L, null);
        Postulacion aMedias = postulacion(12L, null);
        when(postulaciones.findByVacanteIdOrderByCreadoEnDesc(VACANTE))
                .thenReturn(List.of(conSimulacion, aMedias));
        when(notasEtapa.findByPostulacionIdIn(anyList())).thenReturn(List.of(
                nota(11L, "SIMULACION", "8"), nota(11L, "VALIDACION", "20")));
        when(perfiles.findByPostulacionIdIn(anyList())).thenReturn(List.of());
        NotaCriterio ejecucion = criterio(11L, SIM_EJECUCION, "8");      // 8 de 10
        NotaCriterio resultado = criterio(11L, VAL_RESULTADO, "20");     // 20 de 25
        NotaCriterio delCurriculum = criterio(11L, CV_PERSONAS, "64");   // 0..100 de la IA
        NotaCriterio aMitad = criterio(12L, SIM_EJECUCION, "5");         // 5 de 10, sin cerrar
        when(notasCriterio.findByPostulacionIdIn(anyList()))
                .thenReturn(List.of(ejecucion, resultado, delCurriculum, aMitad));

        int recalculadas = recalculo.recalcular(vacante, ANTES, NIVEL);

        assertThat(ejecucion.getPuntaje()).isEqualByComparingTo("12");    // 8/10 × 15
        assertThat(resultado.getPuntaje()).isEqualByComparingTo("16");    // 20/25 × 20
        assertThat(aMitad.getPuntaje()).isEqualByComparingTo("7.5");      // 5/10 × 15
        // Lo que se puso queda apuntado con el máximo de entonces, para el próximo cambio
        assertThat(ejecucion.getPuntajeOriginal()).isEqualByComparingTo("8");
        assertThat(ejecucion.getMaximoOriginal()).isEqualByComparingTo("10");
        assertThat(delCurriculum.getPuntajeOriginal()).isNull();
        // El currículum no se reescala: la IA puso 0..100 y su peso es un peso, no un máximo
        assertThat(delCurriculum.getPuntaje()).isEqualByComparingTo("64");
        verify(porCriterio).calcularNotaEtapa(eq(conSimulacion), eq("SIMULACION"), anyList());
        verify(porCriterio).calcularNotaEtapa(eq(conSimulacion), eq("VALIDACION"), anyList());
        verify(porCriterio, never()).calcularNotaEtapa(eq(aMedias), any(), anyList());
        // Quien solo está a mitad de la rúbrica no tiene nota: no cuenta
        assertThat(recalculadas).isEqualTo(1);
    }

    @Test
    @DisplayName("sin ninguna nota no cuenta ni se toca: su nota saldrá ya con la versión nueva")
    void sinNotaNoCuenta() {
        when(postulaciones.findByVacanteIdOrderByCreadoEnDesc(VACANTE))
                .thenReturn(List.of(postulacion(11L, 900L)));
        when(notasEtapa.findByPostulacionIdIn(anyList())).thenReturn(List.of());
        when(perfiles.findByPostulacionIdIn(anyList())).thenReturn(List.of());

        assertThat(recalculo.recalcular(vacante, ANTES, NIVEL)).isZero();
        verify(puente, never()).recalcularConOtrosPesos(any());
        verify(bancoCriterios, never()).calificarEtapa(any());
        verify(notasEtapa, never()).save(any());
    }

    @Test
    @DisplayName("con el banco de antes y sin retrato, la nota de lo cerrado no depende de los "
            + "pesos: solo pasa a decir la versión nueva")
    void loCerradoSoloCambiaDeVersion() {
        when(postulaciones.findByVacanteIdOrderByCreadoEnDesc(VACANTE))
                .thenReturn(List.of(postulacion(11L, 900L)));
        NotaEtapa cerrado = nota(11L, "PERFIL_INTEGRAL", "66");
        when(notasEtapa.findByPostulacionIdIn(anyList())).thenReturn(List.of(cerrado));
        when(perfiles.findByPostulacionIdIn(anyList())).thenReturn(List.of());
        when(bancoCriterios.metodoDe(any())).thenReturn(null);

        recalculo.recalcular(vacante, ANTES, NIVEL);

        assertThat(cerrado.getPuntaje()).isEqualByComparingTo("66");
        assertThat(cerrado.getVersionPesosId()).isEqualTo(AHORA);
        verify(puente, never()).recalcularConOtrosPesos(any());
    }

    @Test
    @DisplayName("si una nota no se puede recalcular, revienta diciendo cuál: no se guarda nada (AC-07)")
    void unFalloAMitadLoDice() {
        Postulacion p = postulacion(11L, null);
        when(postulaciones.findByVacanteIdOrderByCreadoEnDesc(VACANTE)).thenReturn(List.of(p));
        when(notasEtapa.findByPostulacionIdIn(anyList()))
                .thenReturn(List.of(nota(11L, "SIMULACION", "8")));
        when(perfiles.findByPostulacionIdIn(anyList())).thenReturn(List.of());
        when(porCriterio.calcularNotaEtapa(eq(p), eq("SIMULACION"), anyList()))
                .thenThrow(new IllegalStateException("Todavía faltan notas por poner: Calidad"));

        assertThatThrownBy(() -> recalculo.recalcular(vacante, ANTES, NIVEL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SIMULACION")
                .hasMessageContaining("postulación 11")
                .hasMessageContaining("no se cambió nada");
    }

    @Test
    @DisplayName("la confirmación cuenta a quien tiene una nota de etapa o un retrato")
    void cuentaAQuienTieneNota() {
        when(postulaciones.findByVacanteIdOrderByCreadoEnDesc(VACANTE)).thenReturn(List.of(
                postulacion(11L, null), postulacion(12L, null), postulacion(13L, null)));
        when(notasEtapa.findByPostulacionIdIn(anyList()))
                .thenReturn(List.of(nota(11L, "PRUEBA_PUESTO", "50")));
        when(perfiles.findByPostulacionIdIn(anyList())).thenReturn(List.of(perfil(12L)));

        assertThat(recalculo.conAlgunaNota(VACANTE)).containsExactly(11L, 12L);
    }

    @Test
    @DisplayName("QA-01 · ir a otros pesos y volver deja la simulación como la puso el facilitador, "
            + "aunque se redondee y un criterio pase por peso 0 (AC-03)")
    void idaYVueltaDejaLaSimulacionComoEstaba() {
        // Versión 2: ejecución 15, calidad 15, aprendizaje 10. Versión 3: 20, 10 y 0.
        Long calidad = 502L;
        Long aprendizaje = 503L;
        when(porCriterio.rubricaGlobalDe("SIMULACION")).thenReturn(List.of(
                Criterio.builder().id(SIM_EJECUCION).build(), Criterio.builder().id(calidad).build(),
                Criterio.builder().id(aprendizaje).build()));
        when(pesosCriterio.findByVersionPesosIdAndNivelPuestoCodigo(ANTES, NIVEL)).thenReturn(List.of(
                peso(SIM_EJECUCION, "15"), peso(calidad, "15"), peso(aprendizaje, "10")));
        when(pesosCriterio.findByVersionPesosIdAndNivelPuestoCodigo(AHORA, NIVEL)).thenReturn(List.of(
                peso(SIM_EJECUCION, "20"), peso(calidad, "10"), peso(aprendizaje, "0")));
        Postulacion cata = postulacion(11L, null);
        when(postulaciones.findByVacanteIdOrderByCreadoEnDesc(VACANTE)).thenReturn(List.of(cata));
        when(notasEtapa.findByPostulacionIdIn(anyList()))
                .thenReturn(List.of(nota(11L, "SIMULACION", "28")));
        when(perfiles.findByPostulacionIdIn(anyList())).thenReturn(List.of());
        // Filas de antes de la V75: no dicen todavía cómo se pusieron
        NotaCriterio ejecucion = criterio(11L, SIM_EJECUCION, "15");
        NotaCriterio enCalidad = criterio(11L, calidad, "7");
        NotaCriterio enAprendizaje = criterio(11L, aprendizaje, "6");
        when(notasCriterio.findByPostulacionIdIn(anyList()))
                .thenReturn(List.of(ejecucion, enCalidad, enAprendizaje));

        // Ida: de la 2 a la 3
        recalculo.recalcular(vacante, ANTES, NIVEL);
        assertThat(ejecucion.getPuntaje()).isEqualByComparingTo("20");
        assertThat(enCalidad.getPuntaje()).isEqualByComparingTo("4.67");
        assertThat(enAprendizaje.getPuntaje()).isEqualByComparingTo("0");
        // Lo que se puso queda apuntado, con su máximo de entonces
        assertThat(enCalidad.getPuntajeOriginal()).isEqualByComparingTo("7");
        assertThat(enCalidad.getMaximoOriginal()).isEqualByComparingTo("15");
        assertThat(enAprendizaje.getPuntajeOriginal()).isEqualByComparingTo("6");
        assertThat(enAprendizaje.getMaximoOriginal()).isEqualByComparingTo("10");

        // Vuelta: de la 3 a la 2, con las mismas filas ya reescaladas
        Vacante deVuelta = Vacante.builder().id(VACANTE).versionPesosId(ANTES).build();
        recalculo.recalcular(deVuelta, AHORA, NIVEL);
        assertThat(ejecucion.getPuntaje()).isEqualByComparingTo("15");
        assertThat(enCalidad.getPuntaje()).isEqualByComparingTo("7");
        assertThat(enAprendizaje.getPuntaje()).isEqualByComparingTo("6");
        // Y lo que se puso no se movió
        assertThat(enCalidad.getPuntajeOriginal()).isEqualByComparingTo("7");
        assertThat(enCalidad.getMaximoOriginal()).isEqualByComparingTo("15");

        // Otra ida da exactamente lo mismo que la primera: no se acumula nada
        recalculo.recalcular(vacante, ANTES, NIVEL);
        assertThat(enCalidad.getPuntaje()).isEqualByComparingTo("4.67");
        assertThat(enAprendizaje.getPuntaje()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("QA-01 · una nota que ya dice cómo se puso se calcula desde ahí, no desde la "
            + "escala de la versión que la vacante dejaba (validación)")
    void conLaNotaOriginalNoMiraLaVersionAnterior() {
        when(postulaciones.findByVacanteIdOrderByCreadoEnDesc(VACANTE))
                .thenReturn(List.of(postulacion(11L, null)));
        when(notasEtapa.findByPostulacionIdIn(anyList()))
                .thenReturn(List.of(nota(11L, "VALIDACION", "0")));
        when(perfiles.findByPostulacionIdIn(anyList())).thenReturn(List.of());
        // Puesta como 18 sobre 25 y hoy en 0 porque pasó por una versión con peso 0
        NotaCriterio resultado = criterio(11L, VAL_RESULTADO, "0");
        resultado.setPuntajeOriginal(new BigDecimal("18"));
        resultado.setMaximoOriginal(new BigDecimal("25"));
        when(notasCriterio.findByPostulacionIdIn(anyList())).thenReturn(List.of(resultado));

        recalculo.recalcular(vacante, ANTES, NIVEL);

        assertThat(resultado.getPuntaje()).isEqualByComparingTo("14.40");   // 18/25 × 20
        assertThat(resultado.getPuntajeOriginal()).isEqualByComparingTo("18");
        assertThat(resultado.getMaximoOriginal()).isEqualByComparingTo("25");
        verify(notasCriterio).saveAll(List.of(resultado));
        verify(porCriterio).calcularNotaEtapa(any(), eq("VALIDACION"), anyList());
    }

    @Test
    @DisplayName("en la escala de ahora: proporcional; con el máximo de entonces, tal cual; sin "
            + "peso ahora se queda como se puso; puesta sin peso se acota")
    void enLaEscala() {
        assertThat(RecalculoConOtrosPesos.enLaEscala(new BigDecimal("8"), BigDecimal.TEN,
                new BigDecimal("15"))).isEqualByComparingTo("12");
        assertThat(RecalculoConOtrosPesos.enLaEscala(new BigDecimal("7"), new BigDecimal("15"),
                BigDecimal.TEN)).isEqualByComparingTo("4.67");
        // Con el máximo con que se puso, exactamente lo que se puso: ni un 7,01
        assertThat(RecalculoConOtrosPesos.enLaEscala(new BigDecimal("7"), new BigDecimal("15"),
                new BigDecimal("15"))).isEqualByComparingTo("7");
        // Peso 0 ahora: vale 0, pero lo que se puso no se toca y vuelve con otro peso
        assertThat(RecalculoConOtrosPesos.enLaEscala(new BigDecimal("6"), BigDecimal.TEN,
                BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThat(RecalculoConOtrosPesos.enLaEscala(new BigDecimal("8"), BigDecimal.TEN, null))
                .isEqualByComparingTo("8");
        assertThat(RecalculoConOtrosPesos.enLaEscala(BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("15"))).isEqualByComparingTo("0");
        assertThat(RecalculoConOtrosPesos.enLaEscala(new BigDecimal("30"), null,
                new BigDecimal("15"))).isEqualByComparingTo("15");
        // Un criterio con peso 0 en las dos versiones no cambia nada
        assertThat(RecalculoConOtrosPesos.enLaEscala(BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    private static Postulacion postulacion(Long id, Long evaluacionId) {
        return Postulacion.builder().id(id).vacanteId(VACANTE).evaluacionId(evaluacionId)
                .estadoCodigo("PRUEBA_POR_CONFIRMAR").build();
    }

    private static NotaEtapa nota(Long postulacion, String etapa, String puntaje) {
        return NotaEtapa.builder().postulacionId(postulacion).etapaCodigo(etapa)
                .puntaje(new BigDecimal(puntaje)).versionPesosId(ANTES).build();
    }

    private static PerfilTalento perfil(Long postulacion) {
        return PerfilTalento.builder().id(postulacion * 10).postulacionId(postulacion)
                .versionPesosId(ANTES).build();
    }

    private static NotaCriterio criterio(Long postulacion, Long criterio, String puntaje) {
        return NotaCriterio.builder().postulacionId(postulacion).criterioId(criterio)
                .puntaje(new BigDecimal(puntaje)).origen("PERSONA").build();
    }

    private static PesoCriterio peso(Long criterio, String peso) {
        return PesoCriterio.builder().criterioId(criterio).peso(new BigDecimal(peso)).build();
    }
}
