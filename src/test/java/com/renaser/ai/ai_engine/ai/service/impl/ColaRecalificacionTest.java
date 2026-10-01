package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.messaging.TrabajoIaPublisher;
import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.repository.TrabajoIaRepository;
import com.renaser.ai.ai_engine.ai.service.AgenteSeleccion;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La recalificación de las preguntas propias (V66) va por su propio carril: solo el
 * evaluador, sin armar el retrato al terminar (que movería a la persona de etapa), sin contar
 * en el «cómo va» del retrato, y con el porqué cuando se detiene.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La cola: el carril de la recalificación")
class ColaRecalificacionTest {

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
        when(evaluador.codigo()).thenReturn(AgenteEvaluador.CODIGO_AGENTE);
        lenient().when(puente.organizacionDe(POSTULACION)).thenReturn(1L);
        cola = new ColaCalificacionIaImpl(trabajos, registro, publicador, puente, puenteDelPerfil,
                tope, organizaciones, List.of(evaluador), true, 3, 15);
    }

    private static TrabajoIa trabajo(long id, String agente, String estado, String modo) {
        return TrabajoIa.builder().id(id).postulacionId(POSTULACION).organizacionId(1L)
                .agenteCodigo(agente).estado(estado).modo(modo).intentos(1)
                .creadoEn(Instant.now()).build();
    }

    @Test
    @DisplayName("Recalificar crea el trabajo del evaluador en su carril, aunque ya haya uno terminado")
    void recalificarCreaEnSuCarril() {
        TrabajoIa nuevo = trabajo(9L, AgenteEvaluador.CODIGO_AGENTE, "PENDIENTE", "RECALIFICA");
        when(registro.crearSiNoHayUnoVivo(1L, POSTULACION, AgenteEvaluador.CODIGO_AGENTE, "RECALIFICA"))
                .thenReturn(Optional.of(nuevo));

        assertThat(cola.recalificar(POSTULACION)).isTrue();
        verify(publicador).publicar(9L);
    }

    @Test
    @DisplayName("Al terminar una recalificación no se arma el retrato: nadie se mueve de etapa")
    void alTerminarNoArmaElRetrato() {
        TrabajoIa suyo = trabajo(9L, AgenteEvaluador.CODIGO_AGENTE, "EN_CURSO", "RECALIFICA");
        when(registro.tomar(9L)).thenReturn(Optional.of(suyo));

        cola.ejecutar(9L);

        verify(evaluador).ejecutar(suyo);
        verify(registro).terminar(9L);
        verify(registro, never()).crearElRetratoSiLosDemasAcabaron(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("El «cómo va» del retrato no mira las recalificaciones")
    void comoVaNoLaMira() {
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgentePotencialRiesgo.CODIGO_AGENTE, "TERMINADO", "FINA"),
                trabajo(2L, AgenteEvaluador.CODIGO_AGENTE, "EN_CURSO", "RECALIFICA")));

        assertThat(cola.comoVa(POSTULACION)).isEqualTo("TERMINADA");
    }

    @Test
    @DisplayName("Una detenida dice por qué: el tope, o el texto del fallo del proveedor")
    void laDetenidaDicePorQue() {
        when(trabajos.findByPostulacionIdInOrderByIdAsc(List.of(POSTULACION, 56L))).thenReturn(List.of(
                trabajo(1L, AgenteEvaluador.CODIGO_AGENTE, "FALLIDO", "RECALIFICA"),
                TrabajoIa.builder().id(2L).postulacionId(56L).organizacionId(1L)
                        .agenteCodigo(AgenteEvaluador.CODIGO_AGENTE).estado("EN_ESPERA")
                        .modo("RECALIFICA").build()));
        when(registro.motivoDelUltimoFallo(1L)).thenReturn(Optional.of(
                "El agente EVALUADOR no pudo hablar con DeepSeek: la cuenta del proveedor no tiene saldo (402)."));

        Map<Long, ColaCalificacionIa.Seguimiento> como = cola.recalificacionDe(List.of(POSTULACION, 56L));

        assertThat(como.get(POSTULACION).estado()).isEqualTo("DETENIDA");
        assertThat(como.get(POSTULACION).motivo()).contains("no tiene saldo (402)");
        assertThat(como.get(56L).estado()).isEqualTo("DETENIDA");
        assertThat(como.get(56L).motivo()).contains("tope mensual");
    }

    @Test
    @DisplayName("Sin cupo o con la IA apagada se sabe antes de guardar nada")
    void porQueNoSePuede() {
        when(tope.sinCupo(1L)).thenReturn(true);
        assertThat(cola.porQueNoSePuedeUsarLaIa(1L)).contains("no tiene saldo");

        ColaCalificacionIaImpl apagada = new ColaCalificacionIaImpl(trabajos, registro, publicador,
                puente, puenteDelPerfil, tope, organizaciones, List.of(evaluador), false, 3, 15);
        assertThat(apagada.porQueNoSePuedeUsarLaIa(1L)).contains("apagada")
                // QA-PP-06: lo usan también las recomendaciones; no puede hablar de calificar
                .doesNotContainIgnoringCase("calificar");
        assertThat(apagada.recalificar(POSTULACION)).isFalse();
        verify(registro, never()).crearSiNoHayUnoVivo(anyLong(), anyLong(), any(), any());
    }
}
