package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.messaging.TrabajoIaPublisher;
import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.repository.TrabajoIaRepository;
import com.renaser.ai.ai_engine.ai.service.AgenteSeleccion;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La búsqueda de «Qué buscar en el CV» en la cola (V80): va junto a EVIDENCIA_CV cuando la
 * vacante tiene líneas, el retrato la espera, y la rebúsqueda va por su carril sin mover a nadie.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La cola: la búsqueda de «Qué buscar en el CV»")
class ColaBusquedaCvTest {

    private static final long POSTULACION = 77L;

    @Mock private TrabajoIaRepository trabajos;
    @Mock private RegistroTrabajosIa registro;
    @Mock private TrabajoIaPublisher publicador;
    @Mock private PuenteCalificacionIa puente;
    @Mock private com.renaser.ai.ai_engine.perfil.service.PuenteLecturaCvPerfil puenteDelPerfil;
    @Mock private TopeMensualIa tope;
    @Mock private OrganizacionRepository organizaciones;
    @Mock private AgenteSeleccion busqueda;
    @Mock private AgenteSeleccion evidencia;

    private ColaCalificacionIaImpl cola;

    @BeforeEach
    void armar() {
        lenient().when(busqueda.codigo()).thenReturn(AgenteBusquedaCv.CODIGO_AGENTE);
        lenient().when(evidencia.codigo()).thenReturn(AgenteEvidenciaCv.CODIGO_AGENTE);
        lenient().when(puente.organizacionDe(POSTULACION)).thenReturn(1L);
        cola = new ColaCalificacionIaImpl(trabajos, registro, publicador, puente, puenteDelPerfil,
                tope, organizaciones, List.of(busqueda, evidencia), true, 3, 15);
    }

    private static TrabajoIa trabajo(long id, String agente, String estado, String modo) {
        return TrabajoIa.builder().id(id).postulacionId(POSTULACION).organizacionId(1L)
                .agenteCodigo(agente).estado(estado).modo(modo).intentos(1)
                .creadoEn(Instant.now()).build();
    }

    @Test
    @DisplayName("Con líneas en la vacante, la criba encola la búsqueda junto a EVIDENCIA_CV")
    void conLineasLaCribaEncolaLaBusqueda() {
        when(puente.hayQueBuscarEnElCv(POSTULACION)).thenReturn(true);
        when(registro.crearSiHaceFalta(eq(1L), eq(POSTULACION), anyString(), eq("FINA"), any()))
                .thenAnswer(i -> Optional.of(trabajo(5L, i.getArgument(2), "PENDIENTE", "FINA")));
        when(registro.crearSiNingunoVivo(1L, POSTULACION, AgenteBusquedaCv.CODIGO_AGENTE, "FINA"))
                .thenReturn(Optional.of(trabajo(9L, AgenteBusquedaCv.CODIGO_AGENTE, "PENDIENTE", "FINA")));

        assertThat(cola.encolarCribaCv(POSTULACION)).isTrue();

        verify(registro).crearSiNingunoVivo(1L, POSTULACION, AgenteBusquedaCv.CODIGO_AGENTE, "FINA");
        verify(publicador).publicar(9L);
    }

    @Test
    @DisplayName("Sin líneas, o ya buscada con las vigentes, no se encola nada ni se gasta IA")
    void sinLineasNoSeEncola() {
        when(puente.hayQueBuscarEnElCv(POSTULACION)).thenReturn(false);
        when(registro.crearSiHaceFalta(eq(1L), eq(POSTULACION), anyString(), eq("FINA"), any()))
                .thenAnswer(i -> Optional.of(trabajo(5L, i.getArgument(2), "PENDIENTE", "FINA")));

        cola.encolarCribaCv(POSTULACION);

        verify(registro, never()).crearSiNingunoVivo(anyLong(), anyLong(),
                eq(AgenteBusquedaCv.CODIGO_AGENTE), anyString());
    }

    @Test
    @DisplayName("Una búsqueda viva, aunque sea una rebúsqueda, no se duplica")
    void unaVivaNoSeDuplica() {
        when(puente.hayQueBuscarEnElCv(POSTULACION)).thenReturn(true);
        when(trabajos.findFirstByPostulacionIdAndAgenteCodigoOrderByIdDesc(
                POSTULACION, AgenteBusquedaCv.CODIGO_AGENTE))
                .thenReturn(Optional.of(trabajo(3L, AgenteBusquedaCv.CODIGO_AGENTE, "EN_CURSO", "REBUSCA")));
        when(registro.crearSiHaceFalta(eq(1L), eq(POSTULACION), anyString(), eq("FINA"), any()))
                .thenAnswer(i -> Optional.of(trabajo(5L, i.getArgument(2), "PENDIENTE", "FINA")));

        cola.encolarCribaCv(POSTULACION);

        verify(registro, never()).crearSiNingunoVivo(anyLong(), anyLong(),
                eq(AgenteBusquedaCv.CODIGO_AGENTE), anyString());
    }

    @Test
    @DisplayName("Al terminar la búsqueda de la criba se pregunta a la barrera, que la cuenta")
    void laBusquedaDeLaCribaPreguntaALaBarrera() {
        TrabajoIa suyo = trabajo(9L, AgenteBusquedaCv.CODIGO_AGENTE, "EN_CURSO", "FINA");
        when(registro.tomar(9L)).thenReturn(Optional.of(suyo));

        cola.ejecutar(9L);

        verify(registro).crearElRetratoSiLosDemasAcabaron(eq(1L), eq(POSTULACION),
                eq(List.of(AgenteDatosCv.CODIGO_AGENTE, AgenteEvidenciaCv.CODIGO_AGENTE,
                        AgenteEvaluador.CODIGO_AGENTE, AgenteBusquedaCv.CODIGO_AGENTE)),
                eq(AgentePotencialRiesgo.CODIGO_AGENTE), eq("FINA"), eq(9L));
    }

    @Test
    @DisplayName("Una rebúsqueda no arma el retrato al terminar: nadie se mueve de etapa")
    void laRebusquedaNoArmaElRetrato() {
        TrabajoIa suyo = trabajo(9L, AgenteBusquedaCv.CODIGO_AGENTE, "EN_CURSO", "REBUSCA");
        when(registro.tomar(9L)).thenReturn(Optional.of(suyo));

        cola.ejecutar(9L);

        verify(busqueda).ejecutar(suyo);
        verify(registro, never()).crearElRetratoSiLosDemasAcabaron(any(), any(), anyList(), any(),
                any(), any());
    }

    @Test
    @DisplayName("Volver a buscar crea la rebúsqueda; con una viva no paga otra")
    void volverABuscar() {
        when(registro.crearSiNingunoVivo(1L, POSTULACION, AgenteBusquedaCv.CODIGO_AGENTE, "REBUSCA"))
                .thenReturn(Optional.of(trabajo(11L, AgenteBusquedaCv.CODIGO_AGENTE, "PENDIENTE", "REBUSCA")))
                .thenReturn(Optional.empty());
        when(organizaciones.findById(1L)).thenReturn(Optional.empty());

        assertThat(cola.buscarDeNuevoEnElCv(POSTULACION)).isTrue();
        assertThat(cola.buscarDeNuevoEnElCv(POSTULACION)).isFalse();
        verify(publicador).publicar(11L);
    }

    @Test
    @DisplayName("Sin cupo de IA, la búsqueda queda en espera (la persona sigue «Pendiente»)")
    void sinCupoQuedaEnEspera() {
        when(registro.crearSiNingunoVivo(1L, POSTULACION, AgenteBusquedaCv.CODIGO_AGENTE, "REBUSCA"))
                .thenReturn(Optional.of(trabajo(11L, AgenteBusquedaCv.CODIGO_AGENTE, "PENDIENTE", "REBUSCA")));
        when(organizaciones.findById(1L)).thenReturn(Optional.empty());
        when(tope.sinCupo(1L)).thenReturn(true);
        when(registro.dejarEnEspera(11L)).thenReturn(true);

        cola.buscarDeNuevoEnElCv(POSTULACION);

        verify(registro).dejarEnEspera(11L);
        verify(publicador, never()).publicar(11L);
    }

    @Test
    @DisplayName("Cómo va la búsqueda: el último trabajo de cada uno, en cualquier modo")
    void comoVaLaBusqueda() {
        when(trabajos.findByPostulacionIdInOrderByIdAsc(List.of(1L, 2L, 3L, 4L, 5L))).thenReturn(List.of(
                trabajoDe(1L, 1L, "TERMINADO", "FINA"),
                trabajoDe(2L, 1L, "PENDIENTE", "REBUSCA"),
                trabajoDe(3L, 2L, "FALLIDO", "FINA"),
                trabajoDe(4L, 3L, "EN_ESPERA", "FINA"),
                TrabajoIa.builder().id(5L).postulacionId(4L)
                        .agenteCodigo(AgenteEvidenciaCv.CODIGO_AGENTE).estado("EN_CURSO").modo("FINA")
                        .build()));

        Map<Long, String> como = cola.comoVaLaBusqueda(List.of(1L, 2L, 3L, 4L, 5L));

        assertThat(como).containsExactlyInAnyOrderEntriesOf(Map.of(
                1L, "BUSCANDO", 2L, "FALLIDA", 3L, "EN_ESPERA"));
    }

    @Test
    @DisplayName("Una rebúsqueda viva no deja el retrato «en curso»")
    void laRebusquedaNoCuentaEnElRetrato() {
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgenteEvidenciaCv.CODIGO_AGENTE, "TERMINADO", "FINA"),
                trabajo(2L, AgenteBusquedaCv.CODIGO_AGENTE, "TERMINADO", "FINA"),
                trabajo(3L, AgentePotencialRiesgo.CODIGO_AGENTE, "TERMINADO", "FINA"),
                trabajo(4L, AgenteBusquedaCv.CODIGO_AGENTE, "EN_CURSO", "REBUSCA")));

        assertThat(cola.comoVa(POSTULACION)).isEqualTo("TERMINADA");
    }

    @Test
    @DisplayName("Una búsqueda de la criba viva sí deja el retrato «en curso»: el retrato la espera")
    void laBusquedaDeLaCribaCuentaEnElRetrato() {
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgenteEvidenciaCv.CODIGO_AGENTE, "TERMINADO", "FINA"),
                trabajo(2L, AgenteBusquedaCv.CODIGO_AGENTE, "EN_CURSO", "FINA")));

        assertThat(cola.comoVa(POSTULACION)).isEqualTo("EN_CURSO");
    }

    private static TrabajoIa trabajoDe(long id, long postulacion, String estado, String modo) {
        return TrabajoIa.builder().id(id).postulacionId(postulacion).organizacionId(1L)
                .agenteCodigo(AgenteBusquedaCv.CODIGO_AGENTE).estado(estado).modo(modo).intentos(1)
                .creadoEn(Instant.now()).build();
    }
}
