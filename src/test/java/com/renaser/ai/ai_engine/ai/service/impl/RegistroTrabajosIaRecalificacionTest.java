package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.EjecucionIa;
import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.repository.EjecucionIaRepository;
import com.renaser.ai.ai_engine.ai.repository.TrabajoIaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El registro de la recalificación (V66): corregir la guía obliga a volver a calificar a
 * quien ya tiene nota, así que un trabajo TERMINADO no exime; solo lo frena uno vivo.
 * Y el porqué de un fallo se lee de la última ejecución fallida.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El registro de trabajos: la recalificación y el motivo del fallo")
class RegistroTrabajosIaRecalificacionTest {

    private static final long ORGANIZACION = 1L;
    private static final long POSTULACION = 55L;

    @Mock private TrabajoIaRepository trabajos;
    @Mock private EjecucionIaRepository ejecuciones;

    private RegistroTrabajosIa registro;

    @BeforeEach
    void armar() {
        registro = new RegistroTrabajosIa(trabajos, ejecuciones);
    }

    private void ultimoEs(String estado) {
        TrabajoIa ultimo = TrabajoIa.builder().id(7L).postulacionId(POSTULACION)
                .agenteCodigo(AgenteEvaluador.CODIGO_AGENTE).modo("RECALIFICA")
                .estado(estado).intentos(1).creadoEn(Instant.now()).build();
        when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                POSTULACION, AgenteEvaluador.CODIGO_AGENTE, "RECALIFICA"))
                .thenReturn(Optional.of(ultimo));
    }

    @Test
    @DisplayName("Sin ningún trabajo previo crea uno PENDIENTE que cuelga de la postulación")
    void sinPrevioCreaUnoPendiente() {
        when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                POSTULACION, AgenteEvaluador.CODIGO_AGENTE, "RECALIFICA"))
                .thenReturn(Optional.empty());
        when(trabajos.save(any(TrabajoIa.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<TrabajoIa> creado = registro.crearSiNoHayUnoVivo(ORGANIZACION, POSTULACION,
                AgenteEvaluador.CODIGO_AGENTE, "RECALIFICA");

        assertThat(creado).isPresent();
        TrabajoIa t = creado.get();
        assertThat(t.getEstado()).isEqualTo("PENDIENTE");
        assertThat(t.getOrganizacionId()).isEqualTo(ORGANIZACION);
        assertThat(t.getPostulacionId()).isEqualTo(POSTULACION);
        assertThat(t.getAgenteCodigo()).isEqualTo(AgenteEvaluador.CODIGO_AGENTE);
        assertThat(t.getModo()).isEqualTo("RECALIFICA");
        assertThat(t.getReferenciaTabla()).isEqualTo("postulacion");
        assertThat(t.getReferenciaId()).isEqualTo(POSTULACION);
        assertThat(t.getIntentos()).isZero();
        assertThat(t.getCreadoEn()).isNotNull();
    }

    @ParameterizedTest(name = "Uno {0} lo frena: dos a la vez pagarían dos veces")
    @ValueSource(strings = {"PENDIENTE", "EN_CURSO", "EN_ESPERA"})
    void unoVivoLoFrena(String estado) {
        ultimoEs(estado);

        assertThat(registro.crearSiNoHayUnoVivo(ORGANIZACION, POSTULACION,
                AgenteEvaluador.CODIGO_AGENTE, "RECALIFICA")).isEmpty();
        verify(trabajos, never()).save(any());
    }

    @ParameterizedTest(name = "Uno {0} no exime: se vuelve a calificar")
    @ValueSource(strings = {"TERMINADO", "FALLIDO"})
    void unoAcabadoNoExime(String estado) {
        ultimoEs(estado);
        when(trabajos.save(any(TrabajoIa.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<TrabajoIa> creado = registro.crearSiNoHayUnoVivo(ORGANIZACION, POSTULACION,
                AgenteEvaluador.CODIGO_AGENTE, "RECALIFICA");

        assertThat(creado).isPresent();
        ArgumentCaptor<TrabajoIa> guardado = ArgumentCaptor.forClass(TrabajoIa.class);
        verify(trabajos).save(guardado.capture());
        assertThat(guardado.getValue().getEstado()).isEqualTo("PENDIENTE");
        assertThat(guardado.getValue().getId()).isNull();
    }

    @Test
    @DisplayName("El motivo del fallo es el texto de error de la última ejecución fallida")
    void motivoDelUltimoFallo() {
        when(ejecuciones.findFirstByTrabajoIaIdAndEsExitosaFalseOrderByIdDesc(9L))
                .thenReturn(Optional.of(EjecucionIa.builder().error("sin saldo (402)").build()));

        assertThat(registro.motivoDelUltimoFallo(9L)).contains("sin saldo (402)");
    }

    @Test
    @DisplayName("Sin ejecución fallida no hay motivo")
    void sinEjecucionNoHayMotivo() {
        when(ejecuciones.findFirstByTrabajoIaIdAndEsExitosaFalseOrderByIdDesc(9L))
                .thenReturn(Optional.empty());

        assertThat(registro.motivoDelUltimoFallo(9L)).isEmpty();
    }

    @Test
    @DisplayName("Un error nulo no es un motivo")
    void errorNuloNoEsMotivo() {
        when(ejecuciones.findFirstByTrabajoIaIdAndEsExitosaFalseOrderByIdDesc(9L))
                .thenReturn(Optional.of(EjecucionIa.builder().error(null).build()));

        assertThat(registro.motivoDelUltimoFallo(9L)).isEmpty();
    }

    @Test
    @DisplayName("Un error en blanco tampoco: el panel caería en el texto por defecto")
    void errorEnBlancoNoEsMotivo() {
        when(ejecuciones.findFirstByTrabajoIaIdAndEsExitosaFalseOrderByIdDesc(9L))
                .thenReturn(Optional.of(EjecucionIa.builder().error("   ").build()));

        assertThat(registro.motivoDelUltimoFallo(9L)).isEmpty();
    }
}
