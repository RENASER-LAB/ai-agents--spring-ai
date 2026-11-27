package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Criterio;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Enlace;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EstadoDeUnInforme;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.service.ArmadoDelInforme.Armado;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Bloque;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Punto;
import com.renaser.ai.ai_engine.informe.service.LoComunDelInforme.Base;
import com.renaser.ai.ai_engine.informe.service.LoQueSigueAbierto.Situacion;
import com.renaser.ai.ai_engine.informe.service.PendientesEntreEtapas.Duda;
import com.renaser.ai.ai_engine.informe.service.PendientesEntreEtapas.Resultado;
import com.renaser.ai.ai_engine.informe.service.RedaccionDelInforme.Textos;
import com.renaser.ai.ai_engine.postulacion.entity.Cv;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;

import org.junit.jupiter.api.BeforeEach;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El armado único que leen el visor y el PDF: nada si la etapa no está lista, los textos del
 * redactor si se escribieron con estos datos, pedirlos sin esperarlos si faltan, y que pedirlos
 * nunca tumbe un informe.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("El armado del informe por etapa")
class ArmadoDelInformeTest {

    private static final Long POSTULACION = 10L;
    private static final List<Enlace> REDES = List.of(new Enlace("LinkedIn", "https://linkedin.com/in/ana", "LINKEDIN"));

    @Mock private PostulacionRepository postulaciones;
    @Mock private LoComunDelInforme comun;
    @Mock private ArmadoDelCv cv;
    @Mock private ArmadoDeLaEvaluacion evaluacion;
    @Mock private ArmadoDeLaPrueba prueba;
    @Mock private TextosDelInforme textos;
    @Mock private LoQueSigueAbierto abierto;

    @InjectMocks private ArmadoDelInforme armado;

    private final Postulacion postulacion = Postulacion.builder().id(POSTULACION).build();
    private final Base base = new Base(postulacion, null, null, "Renaser", "Ana", null,
            Cv.builder().id(7L).archivoOriginalId(33L).build(), null, List.of(), null, List.of(), List.of(), null);

    @BeforeEach
    void leer() {
        when(comun.leer(postulacion)).thenReturn(base);
        when(comun.redes(base)).thenReturn(REDES);
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(postulacion));
    }

    @Test
    @DisplayName("cada etapa pregunta su estado a su propio armado")
    void estados() {
        EstadoDeUnInforme delCv = new EstadoDeUnInforme("CV", true, null, BigDecimal.TEN);
        EstadoDeUnInforme deLaEvaluacion = new EstadoDeUnInforme("EVALUACION", false, "x", null);
        EstadoDeUnInforme deLaPrueba = new EstadoDeUnInforme("PRUEBA", false, "y", null);
        when(cv.estado(base)).thenReturn(delCv);
        when(evaluacion.estado(base)).thenReturn(deLaEvaluacion);
        when(prueba.estado(base)).thenReturn(deLaPrueba);

        assertThat(armado.estado(postulacion, EtapaDelInforme.CV)).isEqualTo(delCv);
        assertThat(armado.estado(postulacion, EtapaDelInforme.EVALUACION)).isEqualTo(deLaEvaluacion);
        assertThat(armado.estado(postulacion, EtapaDelInforme.PRUEBA)).isEqualTo(deLaPrueba);
    }

    @Test
    @DisplayName("si la etapa no está lista no hay informe, ni para descargar ni para redactar")
    void noLista() {
        when(evaluacion.estado(base)).thenReturn(new EstadoDeUnInforme("EVALUACION", false, "x", null));

        assertThat(armado.armar(postulacion, EtapaDelInforme.EVALUACION, true)).isEmpty();
        assertThat(armado.paraRedactar(POSTULACION, EtapaDelInforme.EVALUACION)).isEmpty();
        assertThat(armado.paraRedactar(99L, EtapaDelInforme.EVALUACION)).isEmpty();
        verify(evaluacion, never()).armar(any(), any());
    }

    @Test
    @DisplayName("con textos escritos para estos datos los usa, no pide otros y lleva las redes y el archivo del CV")
    void conTextos() {
        Informe delCv = InformesDePrueba.cv(2);
        listo(EtapaDelInforme.CV, delCv);
        String linea = "Redujo el tiempo de contratación de 30 a 18 días.";
        when(textos.vigentes(eq(POSTULACION), eq(EtapaDelInforme.CV), anyString())).thenReturn(Optional.of(
                new Textos(null, Map.of("Resultados demostrables", linea), List.of(), 0)));

        Armado hecho = armado.armar(postulacion, EtapaDelInforme.CV, true).orElseThrow();

        assertThat(hecho.archivoCvId()).isEqualTo(33L);
        assertThat(hecho.informe().enlaces()).isEqualTo(REDES);
        assertThat(hecho.informe().textosEnCamino()).isFalse();
        assertThat(hecho.informe().evaluado().criterios()).extracting(Criterio::texto).first().isEqualTo(linea);
        verify(textos, never()).pedir(any(), any(), any());
    }

    @Test
    @DisplayName("sin textos los pide al descargar, o solo pregunta si vienen en camino, y mientras van las explicaciones")
    void sinTextos() {
        listo(EtapaDelInforme.PRUEBA, InformesDePrueba.prueba(List.of()));
        when(textos.pedir(eq(POSTULACION), eq(EtapaDelInforme.PRUEBA), anyString())).thenReturn(true);

        assertThat(armado.armar(postulacion, EtapaDelInforme.PRUEBA, true).orElseThrow().informe().textosEnCamino())
                .isTrue();

        when(textos.enCamino(POSTULACION, EtapaDelInforme.PRUEBA)).thenReturn(false);
        assertThat(armado.armar(postulacion, EtapaDelInforme.PRUEBA, false).orElseThrow().informe().textosEnCamino())
                .isFalse();
        verify(textos).enCamino(POSTULACION, EtapaDelInforme.PRUEBA);
    }

    @Test
    @DisplayName("si pedir los textos falla, el informe sale igual con las explicaciones")
    void pedirFalla() {
        listo(EtapaDelInforme.EVALUACION, InformesDePrueba.evaluacion());
        when(textos.pedir(any(), any(), any())).thenThrow(new IllegalStateException("cola caída"));

        Armado hecho = armado.armar(postulacion, EtapaDelInforme.EVALUACION, true).orElseThrow();

        assertThat(hecho.informe().textosEnCamino()).isFalse();
        assertThat(hecho.informe().etapa()).isEqualTo(InformesDePrueba.evaluacion().etapa());
    }

    @Test
    @DisplayName("sin nada que redactar no pide nada; sin CV no hay archivo que enlazar")
    void nadaQueRedactar() {
        Base sinCv = new Base(postulacion, null, null, null, "Ana", null, null, null, List.of(), null, List.of(),
                List.of(), null);
        when(comun.leer(postulacion)).thenReturn(sinCv);
        Informe vacio = InformesDePrueba.prueba(List.of());
        Informe sinNada = new Informe(vacio.etapa(), vacio.titulo(), null, null, POSTULACION, "Ana", null,
                vacio.generadoEn(), vacio.nota(), null, List.of(), List.of(), null, null, null, List.of(), List.of(),
                null, List.of(), null, null, null, false, "x.pdf");
        when(prueba.estado(sinCv)).thenReturn(new EstadoDeUnInforme("PRUEBA", true, null, null));
        when(prueba.armar(eq(sinCv), any())).thenReturn(sinNada);

        Armado hecho = armado.armar(postulacion, EtapaDelInforme.PRUEBA, true).orElseThrow();

        assertThat(hecho.archivoCvId()).isNull();
        verify(textos, never()).pedir(any(), any(), any());
        verify(textos, never()).enCamino(any(), any());
    }

    @Test
    @DisplayName("para el redactor da el informe sin textos de la etapa lista")
    void paraRedactar() {
        Informe delCv = InformesDePrueba.cv(1);
        listo(EtapaDelInforme.CV, delCv);

        assertThat(armado.paraRedactar(POSTULACION, EtapaDelInforme.CV)).contains(delCv);
        verify(textos, never()).vigentes(any(), any(), any());
    }

    // ================================================================ el seguimiento (V94)

    @Nested
    @DisplayName("Con el seguimiento de pendientes")
    class ConElSeguimiento {

        private final Resultado resuelta = new Resultado(List.of(new Duda("D1", "CV", "Excel avanzado", "DECLARADA",
                "Cuéntame un reporte.", PendientesEntreEtapas.RESUELTA, 1, null, "Armé por mi cuenta un tablero",
                "Cuenta un reporte propio.", "EVALUACION")), 0, List.of());

        private Situacion alDia() {
            return new Situacion(EtapaDelInforme.EVALUACION, null, "hs", resuelta, null);
        }

        private Situacion sinLlegar() {
            return new Situacion(EtapaDelInforme.EVALUACION, null, "hs", null, null);
        }

        private Punto primeraDuda(Informe informe) {
            return informe.bloques().stream().filter(b -> "PIDIO_EL_CV".equals(b.codigo()))
                    .map(Bloque::puntos).findFirst().orElseThrow().get(0);
        }

        @Test
        @DisplayName("AC-01 · al día: sus estados van en el informe y el redactor se pide con esos datos")
        void alDiaVaEnElInforme() {
            listo(EtapaDelInforme.EVALUACION, InformesDePrueba.evaluacion());
            when(abierto.de(base, EtapaDelInforme.EVALUACION)).thenReturn(Optional.of(alDia()));
            when(textos.pedir(eq(POSTULACION), eq(EtapaDelInforme.EVALUACION), anyString())).thenReturn(true);

            Armado hecho = armado.armar(postulacion, EtapaDelInforme.EVALUACION, true).orElseThrow();

            assertThat(primeraDuda(hecho.informe()).etiqueta()).isEqualTo("Resuelto");
            String conEstados = RedaccionDelInforme.huella(RedaccionDelInforme.insumo(
                    PendientesEntreEtapas.aplicar(InformesDePrueba.evaluacion(), resuelta)));
            verify(textos).pedir(POSTULACION, EtapaDelInforme.EVALUACION, conEstados);
            verify(abierto, never()).pedir(any(), any());
        }

        @Test
        @DisplayName("AC-14 · AC-18 · en camino: sale con «Por contrastar», no se pide al redactor y el visor vuelve a preguntar")
        void enCaminoNoPideAlRedactor() {
            listo(EtapaDelInforme.EVALUACION, InformesDePrueba.evaluacion());
            when(abierto.de(base, EtapaDelInforme.EVALUACION)).thenReturn(Optional.of(sinLlegar()));
            when(abierto.pedir(eq(POSTULACION), any())).thenReturn(true);

            Armado hecho = armado.armar(postulacion, EtapaDelInforme.EVALUACION, true).orElseThrow();

            assertThat(hecho.informe().textosEnCamino()).isTrue();
            assertThat(primeraDuda(hecho.informe()).etiqueta()).isEqualTo("Por contrastar");
            verify(textos, never()).pedir(any(), any(), any());
            verify(textos, never()).enCamino(any(), any());
        }

        @Test
        @DisplayName("sin pedir, solo pregunta si viene en camino")
        void soloPregunta() {
            listo(EtapaDelInforme.EVALUACION, InformesDePrueba.evaluacion());
            when(abierto.de(base, EtapaDelInforme.EVALUACION)).thenReturn(Optional.of(sinLlegar()));
            when(abierto.enCamino(eq(POSTULACION), any())).thenReturn(true);

            assertThat(armado.armar(postulacion, EtapaDelInforme.EVALUACION, false).orElseThrow().informe()
                    .textosEnCamino()).isTrue();
            verify(abierto, never()).pedir(any(), any());
        }

        @Test
        @DisplayName("AC-07 · si no viene (falló o la IA está apagada), el redactor se pide igual con «Por contrastar»")
        void siNoVieneElRedactorVaIgual() {
            listo(EtapaDelInforme.EVALUACION, InformesDePrueba.evaluacion());
            when(abierto.de(base, EtapaDelInforme.EVALUACION)).thenReturn(Optional.of(sinLlegar()));
            when(abierto.pedir(eq(POSTULACION), any())).thenReturn(false);

            armado.armar(postulacion, EtapaDelInforme.EVALUACION, true);

            verify(textos).pedir(eq(POSTULACION), eq(EtapaDelInforme.EVALUACION), anyString());
        }

        @Test
        @DisplayName("Spec 12 · prueba al día sin nada abierto: «Del CV» vacío, sin pedir seguimiento y el redactor una vez")
        void pruebaSinNadaAbierto() {
            Informe laPrueba = InformesDePrueba.prueba(List.of());
            listo(EtapaDelInforme.PRUEBA, laPrueba);
            Resultado nada = new Resultado(List.of(), 0, List.of());
            when(abierto.de(base, EtapaDelInforme.PRUEBA))
                    .thenReturn(Optional.of(new Situacion(EtapaDelInforme.PRUEBA, null, "hp", nada, null)));
            when(textos.pedir(eq(POSTULACION), eq(EtapaDelInforme.PRUEBA), anyString())).thenReturn(true);

            Informe informe = armado.armar(postulacion, EtapaDelInforme.PRUEBA, true).orElseThrow().informe();

            assertThat(informe.bloques()).extracting(Bloque::codigo).doesNotContain(PendientesEntreEtapas.BLOQUE_ACLARADO);
            assertThat(informe.bloques().stream().filter(b -> "PENDIENTE".equals(b.codigo())).findFirst().orElseThrow()
                    .puntos()).extracting(Punto::grupo).containsExactly("De la prueba", "De la evaluación");
            String conLoResuelto = RedaccionDelInforme.huella(RedaccionDelInforme.insumo(
                    PendientesEntreEtapas.aplicar(laPrueba, nada)));
            verify(textos).pedir(POSTULACION, EtapaDelInforme.PRUEBA, conLoResuelto);
            verify(abierto, never()).pedir(any(), any());
            verify(abierto, never()).enCamino(any(), any());
            assertThat(armado.pedirElSeguimiento(POSTULACION, "PRUEBA")).isFalse();
        }

        @Test
        @DisplayName("si leer o pedir el seguimiento falla, el informe sale igual que siempre")
        void fallasNoTumbanElInforme() {
            listo(EtapaDelInforme.EVALUACION, InformesDePrueba.evaluacion());
            when(abierto.de(base, EtapaDelInforme.EVALUACION)).thenThrow(new IllegalStateException("caída"));
            assertThat(primeraDuda(armado.armar(postulacion, EtapaDelInforme.EVALUACION, true).orElseThrow().informe())
                    .etiqueta()).isEqualTo("Por contrastar");

            org.mockito.Mockito.doReturn(Optional.of(sinLlegar())).when(abierto).de(base, EtapaDelInforme.EVALUACION);
            when(abierto.pedir(any(), any())).thenThrow(new IllegalStateException("cola caída"));
            assertThat(armado.armar(postulacion, EtapaDelInforme.EVALUACION, true)).isPresent();
            verify(textos, org.mockito.Mockito.atLeastOnce()).pedir(eq(POSTULACION), eq(EtapaDelInforme.EVALUACION), anyString());
        }

        @Test
        @DisplayName("el redactor recibe el informe con los estados del seguimiento vigente")
        void paraRedactarConEstados() {
            listo(EtapaDelInforme.EVALUACION, InformesDePrueba.evaluacion());
            when(abierto.de(base, EtapaDelInforme.EVALUACION)).thenReturn(Optional.of(alDia()));

            assertThat(primeraDuda(armado.paraRedactar(POSTULACION, EtapaDelInforme.EVALUACION).orElseThrow())
                    .etiqueta()).isEqualTo("Resuelto");
            verify(abierto, never()).pedir(any(), any());
            verify(abierto, never()).enCamino(any(), any());
        }

        @Test
        @DisplayName("para el agente: la situación de hoy, salvo si espera al de la evaluación o no hay postulación")
        void paraSeguir() {
            when(abierto.de(base, EtapaDelInforme.PRUEBA)).thenReturn(Optional.of(sinLlegar()));
            assertThat(armado.paraSeguir(POSTULACION, EtapaDelInforme.PRUEBA)).isPresent();

            Situacion esperando = new Situacion(EtapaDelInforme.PRUEBA, null, "hp", null, sinLlegar());
            when(abierto.de(base, EtapaDelInforme.PRUEBA)).thenReturn(Optional.of(esperando));
            assertThat(armado.paraSeguir(POSTULACION, EtapaDelInforme.PRUEBA)).isEmpty();
            assertThat(armado.paraSeguir(99L, EtapaDelInforme.PRUEBA)).isEmpty();
        }

        @Test
        @DisplayName("la cola: pide el seguimiento solo si hay algo abierto que no esté al día")
        void pedirElSeguimiento() {
            when(abierto.de(base, EtapaDelInforme.EVALUACION)).thenReturn(Optional.of(sinLlegar()));
            when(abierto.pedir(eq(POSTULACION), any())).thenReturn(true);
            assertThat(armado.pedirElSeguimiento(POSTULACION, "EVALUACION")).isTrue();

            when(abierto.de(base, EtapaDelInforme.EVALUACION)).thenReturn(Optional.of(alDia()));
            assertThat(armado.pedirElSeguimiento(POSTULACION, "EVALUACION")).isFalse();

            when(abierto.de(base, EtapaDelInforme.PRUEBA)).thenReturn(Optional.empty());
            assertThat(armado.pedirElSeguimiento(POSTULACION, "PRUEBA")).isFalse();
            assertThat(armado.pedirElSeguimiento(99L, "PRUEBA")).isFalse();
            verify(abierto).pedir(eq(POSTULACION), any());
        }

        @Test
        @DisplayName("AC-15 · tras el seguimiento de la evaluación se piden su redactor y, en cadena, lo de la prueba")
        void trasElSeguimiento() {
            listo(EtapaDelInforme.EVALUACION, InformesDePrueba.evaluacion());
            listo(EtapaDelInforme.PRUEBA, InformesDePrueba.prueba(List.of()));
            when(abierto.de(base, EtapaDelInforme.EVALUACION)).thenReturn(Optional.of(alDia()));

            armado.trasElSeguimiento(POSTULACION, "EVALUACION");

            verify(textos).pedir(eq(POSTULACION), eq(EtapaDelInforme.EVALUACION), anyString());
            verify(textos).pedir(eq(POSTULACION), eq(EtapaDelInforme.PRUEBA), anyString());

            armado.trasElSeguimiento(POSTULACION, "PRUEBA");
            verify(textos, org.mockito.Mockito.times(2)).pedir(eq(POSTULACION), eq(EtapaDelInforme.PRUEBA), anyString());
            verify(textos).pedir(eq(POSTULACION), eq(EtapaDelInforme.EVALUACION), anyString());

            armado.trasElSeguimiento(99L, "PRUEBA");
            verify(cv, never()).armar(any(), any());
        }
    }

    private void listo(EtapaDelInforme etapa, Informe informe) {
        EstadoDeUnInforme listo = new EstadoDeUnInforme(etapa.name(), true, null, BigDecimal.TEN);
        switch (etapa) {
            case CV -> {
                when(cv.estado(base)).thenReturn(listo);
                when(cv.armar(eq(base), any())).thenReturn(informe);
            }
            case EVALUACION -> {
                when(evaluacion.estado(base)).thenReturn(listo);
                when(evaluacion.armar(eq(base), any())).thenReturn(informe);
            }
            case PRUEBA -> {
                when(prueba.estado(base)).thenReturn(listo);
                when(prueba.armar(eq(base), any())).thenReturn(informe);
            }
        }
    }
}
