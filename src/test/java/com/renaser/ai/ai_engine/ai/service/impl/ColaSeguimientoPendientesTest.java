package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.messaging.TrabajoIaPublisher;
import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.repository.TrabajoIaRepository;
import com.renaser.ai.ai_engine.ai.service.AgenteSeleccion;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.informe.service.SeguimientoTrasCalificar;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El seguimiento de pendientes en la cola (V94): va antes que el redactor de la evaluación y de la
 * prueba, lo pide él al terminar —bien o agotando sus reintentos— y nada de esto frena la etapa.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La cola: el seguimiento de pendientes")
class ColaSeguimientoPendientesTest {

    private static final long POSTULACION = 88L;
    private static final String SEGUIMIENTO = ColaCalificacionIa.AGENTE_SEGUIMIENTO_PENDIENTES;
    private static final String REDACTOR = ColaCalificacionIa.AGENTE_REDACTOR_INFORME;

    @Mock private TrabajoIaRepository trabajos;
    @Mock private RegistroTrabajosIa registro;
    @Mock private TrabajoIaPublisher publicador;
    @Mock private PuenteCalificacionIa puente;
    @Mock private com.renaser.ai.ai_engine.perfil.service.PuenteLecturaCvPerfil puenteDelPerfil;
    @Mock private TopeMensualIa tope;
    @Mock private OrganizacionRepository organizaciones;
    @Mock private SeguimientoTrasCalificar seguimiento;
    @Mock private AgenteSeleccion potencialRiesgo;
    @Mock private AgenteSeleccion pruebaPuesto;
    @Mock private AgenteSeleccion seguidor;

    private ColaCalificacionIaImpl cola;

    @BeforeEach
    void armar() {
        lenient().when(potencialRiesgo.codigo()).thenReturn(AgentePotencialRiesgo.CODIGO_AGENTE);
        lenient().when(pruebaPuesto.codigo()).thenReturn(AgentePruebaPuesto.CODIGO_AGENTE);
        lenient().when(seguidor.codigo()).thenReturn(SEGUIMIENTO);
        lenient().when(puente.organizacionDe(POSTULACION)).thenReturn(1L);
        cola = conLaCalificacion(true);
    }

    private ColaCalificacionIaImpl conLaCalificacion(boolean habilitada) {
        return new ColaCalificacionIaImpl(trabajos, registro, publicador, puente, puenteDelPerfil, tope,
                organizaciones, List.of(potencialRiesgo, pruebaPuesto, seguidor), seguimiento, habilitada, 3, 15);
    }

    private static TrabajoIa trabajo(long id, String agente, String estado, String referencia) {
        return TrabajoIa.builder().id(id).postulacionId(POSTULACION).organizacionId(1L)
                .agenteCodigo(agente).estado(estado).modo("FINA").referenciaTabla(referencia).intentos(1)
                .creadoEn(Instant.now()).build();
    }

    @Test
    @DisplayName("Al cerrar el retrato con dudas que seguir, la evaluación espera al seguimiento: solo se redacta el CV")
    void conDudasLaEvaluacionEsperaAlSeguimiento() {
        when(registro.tomar(1L)).thenReturn(Optional.of(trabajo(1L, AgentePotencialRiesgo.CODIGO_AGENTE, "EN_CURSO", null)));
        when(puente.tieneEvaluacionEntregada(POSTULACION)).thenReturn(true);
        when(seguimiento.pedirElSeguimiento(POSTULACION, "EVALUACION")).thenReturn(true);

        cola.ejecutar(1L);

        verify(registro).crearDePostulacionConReferencia(1L, POSTULACION, REDACTOR, "informe_cv", "FINA");
        verify(registro, never()).crearDePostulacionConReferencia(1L, POSTULACION, REDACTOR, "informe_evaluacion", "FINA");
        verify(seguimiento, never()).pedirElSeguimiento(POSTULACION, "CV");
    }

    @Test
    @DisplayName("AC-05 · sin nada que seguir, el redactor de la evaluación se pide como hoy")
    void sinDudasElRedactorComoHoy() {
        when(registro.tomar(1L)).thenReturn(Optional.of(trabajo(1L, AgentePotencialRiesgo.CODIGO_AGENTE, "EN_CURSO", null)));
        when(puente.tieneEvaluacionEntregada(POSTULACION)).thenReturn(true);
        when(seguimiento.pedirElSeguimiento(POSTULACION, "EVALUACION")).thenReturn(false);

        cola.ejecutar(1L);

        verify(registro).crearDePostulacionConReferencia(1L, POSTULACION, REDACTOR, "informe_evaluacion", "FINA");
    }

    @Test
    @DisplayName("Si preguntar por el seguimiento falla, el redactor de la prueba se pide igual")
    void siElSeguimientoFallaAlPedirseElRedactorVaIgual() {
        when(registro.tomar(1L)).thenReturn(Optional.of(trabajo(1L, AgentePruebaPuesto.CODIGO_AGENTE, "EN_CURSO", null)));
        when(seguimiento.pedirElSeguimiento(POSTULACION, "PRUEBA")).thenThrow(new IllegalStateException("base caída"));

        cola.ejecutar(1L);

        verify(registro).crearDePostulacionConReferencia(1L, POSTULACION, REDACTOR, "informe_prueba", "FINA");
    }

    @Test
    @DisplayName("Al calificarse la prueba con dudas abiertas, el redactor espera al seguimiento")
    void laPruebaEsperaAlSeguimiento() {
        when(registro.tomar(1L)).thenReturn(Optional.of(trabajo(1L, AgentePruebaPuesto.CODIGO_AGENTE, "EN_CURSO", null)));
        when(seguimiento.pedirElSeguimiento(POSTULACION, "PRUEBA")).thenReturn(true);

        cola.ejecutar(1L);

        verify(registro, never()).crearDePostulacionConReferencia(any(), any(), eq(REDACTOR), anyString(), anyString());
    }

    @Test
    @DisplayName("AC-14 · al terminar el seguimiento, el informe pide al redactor; la cola no lo encola por su cuenta")
    void alTerminarElSeguimientoPideAlRedactor() {
        when(registro.tomar(2L)).thenReturn(Optional.of(trabajo(2L, SEGUIMIENTO, "EN_CURSO", "seguimiento_evaluacion")));

        cola.ejecutar(2L);

        verify(seguidor).ejecutar(any(TrabajoIa.class));
        verify(registro).terminar(2L);
        verify(seguimiento).trasElSeguimiento(POSTULACION, "EVALUACION");
        verify(registro, never()).crearDePostulacionConReferencia(any(), any(), eq(REDACTOR), anyString(), anyString());
    }

    @Test
    @DisplayName("AC-07 · si el seguimiento agota sus reintentos, el redactor se pide igual")
    void siSeAgotaElRedactorSePideIgual() {
        when(registro.tomar(2L)).thenReturn(Optional.of(trabajo(2L, SEGUIMIENTO, "EN_CURSO", "seguimiento_prueba")));
        doThrow(new IllegalStateException("el modelo no responde")).when(seguidor).ejecutar(any(TrabajoIa.class));
        when(registro.fallar(eq(2L), anyInt(), anyString())).thenReturn(false);

        cola.ejecutar(2L);

        verify(seguimiento).trasElSeguimiento(POSTULACION, "PRUEBA");
        verify(publicador, never()).publicar(2L);
    }

    @Test
    @DisplayName("Si al seguimiento le quedan intentos, se reintenta y todavía no se pide al redactor")
    void conIntentosSeReintenta() {
        when(registro.tomar(2L)).thenReturn(Optional.of(trabajo(2L, SEGUIMIENTO, "EN_CURSO", "seguimiento_prueba")));
        doThrow(new IllegalStateException("el modelo no responde")).when(seguidor).ejecutar(any(TrabajoIa.class));
        when(registro.fallar(eq(2L), anyInt(), anyString())).thenReturn(true);

        cola.ejecutar(2L);

        verify(publicador).publicar(2L);
        verify(seguimiento, never()).trasElSeguimiento(any(), any());
    }

    @Test
    @DisplayName("Si pedir al redactor tras el seguimiento falla, la cola sigue: se pedirá al abrir el informe")
    void trasElSeguimientoNoRompeLaCola() {
        when(registro.tomar(2L)).thenReturn(Optional.of(trabajo(2L, SEGUIMIENTO, "EN_CURSO", null)));
        doThrow(new IllegalArgumentException("No hay informe de «»")).when(seguimiento).trasElSeguimiento(POSTULACION, "");

        cola.ejecutar(2L);

        verify(registro).terminar(2L);
    }

    @Test
    @DisplayName("Encolar el seguimiento usa su propia referencia, y uno vivo lo frena")
    void encolarYVivo() {
        when(registro.crearDePostulacionConReferencia(1L, POSTULACION, SEGUIMIENTO, "seguimiento_prueba", "FINA"))
                .thenReturn(Optional.of(trabajo(5L, SEGUIMIENTO, "PENDIENTE", "seguimiento_prueba")))
                .thenReturn(Optional.empty());

        assertThat(cola.encolarSeguimientoPendientes(POSTULACION, "PRUEBA")).isTrue();
        verify(publicador).publicar(5L);
        assertThat(cola.encolarSeguimientoPendientes(POSTULACION, "PRUEBA")).isFalse();

        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(5L, SEGUIMIENTO, "EN_ESPERA", "seguimiento_prueba"),
                trabajo(6L, REDACTOR, "PENDIENTE", "informe_evaluacion"),
                trabajo(7L, SEGUIMIENTO, "TERMINADO", "seguimiento_evaluacion")));
        assertThat(cola.seguimientoPendientesVivo(POSTULACION, "PRUEBA")).isTrue();
        assertThat(cola.seguimientoPendientesVivo(POSTULACION, "EVALUACION")).isFalse();
        assertThat(cola.redactorDelInformeVivo(POSTULACION, "EVALUACION")).isTrue();
    }

    @Test
    @DisplayName("Con la IA apagada no se encola ningún seguimiento")
    void conLaIaApagada() {
        assertThat(conLaCalificacion(false).encolarSeguimientoPendientes(POSTULACION, "EVALUACION")).isFalse();
        verify(registro, never()).crearDePostulacionConReferencia(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("El seguimiento no cuenta para «cómo va» el retrato")
    void noCuentaParaComoVa() {
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(5L, SEGUIMIENTO, "EN_CURSO", "seguimiento_evaluacion")));

        assertThat(cola.comoVa(POSTULACION)).isEqualTo("SIN_EMPEZAR");
    }
}
