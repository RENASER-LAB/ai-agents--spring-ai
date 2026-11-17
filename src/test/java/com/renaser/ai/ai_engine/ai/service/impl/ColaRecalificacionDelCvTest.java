package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.messaging.TrabajoIaPublisher;
import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.repository.TrabajoIaRepository;
import com.renaser.ai.ai_engine.ai.service.AgenteSeleccion;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoPerfil;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoPerfil;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La recalificación del CV (spec la-ia-sabe-que-dia-es-hoy, AC-18 y AC-19): los agentes del CV
 * vuelven a correr en el carril RECALIFICA, el retrato se rehace solo si la nota del CV salió, y
 * se cierra sin mover a nadie de etapa.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("La cola: la recalificación del CV por la fecha")
class ColaRecalificacionDelCvTest {

    private static final long POSTULACION = 70L;
    private static final String RECALIFICA = ColaCalificacionIaImpl.RECALIFICA;
    private static final String EVIDENCIA = AgenteEvidenciaCv.CODIGO_AGENTE;
    private static final String DATOS = AgenteDatosCv.CODIGO_AGENTE;
    private static final String BUSQUEDA = AgenteBusquedaCv.CODIGO_AGENTE;
    private static final String RETRATO = AgentePotencialRiesgo.CODIGO_AGENTE;

    @Mock private TrabajoIaRepository trabajos;
    @Mock private RegistroTrabajosIa registro;
    @Mock private TrabajoIaPublisher publicador;
    @Mock private PuenteCalificacionIa puente;
    @Mock private com.renaser.ai.ai_engine.perfil.service.PuenteLecturaCvPerfil puenteDelPerfil;
    @Mock private TopeMensualIa tope;
    @Mock private OrganizacionRepository organizaciones;
    @Mock private AgenteSeleccion evidencia;
    @Mock private AgenteSeleccion datos;

    private ColaCalificacionIaImpl cola;

    @BeforeEach
    void armar() {
        when(evidencia.codigo()).thenReturn(EVIDENCIA);
        when(datos.codigo()).thenReturn(DATOS);
        when(puente.organizacionDe(POSTULACION)).thenReturn(1L);
        when(registro.crearSiNoHayUnoVivo(anyLong(), anyLong(), any(), any())).thenAnswer(llamada ->
                Optional.of(trabajo(100L + ((String) llamada.getArgument(2)).length(), llamada.getArgument(2),
                        "PENDIENTE", RECALIFICA)));
        cola = new ColaCalificacionIaImpl(trabajos, registro, publicador, puente, puenteDelPerfil,
                tope, organizaciones, List.of(evidencia, datos), true, 3, 15);
    }

    private static TrabajoIa trabajo(long id, String agente, String estado, String modo) {
        return TrabajoIa.builder().id(id).postulacionId(POSTULACION).organizacionId(1L)
                .agenteCodigo(agente).estado(estado).modo(modo).intentos(1)
                .creadoEn(Instant.now()).build();
    }

    @Nested
    @DisplayName("Encolar")
    class Encolar {

        @Test
        @DisplayName("crea los del CV en el carril RECALIFICA aunque ya hayan terminado, y los publica")
        void creaLosDelCv() {
            when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                    trabajo(1L, EVIDENCIA, "TERMINADO", "FINA"), trabajo(2L, RETRATO, "TERMINADO", "FINA")));

            assertThat(cola.recalificarElCv(POSTULACION, false)).isTrue();

            verify(registro).crearSiNoHayUnoVivo(1L, POSTULACION, EVIDENCIA, RECALIFICA);
            verify(registro).crearSiNoHayUnoVivo(1L, POSTULACION, DATOS, RECALIFICA);
            verify(registro, never()).crearSiNoHayUnoVivo(1L, POSTULACION, BUSQUEDA, RECALIFICA);
            verify(registro, never()).crearSiNoHayUnoVivo(1L, POSTULACION, AgenteEvaluador.CODIGO_AGENTE, RECALIFICA);
            verify(publicador, org.mockito.Mockito.times(2)).publicar(anyLong());
        }

        @Test
        @DisplayName("si la vacante busca algo en el CV, también la búsqueda")
        void conLaBusqueda() {
            assertThat(cola.recalificarElCv(POSTULACION, true)).isTrue();
            verify(registro).crearSiNoHayUnoVivo(1L, POSTULACION, BUSQUEDA, RECALIFICA);
        }

        @Test
        @DisplayName("con un trabajo vivo del retrato, en cualquier carril, no encola nada: confirmar dos veces no paga dos veces")
        void conUnoVivoNoEncola() {
            when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                    trabajo(1L, EVIDENCIA, "EN_CURSO", RECALIFICA)));

            assertThat(cola.recalificarElCv(POSTULACION, true)).isFalse();
            verify(registro, never()).crearSiNoHayUnoVivo(anyLong(), anyLong(), any(), any());
        }

        @Test
        @DisplayName("con la IA apagada no encola")
        void apagada() {
            ColaCalificacionIaImpl apagada = new ColaCalificacionIaImpl(trabajos, registro, publicador, puente,
                    puenteDelPerfil, tope, organizaciones, List.of(evidencia), false, 3, 15);
            assertThat(apagada.recalificarElCv(POSTULACION, false)).isFalse();
            verify(registro, never()).crearSiNoHayUnoVivo(anyLong(), anyLong(), any(), any());
        }
    }

    @Nested
    @DisplayName("El retrato al terminar")
    class ElRetrato {

        @Test
        @DisplayName("al terminar la evidencia del CV se pide el retrato del mismo carril, que espera a los del CV")
        void pideElRetrato() {
            TrabajoIa suyo = trabajo(10L, EVIDENCIA, "EN_CURSO", RECALIFICA);
            when(registro.tomar(10L)).thenReturn(Optional.of(suyo));
            when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(POSTULACION, EVIDENCIA, RECALIFICA))
                    .thenReturn(Optional.of(trabajo(10L, EVIDENCIA, "TERMINADO", RECALIFICA)));
            when(registro.crearElRetratoSiLosDemasAcabaron(eq(1L), eq(POSTULACION), any(), eq(RETRATO),
                    eq(RECALIFICA), eq(10L))).thenReturn(Optional.of(trabajo(11L, RETRATO, "PENDIENTE", RECALIFICA)));

            cola.ejecutar(10L);

            verify(registro).crearElRetratoSiLosDemasAcabaron(1L, POSTULACION, List.of(EVIDENCIA, DATOS, BUSQUEDA),
                    RETRATO, RECALIFICA, 10L);
            verify(publicador).publicar(11L);
        }

        @Test
        @DisplayName("AC-19 · si la evidencia del CV se agotó, el retrato no se rehace: conserva su calificación anterior")
        void sinNotaDelCvNoHayRetrato() {
            TrabajoIa suyo = trabajo(12L, DATOS, "EN_CURSO", RECALIFICA);
            when(registro.tomar(12L)).thenReturn(Optional.of(suyo));
            when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(POSTULACION, EVIDENCIA, RECALIFICA))
                    .thenReturn(Optional.of(trabajo(10L, EVIDENCIA, "FALLIDO", RECALIFICA)));

            cola.ejecutar(12L);

            verify(registro, never()).crearElRetratoSiLosDemasAcabaron(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("una evidencia que falla del todo tampoco arma el retrato")
        void laQueFallaNoLoArma() {
            TrabajoIa suyo = trabajo(13L, EVIDENCIA, "EN_CURSO", RECALIFICA);
            when(registro.tomar(13L)).thenReturn(Optional.of(suyo));
            org.mockito.Mockito.doThrow(new IllegalStateException("DeepSeek no contesta")).when(evidencia).ejecutar(suyo);
            when(registro.fallar(eq(13L), eq(3), any())).thenReturn(false);
            when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(POSTULACION, EVIDENCIA, RECALIFICA))
                    .thenReturn(Optional.of(trabajo(13L, EVIDENCIA, "FALLIDO", RECALIFICA)));

            cola.ejecutar(13L);

            verify(registro, never()).crearElRetratoSiLosDemasAcabaron(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("el «cómo va» del retrato no la mira: su nota sigue valiendo mientras tanto")
        void comoVaNoLaMira() {
            when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                    trabajo(1L, RETRATO, "TERMINADO", "FINA"), trabajo(2L, EVIDENCIA, "EN_CURSO", RECALIFICA)));

            assertThat(cola.comoVa(POSTULACION)).isEqualTo("TERMINADA");
        }
    }

    @Nested
    @DisplayName("Cómo va")
    class ComoVa {

        @Test
        @DisplayName("en curso, detenida con su porqué, terminada; quien nunca la tuvo no sale")
        void estados() {
            when(trabajos.findByPostulacionIdInOrderByIdAsc(List.of(1L, 2L, 3L, 4L, 5L))).thenReturn(List.of(
                    enOtra(1L, 20L, EVIDENCIA, "EN_ESPERA"),
                    enOtra(2L, 21L, EVIDENCIA, "FALLIDO"),
                    enOtra(3L, 22L, EVIDENCIA, "TERMINADO"), enOtra(3L, 23L, RETRATO, "TERMINADO"),
                    enOtra(4L, 24L, EVIDENCIA, "TERMINADO"), enOtra(4L, 25L, RETRATO, "FALLIDO"),
                    // Del carril de las preguntas propias: no es de esta recalificación.
                    enOtra(5L, 26L, AgenteEvaluador.CODIGO_AGENTE, "FALLIDO")));
            when(registro.motivoDelUltimoFallo(21L)).thenReturn(Optional.of("La cuenta del proveedor no tiene saldo (402)."));
            when(registro.motivoDelUltimoFallo(25L)).thenReturn(Optional.empty());

            Map<Long, ColaCalificacionIa.Seguimiento> como = cola.recalificacionDelCvDe(List.of(1L, 2L, 3L, 4L, 5L));

            assertThat(como.get(1L).estado()).isEqualTo("EN_CURSO");
            assertThat(como.get(2L)).isEqualTo(new ColaCalificacionIa.Seguimiento("DETENIDA",
                    "La cuenta del proveedor no tiene saldo (402)."));
            assertThat(como.get(3L).estado()).isEqualTo("TERMINADA");
            assertThat(como.get(4L).estado()).isEqualTo("DETENIDA");
            assertThat(como.get(4L).motivo()).contains("retrato");
            assertThat(como).doesNotContainKey(5L);
            assertThat(cola.recalificacionDelCvDe(List.of())).isEmpty();
        }

        @Test
        @DisplayName("pregunta al registro qué ejecuciones ya llevaban la fecha de hoy")
        void conLaFechaDeHoy() {
            when(registro.conLaFechaDeHoy(Set.of(1L, 2L))).thenReturn(Set.of(2L));
            assertThat(cola.conLaFechaDeHoy(Set.of(1L, 2L))).containsExactly(2L);
        }

        private TrabajoIa enOtra(long postulacionId, long id, String agente, String estado) {
            return TrabajoIa.builder().id(id).postulacionId(postulacionId).organizacionId(1L)
                    .agenteCodigo(agente).estado(estado).modo(RECALIFICA).intentos(1).creadoEn(Instant.now()).build();
        }
    }

    @Nested
    @DisplayName("El retrato que sale de ella")
    class AgenteDelRetrato {

        @Mock private EjecutorAgenteIa ejecutor;

        @Test
        @DisplayName("AC-18 · en el carril RECALIFICA se cierra sin mover a nadie de etapa; en el de siempre, como siempre")
        void sinMoverDeEtapa() {
            AgentePotencialRiesgo agente = new AgentePotencialRiesgo(puente, ejecutor);
            ResultadoPerfil resultado = mock(ResultadoPerfil.class);
            when(puente.insumoPerfil(POSTULACION)).thenReturn(mock(InsumoPerfil.class));
            when(ejecutor.ejecutar(any(), any(), any(), any(), eq(ResultadoPerfil.class), anyBoolean()))
                    .thenReturn(new EjecutorAgenteIa.Ejecutado<>(5L, resultado));

            agente.ejecutar(trabajo(30L, RETRATO, "EN_CURSO", RECALIFICA));
            verify(puente).cerrarPerfilIntegralSinMover(POSTULACION, 5L, resultado);
            verify(puente, never()).cerrarPerfilIntegral(anyLong(), anyLong(), any());

            agente.ejecutar(trabajo(31L, RETRATO, "EN_CURSO", "FINA"));
            verify(puente).cerrarPerfilIntegral(POSTULACION, 5L, resultado);
        }
    }
}
