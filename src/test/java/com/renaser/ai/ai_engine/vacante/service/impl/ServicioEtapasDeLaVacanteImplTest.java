package com.renaser.ai.ai_engine.vacante.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.pesos.entity.Etapa;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.RecorridoDeLaVacante;

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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cambiar las etapas de una vacante desde la tuerca (V95): qué recorrido se acepta, cuándo ya no
 * se puede y qué queda escrito.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Cambiar las etapas de una vacante")
class ServicioEtapasDeLaVacanteImplTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long VACANTE = 7L;
    private static final ContextoUsuario QUIEN = new ContextoUsuario(10L, 20L, ORGANIZACION, "EQUIPO",
            List.of(1L), Map.of("elegir_plantilla_evaluacion", "TODO"));
    private static final List<Etapa> CATALOGO = List.of(
            Etapa.builder().codigo("PERFIL_INTEGRAL").nombre("Perfil Integral").orden(1).fijaPrimera(true).build(),
            Etapa.builder().codigo("PRUEBA_PUESTO").nombre("Prueba del puesto").orden(2).build(),
            Etapa.builder().codigo("SIMULACION").nombre("Simulación de trabajo").orden(3).build(),
            Etapa.builder().codigo("VALIDACION").nombre("Validación práctica").orden(4).build(),
            Etapa.builder().codigo("DECISION").nombre("Decisión").orden(5).build());

    @Mock private VacanteRepository vacantes;
    @Mock private PostulacionRepository postulaciones;
    @Mock private RecorridoDeLaVacante recorridos;
    @Mock private ServicioAuditoria auditoria;

    private ServicioEtapasDeLaVacanteImpl servicio;
    private Vacante vacante;

    @BeforeEach
    void armar() {
        servicio = new ServicioEtapasDeLaVacanteImpl(vacantes, postulaciones, recorridos, auditoria);
        vacante = Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION).estado("BORRADOR").build();
        lenient().when(vacantes.bloquear(VACANTE, ORGANIZACION)).thenReturn(Optional.of(vacante));
        lenient().when(recorridos.catalogo()).thenReturn(CATALOGO);
        lenient().when(recorridos.de(VACANTE)).thenReturn(List.of("PERFIL_INTEGRAL", "PRUEBA_PUESTO", "DECISION"));
    }

    @Test
    @DisplayName("sin postulaciones, apagar la Prueba se guarda y queda escrito de qué a qué (AC-02)")
    void apagarLaPrueba() {
        servicio.cambiar(QUIEN, VACANTE, List.of("PERFIL_INTEGRAL", "DECISION"));

        verify(recorridos).escribir(VACANTE, List.of("PERFIL_INTEGRAL", "DECISION"));
        verify(auditoria).registrar(ORGANIZACION, QUIEN, "cambiar_etapas_vacante", "vacante", VACANTE,
                Map.of("etapas", "PERFIL_INTEGRAL → PRUEBA_PUESTO → DECISION"),
                Map.of("etapas", "PERFIL_INTEGRAL → DECISION"), null);
    }

    @Test
    @DisplayName("subir la Simulación por encima de la Prueba guarda ese orden (AC-03)")
    void subirLaSimulacion() {
        servicio.cambiar(QUIEN, VACANTE, List.of("PERFIL_INTEGRAL", "SIMULACION", "PRUEBA_PUESTO", "DECISION"));

        verify(recorridos).escribir(VACANTE,
                List.of("PERFIL_INTEGRAL", "SIMULACION", "PRUEBA_PUESTO", "DECISION"));
    }

    @Test
    @DisplayName("un recorrido que no empieza por el Perfil Integral es un 400 y no se guarda (AC-04)")
    void sinElPerfilPrimero() {
        assertThatThrownBy(() -> servicio.cambiar(QUIEN, VACANTE, List.of("PRUEBA_PUESTO", "PERFIL_INTEGRAL")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empezar por Perfil Integral");
        assertThatThrownBy(() -> servicio.cambiar(QUIEN, VACANTE, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.cambiar(QUIEN, VACANTE, List.of("PERFIL_INTEGRAL", "NADA")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NADA");

        verify(recorridos, never()).escribir(any(), anyList());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("con una postulación, de cualquier estado, es un 409 y no se guarda (AC-05)")
    void conPostulantes() {
        when(postulaciones.countByVacanteId(VACANTE)).thenReturn(1L);

        assertThatThrownBy(() -> servicio.cambiar(QUIEN, VACANTE, List.of("PERFIL_INTEGRAL", "DECISION")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fijas desde el primero");

        verify(recorridos, never()).escribir(any(), anyList());
    }

    @Test
    @DisplayName("una vacante cerrada o archivada tampoco cambia sus etapas: 409")
    void cerradaOArchivada() {
        vacante.setEstado("CERRADA");
        assertThatThrownBy(() -> servicio.cambiar(QUIEN, VACANTE, List.of("PERFIL_INTEGRAL")))
                .isInstanceOf(IllegalStateException.class);

        vacante.setEstado("BORRADOR");
        vacante.setArchivadaEn(Instant.now());
        assertThatThrownBy(() -> servicio.cambiar(QUIEN, VACANTE, List.of("PERFIL_INTEGRAL")))
                .isInstanceOf(IllegalStateException.class);

        verify(recorridos, never()).escribir(any(), anyList());
    }

    @Test
    @DisplayName("una eliminada contesta 409, y la de otra empresa 404")
    void eliminadaOAjena() {
        when(vacantes.bloquear(VACANTE, ORGANIZACION)).thenReturn(Optional.empty());
        when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION)).thenReturn(Optional.of(
                Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION).eliminadaEn(Instant.now()).build()));
        assertThatThrownBy(() -> servicio.cambiar(QUIEN, VACANTE, List.of("PERFIL_INTEGRAL")))
                .isInstanceOf(IllegalStateException.class);

        when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.cambiar(QUIEN, VACANTE, List.of("PERFIL_INTEGRAL")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("el mismo recorrido que ya tiene no escribe nada ni queda en la auditoría")
    void elMismoNoEscribe() {
        servicio.cambiar(QUIEN, VACANTE, List.of("PERFIL_INTEGRAL", "PRUEBA_PUESTO", "DECISION"));

        verify(recorridos, never()).escribir(any(), anyList());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("el Perfil solo es un recorrido válido")
    void elPerfilSolo() {
        servicio.cambiar(QUIEN, VACANTE, List.of("PERFIL_INTEGRAL"));

        verify(recorridos).escribir(eq(VACANTE), eq(List.of("PERFIL_INTEGRAL")));
    }
}
