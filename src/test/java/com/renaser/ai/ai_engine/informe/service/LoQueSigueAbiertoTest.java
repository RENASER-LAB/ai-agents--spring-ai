package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EstadoDeUnInforme;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Punto;
import com.renaser.ai.ai_engine.informe.service.LoComunDelInforme.Base;
import com.renaser.ai.ai_engine.informe.service.LoQueSigueAbierto.Situacion;
import com.renaser.ai.ai_engine.informe.service.PendientesEntreEtapas.Duda;
import com.renaser.ai.ai_engine.informe.service.PendientesEntreEtapas.Fuente;
import com.renaser.ai.ai_engine.informe.service.PendientesEntreEtapas.Resultado;
import com.renaser.ai.ai_engine.perfilintegral.entity.AfirmacionCv;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Qué sigue abierto en cada etapa: la evaluación con las dudas del CV, la prueba solo con lo que
 * siguiera abierto tras la evaluación (AC-08) más lo que la evaluación dejó (D-1), y nada de la
 * prueba antes de que esté entregada y calificada (AC-16).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Lo que sigue abierto entre etapas")
class LoQueSigueAbiertoTest {

    private static final Long POSTULACION = 10L;
    private static final EtapaDelInforme EVALUACION = EtapaDelInforme.EVALUACION;
    private static final EtapaDelInforme PRUEBA = EtapaDelInforme.PRUEBA;
    private static final List<Fuente> ABIERTAS = List.of(Fuente.respuesta(3, "¿Qué hacías?", "Cerraba la planilla."));

    @Mock private ArmadoDeLaEvaluacion evaluacion;
    @Mock private ArmadoDeLaPrueba prueba;
    @Mock private SeguimientosDelInforme seguimientos;

    @InjectMocks private LoQueSigueAbierto abierto;

    private final Postulacion postulacion = Postulacion.builder().id(POSTULACION).build();

    private Base base(AfirmacionCv... afirmaciones) {
        return new Base(postulacion, null, null, "Renaser", "Ana", null, null, null, List.of(afirmaciones), null,
                List.of(), List.of(), "TERMINADA");
    }

    private static AfirmacionCv afirmacion(long id, String texto, String clase, String pregunta) {
        return AfirmacionCv.builder().id(id).texto("  " + texto + " ").clasificacion(clase).preguntaValidacion(pregunta)
                .build();
    }

    private static final AfirmacionCv PLANILLAS = afirmacion(1, "Manejo de planillas", "DECLARADA", " ¿Cuántas? ");
    private static final AfirmacionCv SALIDA = afirmacion(2, "Motivo de salida", "FALTA_INFO", null);
    private static final AfirmacionCv LOGRO = afirmacion(3, "Redujo el tiempo", "DEMOSTRADA", null);
    private static final AfirmacionCv JEFA = afirmacion(4, "Jefa en 2022", "CONTRADICHA", null);

    private void disponible(EtapaDelInforme etapa, boolean si) {
        EstadoDeUnInforme estado = new EstadoDeUnInforme(etapa.name(), si, si ? null : "x", BigDecimal.TEN);
        if (etapa == EVALUACION) {
            when(evaluacion.estado(any())).thenReturn(estado);
        } else {
            when(prueba.estado(any())).thenReturn(estado);
        }
    }

    @Nested
    @DisplayName("En la evaluación")
    class EnLaEvaluacion {

        @Test
        @DisplayName("sigue las declaradas y las sin información, numeradas, contra sus abiertas, y lee el vigente")
        void dudasYAbiertas() {
            disponible(EVALUACION, true);
            when(evaluacion.abiertasConTexto(any())).thenReturn(ABIERTAS);
            Resultado guardado = new Resultado(List.of(), 0, List.of());
            when(seguimientos.vigente(eq(POSTULACION), eq(EVALUACION), anyString())).thenReturn(Optional.of(guardado));

            Situacion s = abierto.de(base(PLANILLAS, LOGRO, SALIDA, JEFA), EVALUACION).orElseThrow();

            assertThat(s.seguir().dudas()).extracting(Duda::codigo, Duda::texto, Duda::clasificacion,
                            Duda::preguntaDeValidacion, Duda::estado)
                    .containsExactly(tuple("D1", "Manejo de planillas", "DECLARADA", "¿Cuántas?", null),
                            tuple("D2", "Motivo de salida", "FALTA_INFO", null, null));
            assertThat(s.seguir().fuentes()).isEqualTo(ABIERTAS);
            assertThat(s.huella()).isEqualTo(PendientesEntreEtapas.huella(s.seguir()));
            assertThat(s.resultado()).isSameAs(guardado);
            assertThat(s.alDia()).isTrue();
            assertThat(s.antes()).isNull();
        }

        @Test
        @DisplayName("AC-05 · sin dudas, o sin informe de la evaluación, no hay nada que seguir")
        void sinDudasOSinInforme() {
            disponible(EVALUACION, true);
            assertThat(abierto.de(base(LOGRO, JEFA), EVALUACION)).isEmpty();
            disponible(EVALUACION, false);
            assertThat(abierto.de(base(PLANILLAS), EVALUACION)).isEmpty();
            assertThat(abierto.de(base(PLANILLAS), EtapaDelInforme.CV)).isEmpty();
        }

        @Test
        @DisplayName("AC-06 · sin abiertas con texto, todas «sigue pendiente» sin preguntar por ningún guardado")
        void sinTexto() {
            disponible(EVALUACION, true);
            when(evaluacion.abiertasConTexto(any())).thenReturn(List.of());

            Situacion s = abierto.de(base(PLANILLAS, SALIDA), EVALUACION).orElseThrow();

            assertThat(s.resultado().dudas()).extracting(Duda::estado)
                    .containsOnly(PendientesEntreEtapas.SIGUE_PENDIENTE);
            verify(seguimientos, never()).vigente(any(), any(), any());
        }

        @Test
        @DisplayName("sin vigente, la situación queda pendiente de pedir")
        void sinVigente() {
            disponible(EVALUACION, true);
            when(evaluacion.abiertasConTexto(any())).thenReturn(ABIERTAS);
            when(seguimientos.vigente(any(), any(), any())).thenReturn(Optional.empty());

            assertThat(abierto.de(base(PLANILLAS), EVALUACION).orElseThrow().alDia()).isFalse();
        }
    }

    @Nested
    @DisplayName("En la prueba")
    class EnLaPrueba {

        private final List<Punto> deLaEvaluacion = List.of(
                new Punto("No se metió en lo incómodo", "Nunca habló de un error.", "EVALUACION", "Patrón",
                        "De la evaluación"));

        private void laPruebaLista() {
            disponible(PRUEBA, true);
            when(prueba.deLaEvaluacion(any())).thenReturn(deLaEvaluacion);
            when(prueba.paraElSeguimiento(any())).thenReturn(List.of(Fuente.respuesta(2, "¿Cuánto cuesta?", "S/ 31 400.")));
        }

        @Test
        @DisplayName("AC-16 · sin la prueba entregada y calificada no hay nada que seguir")
        void antesDelReloj() {
            disponible(PRUEBA, false);
            assertThat(abierto.de(base(PLANILLAS), PRUEBA)).isEmpty();
            verify(prueba, never()).paraElSeguimiento(any());
        }

        @Test
        @DisplayName("AC-08 · con el de la evaluación al día recibe solo lo abierto, con su estado, y lo que dejó la evaluación")
        void soloLoAbierto() {
            laPruebaLista();
            disponible(EVALUACION, true);
            when(evaluacion.abiertasConTexto(any())).thenReturn(ABIERTAS);
            Duda resuelta1 = new Duda("D1", "CV", "A", "DECLARADA", null, PendientesEntreEtapas.RESUELTA, 3, null, "c", "e", "EVALUACION");
            Duda resuelta2 = new Duda("D2", "CV", "B", "DECLARADA", null, PendientesEntreEtapas.RESUELTA, 3, null, "c", "e", "EVALUACION");
            Duda pendiente = new Duda("D3", "CV", "C", "FALTA_INFO", "¿Y?", PendientesEntreEtapas.SIGUE_PENDIENTE, null, null, null, null, null);
            when(seguimientos.vigente(eq(POSTULACION), eq(EVALUACION), anyString()))
                    .thenReturn(Optional.of(new Resultado(List.of(resuelta1, resuelta2, pendiente), 0, List.of())));
            when(seguimientos.vigente(eq(POSTULACION), eq(PRUEBA), anyString())).thenReturn(Optional.empty());

            Situacion s = abierto.de(base(PLANILLAS, SALIDA), PRUEBA).orElseThrow();

            assertThat(s.seguir().dudas()).extracting(Duda::codigo, Duda::origen, Duda::texto, Duda::clasificacion,
                            Duda::estado)
                    .containsExactly(tuple("D1", "CV", "C", "FALTA_INFO", PendientesEntreEtapas.SIGUE_PENDIENTE),
                            tuple("D2", "EVALUACION", "No se metió en lo incómodo", "Patrón", null));
            assertThat(s.seguir().fuentes()).extracting(Fuente::codigo).containsExactly("P2");
            assertThat(s.antes()).isNull();
            assertThat(s.alDia()).isFalse();
        }

        @Test
        @DisplayName("si el de la evaluación viene en camino, la prueba lo espera y no enseña ninguno")
        void esperaALaEvaluacion() {
            laPruebaLista();
            disponible(EVALUACION, true);
            when(evaluacion.abiertasConTexto(any())).thenReturn(ABIERTAS);
            when(seguimientos.vigente(any(), any(), any())).thenReturn(Optional.empty());
            when(seguimientos.enCamino(POSTULACION, EVALUACION)).thenReturn(true);

            Situacion s = abierto.de(base(PLANILLAS), PRUEBA).orElseThrow();

            assertThat(s.antes()).isNotNull();
            assertThat(s.antes().etapa()).isEqualTo(EVALUACION);
            assertThat(s.alDia()).isFalse();
            verify(seguimientos, never()).vigente(any(), eq(PRUEBA), any());
        }

        @Test
        @DisplayName("si el de la evaluación nunca se pidió, la prueba también lo espera")
        void evaluacionSinPedir() {
            laPruebaLista();
            disponible(EVALUACION, true);
            when(evaluacion.abiertasConTexto(any())).thenReturn(ABIERTAS);
            when(seguimientos.vigente(any(), any(), any())).thenReturn(Optional.empty());
            when(seguimientos.yaPedido(eq(POSTULACION), eq(EVALUACION), anyString())).thenReturn(false);

            assertThat(abierto.de(base(PLANILLAS), PRUEBA).orElseThrow().antes()).isNotNull();
        }

        @Test
        @DisplayName("si el de la evaluación falló (pedido y sin llegar), la prueba recibe todas las dudas del CV")
        void evaluacionFallida() {
            laPruebaLista();
            disponible(EVALUACION, true);
            when(evaluacion.abiertasConTexto(any())).thenReturn(ABIERTAS);
            when(seguimientos.vigente(any(), any(), any())).thenReturn(Optional.empty());
            when(seguimientos.yaPedido(eq(POSTULACION), eq(EVALUACION), anyString())).thenReturn(true);

            Situacion s = abierto.de(base(PLANILLAS, SALIDA), PRUEBA).orElseThrow();

            assertThat(s.antes()).isNull();
            assertThat(s.seguir().dudas()).extracting(Duda::texto)
                    .containsExactly("Manejo de planillas", "Motivo de salida", "No se metió en lo incómodo");
        }

        @Test
        @DisplayName("sin evaluación recibe todas las dudas del CV; sin ninguna duda ni pendiente, nada")
        void sinEvaluacion() {
            laPruebaLista();
            disponible(EVALUACION, false);
            when(seguimientos.vigente(any(), any(), any())).thenReturn(Optional.empty());

            assertThat(abierto.de(base(PLANILLAS), PRUEBA).orElseThrow().seguir().dudas()).hasSize(2);

            when(prueba.deLaEvaluacion(any())).thenReturn(List.of());
            assertThat(abierto.de(base(LOGRO), PRUEBA)).isEmpty();
        }

        @Test
        @DisplayName("Spec 12 · si el de la evaluación al día lo resolvió todo y no dejó nada, queda al día sin dudas: "
                + "«Del CV» vacío, sin leer la prueba ni pedir nada")
        void laEvaluacionLoResolvioTodo() {
            laPruebaLista();
            when(prueba.deLaEvaluacion(any())).thenReturn(List.of());
            disponible(EVALUACION, true);
            when(evaluacion.abiertasConTexto(any())).thenReturn(ABIERTAS);
            Duda planillas = new Duda("D1", "CV", "Manejo de planillas", "DECLARADA", "¿Cuántas?",
                    PendientesEntreEtapas.RESUELTA, 3, null, "Cerraba la planilla", "Cuenta su cierre.", "EVALUACION");
            Duda salida = new Duda("D2", "CV", "Motivo de salida", "FALTA_INFO", null,
                    PendientesEntreEtapas.RESUELTA, 3, null, "Cerraba la planilla", "Lo explica.", "EVALUACION");
            when(seguimientos.vigente(eq(POSTULACION), eq(EVALUACION), anyString()))
                    .thenReturn(Optional.of(new Resultado(List.of(planillas, salida), 0, List.of())));

            Situacion s = abierto.de(base(PLANILLAS, SALIDA), PRUEBA).orElseThrow();

            assertThat(s.etapa()).isEqualTo(PRUEBA);
            assertThat(s.alDia()).isTrue();
            assertThat(s.antes()).isNull();
            assertThat(s.seguir().dudas()).isEmpty();
            assertThat(s.resultado().dudas()).isEmpty();
            assertThat(s.huella()).isEqualTo(PendientesEntreEtapas.huella(s.seguir()));
            // En el informe de la prueba: ni las resueltas en la evaluación en «Del CV», ni «Lo que aclaró la prueba».
            List<Punto> entrevista = PendientesEntreEtapas.aplicar(InformesDePrueba.prueba(List.of()), s.resultado())
                    .bloques().stream().filter(b -> "PENDIENTE".equals(b.codigo())).findFirst().orElseThrow().puntos();
            assertThat(entrevista).extracting(Punto::grupo).doesNotContain(PendientesEntreEtapas.GRUPO_DEL_CV)
                    .contains("De la prueba", PendientesEntreEtapas.GRUPO_DE_LA_EVALUACION);
            verify(prueba, never()).paraElSeguimiento(any());
            verify(seguimientos, never()).vigente(any(), eq(PRUEBA), any());
            verify(seguimientos, never()).pedir(any(), any(), any());
            verify(seguimientos, never()).yaPedido(any(), any(), any());
            verify(seguimientos, never()).enCamino(any(), any());
        }

        @Test
        @DisplayName("Spec 12 · sin seguimiento de la evaluación al día, nada abierto sigue siendo vacío: el informe sale como hoy")
        void nadaAbiertoSinSeguimiento() {
            laPruebaLista();
            when(prueba.deLaEvaluacion(any())).thenReturn(List.of());
            disponible(EVALUACION, true);

            assertThat(abierto.de(base(LOGRO, JEFA), PRUEBA)).isEmpty();
            verify(seguimientos, never()).vigente(any(), any(), any());
            verify(prueba, never()).paraElSeguimiento(any());
        }
    }

    @Nested
    @DisplayName("Pedirlo")
    class Pedirlo {

        private final Situacion deLaEvaluacion = new Situacion(EVALUACION, null, "he", null, null);

        @Test
        @DisplayName("lo normal: pide el de su etapa con su huella")
        void loNormal() {
            when(seguimientos.pedir(POSTULACION, PRUEBA, "hp")).thenReturn(true);
            Situacion s = new Situacion(PRUEBA, null, "hp", null, null);
            assertThat(abierto.pedir(POSTULACION, s)).isTrue();
            when(seguimientos.enCamino(POSTULACION, PRUEBA)).thenReturn(true);
            assertThat(abierto.enCamino(POSTULACION, s)).isTrue();
        }

        @Test
        @DisplayName("si la prueba espera a la evaluación, pide primero el de la evaluación, una sola vez")
        void primeroLaEvaluacion() {
            Situacion s = new Situacion(PRUEBA, null, "hp", null, deLaEvaluacion);
            when(seguimientos.enCamino(POSTULACION, EVALUACION)).thenReturn(false);
            when(seguimientos.pedir(POSTULACION, EVALUACION, "he")).thenReturn(true);

            assertThat(abierto.pedir(POSTULACION, s)).isTrue();
            assertThat(abierto.enCamino(POSTULACION, s)).isFalse();
            verify(seguimientos, never()).pedir(POSTULACION, PRUEBA, "hp");

            when(seguimientos.enCamino(POSTULACION, EVALUACION)).thenReturn(true);
            assertThat(abierto.pedir(POSTULACION, s)).isTrue();
            verify(seguimientos).pedir(POSTULACION, EVALUACION, "he");
        }
    }
}
