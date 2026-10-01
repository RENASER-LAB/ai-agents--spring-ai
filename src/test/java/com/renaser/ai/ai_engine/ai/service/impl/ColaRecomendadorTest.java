package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.messaging.TrabajoIaPublisher;
import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.repository.TrabajoIaRepository;
import com.renaser.ai.ai_engine.ai.service.AgenteSeleccion;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa.Seguimiento;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * La cola de las preguntas propias (V66): el RECOMENDADOR, que cuelga de la vacante, el
 * porqué de no poder usar la IA antes de guardar nada, y el seguimiento de la
 * recalificación que enseña el panel.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La cola: el recomendador y el seguimiento de las preguntas propias")
class ColaRecomendadorTest {

    private static final long ORGANIZACION = 1L;
    private static final long VACANTE = 40L;
    private static final long POSTULACION = 55L;

    @Mock private TrabajoIaRepository trabajos;
    @Mock private RegistroTrabajosIa registro;
    @Mock private TrabajoIaPublisher publicador;
    @Mock private PuenteCalificacionIa puente;
    @Mock private com.renaser.ai.ai_engine.perfil.service.PuenteLecturaCvPerfil puenteDelPerfil;
    @Mock private TopeMensualIa tope;
    @Mock private OrganizacionRepository organizaciones;
    @Mock private AgenteSeleccion evaluador;

    private ColaCalificacionIaImpl cola;

    @BeforeEach
    void armar() {
        lenient().when(evaluador.codigo()).thenReturn(AgenteEvaluador.CODIGO_AGENTE);
        cola = cola(true);
    }

    private ColaCalificacionIaImpl cola(boolean habilitada) {
        return new ColaCalificacionIaImpl(trabajos, registro, publicador, puente, puenteDelPerfil,
                tope, organizaciones, List.of(evaluador), habilitada, 3, 15);
    }

    private void organizacionActiva(boolean activa) {
        when(organizaciones.findById(ORGANIZACION)).thenReturn(Optional.of(
                Organizacion.builder().id(ORGANIZACION).esActiva(activa).build()));
    }

    private static TrabajoIa trabajo(long id, long postulacion, String agente, String estado,
                                     String modo) {
        return TrabajoIa.builder().id(id).postulacionId(postulacion).organizacionId(ORGANIZACION)
                .agenteCodigo(agente).estado(estado).modo(modo).intentos(1)
                .creadoEn(Instant.now()).build();
    }

    private static TrabajoIa delRecomendador(long id, String estado) {
        return TrabajoIa.builder().id(id).organizacionId(ORGANIZACION)
                .agenteCodigo(AgenteRecomendador.CODIGO_AGENTE).estado(estado).modo("FINA")
                .referenciaTabla("vacante").referenciaId(VACANTE).intentos(0)
                .creadoEn(Instant.now()).build();
    }

    // ---------------------------------------------------------------- porQueNoSePuedeUsarLaIa

    @Test
    @DisplayName("Con la empresa suspendida se dice antes de guardar nada")
    void empresaSuspendida() {
        organizacionActiva(false);

        assertThat(cola.porQueNoSePuedeUsarLaIa(ORGANIZACION)).contains("suspendida");
        verify(tope, never()).sinCupo(anyLong());
    }

    @Test
    @DisplayName("Activa y con cupo no hay ningún impedimento")
    void sinImpedimento() {
        organizacionActiva(true);
        when(tope.sinCupo(ORGANIZACION)).thenReturn(false);

        assertThat(cola.porQueNoSePuedeUsarLaIa(ORGANIZACION)).isNull();
    }

    // ---------------------------------------------------------------- recalificar

    @Test
    @DisplayName("Si ya hay una recalificación viva no se crea otra ni se publica nada")
    void recalificarConUnaViva() {
        when(puente.organizacionDe(POSTULACION)).thenReturn(ORGANIZACION);
        when(registro.crearSiNoHayUnoVivo(ORGANIZACION, POSTULACION,
                AgenteEvaluador.CODIGO_AGENTE, ColaCalificacionIaImpl.RECALIFICA))
                .thenReturn(Optional.empty());

        assertThat(cola.recalificar(POSTULACION)).isFalse();
        verifyNoInteractions(publicador);
    }

    // ---------------------------------------------------------------- recalificacionDe

    @Test
    @DisplayName("Sin postulaciones no se consulta la base")
    void recalificacionDeNadie() {
        assertThat(cola.recalificacionDe(List.of())).isEmpty();
        verifyNoInteractions(trabajos);
    }

    @Test
    @DisplayName("Solo cuenta el último trabajo de recalificación del evaluador de cada persona")
    void recalificacionDeCadaUna() {
        List<Long> ids = List.of(POSTULACION, 56L, 57L, 58L);
        when(trabajos.findByPostulacionIdInOrderByIdAsc(ids)).thenReturn(List.of(
                // Otro agente y otra pasada del evaluador: no son de la recalificación.
                trabajo(1L, POSTULACION, AgentePotencialRiesgo.CODIGO_AGENTE, "EN_CURSO", "RECALIFICA"),
                trabajo(2L, POSTULACION, AgenteEvaluador.CODIGO_AGENTE, "EN_CURSO", "FINA"),
                // Dos recalificaciones: manda la última.
                trabajo(3L, POSTULACION, AgenteEvaluador.CODIGO_AGENTE, "FALLIDO", "RECALIFICA"),
                trabajo(4L, POSTULACION, AgenteEvaluador.CODIGO_AGENTE, "TERMINADO", "RECALIFICA"),
                trabajo(5L, 56L, AgenteEvaluador.CODIGO_AGENTE, "PENDIENTE", "RECALIFICA"),
                trabajo(6L, 57L, AgenteEvaluador.CODIGO_AGENTE, "EN_CURSO", "RECALIFICA"),
                trabajo(7L, 58L, AgenteEvaluador.CODIGO_AGENTE, "EN_ESPERA", "RECALIFICA")));
        organizacionActiva(false);

        Map<Long, Seguimiento> como = cola.recalificacionDe(ids);

        assertThat(como).containsOnlyKeys(POSTULACION, 56L, 57L, 58L);
        assertThat(como.get(POSTULACION)).isEqualTo(new Seguimiento("TERMINADA", null));
        assertThat(como.get(56L)).isEqualTo(new Seguimiento("EN_CURSO", null));
        assertThat(como.get(57L)).isEqualTo(new Seguimiento("EN_CURSO", null));
        assertThat(como.get(58L).estado()).isEqualTo("DETENIDA");
        assertThat(como.get(58L).motivo()).contains("suspendida").contains("reactiven");
        verify(registro, never()).motivoDelUltimoFallo(anyLong());
    }

    @Test
    @DisplayName("Una fallida sin texto del proveedor dice que no pudo terminar")
    void fallidaSinMotivo() {
        when(trabajos.findByPostulacionIdInOrderByIdAsc(List.of(POSTULACION))).thenReturn(List.of(
                trabajo(3L, POSTULACION, AgenteEvaluador.CODIGO_AGENTE, "FALLIDO", "RECALIFICA")));
        when(registro.motivoDelUltimoFallo(3L)).thenReturn(Optional.empty());

        Seguimiento s = cola.recalificacionDe(List.of(POSTULACION)).get(POSTULACION);

        assertThat(s.estado()).isEqualTo("DETENIDA");
        assertThat(s.motivo()).isEqualTo("La IA no pudo terminar después de varios intentos.");
    }

    // ---------------------------------------------------------------- encolarRecomendador

    @Test
    @DisplayName("Con la IA apagada el recomendador no se encola")
    void recomendadorConLaIaApagada() {
        assertThat(cola(false).encolarRecomendador(ORGANIZACION, VACANTE)).isFalse();
        verifyNoInteractions(registro, publicador);
    }

    @Test
    @DisplayName("Si el índice frena un duplicado no se publica nada")
    void recomendadorDuplicadoPorElIndice() {
        when(registro.crearParaVacante(ORGANIZACION, AgenteRecomendador.CODIGO_AGENTE, VACANTE,
                ColaCalificacionIaImpl.FINA))
                .thenThrow(new DataIntegrityViolationException("uq_trabajo_vivo"));

        assertThat(cola.encolarRecomendador(ORGANIZACION, VACANTE)).isFalse();
        verifyNoInteractions(publicador);
    }

    @Test
    @DisplayName("Si ya hay una recomendación en curso no se crea otra")
    void recomendadorYaEnCurso() {
        when(registro.crearParaVacante(ORGANIZACION, AgenteRecomendador.CODIGO_AGENTE, VACANTE,
                ColaCalificacionIaImpl.FINA)).thenReturn(Optional.empty());

        assertThat(cola.encolarRecomendador(ORGANIZACION, VACANTE)).isFalse();
        verifyNoInteractions(publicador);
    }

    @Test
    @DisplayName("Con cupo y la empresa activa el recomendador se publica")
    void recomendadorSePublica() {
        when(registro.crearParaVacante(ORGANIZACION, AgenteRecomendador.CODIGO_AGENTE, VACANTE,
                ColaCalificacionIaImpl.FINA)).thenReturn(Optional.of(delRecomendador(12L, "PENDIENTE")));
        organizacionActiva(true);
        when(tope.sinCupo(ORGANIZACION)).thenReturn(false);

        assertThat(cola.encolarRecomendador(ORGANIZACION, VACANTE)).isTrue();
        verify(publicador).publicar(12L);
        verify(registro, never()).dejarEnEspera(any());
    }

    @Test
    @DisplayName("Sin cupo el recomendador queda en espera y no se publica")
    void recomendadorSinCupoQuedaEnEspera() {
        when(registro.crearParaVacante(ORGANIZACION, AgenteRecomendador.CODIGO_AGENTE, VACANTE,
                ColaCalificacionIaImpl.FINA)).thenReturn(Optional.of(delRecomendador(12L, "PENDIENTE")));
        organizacionActiva(true);
        when(tope.sinCupo(ORGANIZACION)).thenReturn(true);
        when(registro.dejarEnEspera(12L)).thenReturn(true);

        assertThat(cola.encolarRecomendador(ORGANIZACION, VACANTE)).isTrue();
        verify(publicador, never()).publicar(anyLong());
    }

    // ---------------------------------------------------------------- comoVaElRecomendador

    @Test
    @DisplayName("Sin ninguna pedida el recomendador está sin pedir")
    void recomendadorSinPedir() {
        when(trabajos.findFirstByReferenciaTablaAndReferenciaIdAndAgenteCodigoOrderByIdDesc(
                "vacante", VACANTE, AgenteRecomendador.CODIGO_AGENTE)).thenReturn(Optional.empty());

        assertThat(cola.comoVaElRecomendador(VACANTE)).isEqualTo(new Seguimiento("SIN_PEDIR", null));
    }

    @Test
    @DisplayName("Una en espera con la empresa activa es por el tope, y lo dice")
    void recomendadorDetenidoPorElTope() {
        when(trabajos.findFirstByReferenciaTablaAndReferenciaIdAndAgenteCodigoOrderByIdDesc(
                "vacante", VACANTE, AgenteRecomendador.CODIGO_AGENTE))
                .thenReturn(Optional.of(delRecomendador(12L, "EN_ESPERA")));
        organizacionActiva(true);

        Seguimiento s = cola.comoVaElRecomendador(VACANTE);

        assertThat(s.estado()).isEqualTo("DETENIDA");
        assertThat(s.motivo()).contains("tope mensual").contains("cuando haya cupo");
    }

    @Test
    @DisplayName("Una terminada se enseña como terminada")
    void recomendadorTerminado() {
        when(trabajos.findFirstByReferenciaTablaAndReferenciaIdAndAgenteCodigoOrderByIdDesc(
                "vacante", VACANTE, AgenteRecomendador.CODIGO_AGENTE))
                .thenReturn(Optional.of(delRecomendador(12L, "TERMINADO")));

        assertThat(cola.comoVaElRecomendador(VACANTE)).isEqualTo(new Seguimiento("TERMINADA", null));
    }
}
