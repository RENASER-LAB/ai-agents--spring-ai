package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.service.FinDelPerfilIntegral;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.whatsapp.service.ResultadosPorWhatsapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cerrar una vacante, a mano o por su fecha límite (V78).
 *
 * <p>Lo que se protege: el orden —primero se cierra lo que esperaba la evaluación y solo
 * después se avisa por WhatsApp a quien sigue (AC-11)—, el autor y el motivo del cierre por
 * fecha (AC-10), y que el sondeo no cierre dos veces ni lo que no le toca.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Cerrar la vacante, a mano o por su fecha")
class CierreDeLaVacanteTest {

    private static final long ORGANIZACION = 1L;
    private static final long VACANTE = 40L;
    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            7L, 3L, ORGANIZACION, "EQUIPO", List.of(1L), Map.of("cerrar_vacante", "TODO"));

    @Mock private VacanteRepository vacantes;
    @Mock private ServicioAuditoria auditoria;
    @Mock private FinDelPerfilIntegral perfilIntegral;
    @Mock private PostulacionRepository postulaciones;
    @Mock private PlatformTransactionManager transacciones;
    @Mock private ResultadosPorWhatsapp whatsapp;

    private CierreDeLaVacante cierre;

    @BeforeEach
    void crear() {
        cierre = new CierreDeLaVacante(vacantes, auditoria, perfilIntegral, postulaciones,
                transacciones);
        cierre.setWhatsapp(whatsapp);
    }

    private Vacante vacante(String estado, String tipoCierre, Instant cierraEn) {
        return Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION).estado(estado)
                .tipoCierre(tipoCierre).cierraEn(cierraEn).build();
    }

    @Test
    @DisplayName("a mano: se cierra, se audita con quien y su motivo, vence el Perfil Integral y "
            + "solo después avisa por WhatsApp a quien sigue (AC-09, AC-11)")
    void aMano() {
        Vacante v = vacante("PUBLICADA", "PERMANENTE", null);

        cierre.cerrar(v, QUIEN, "Ya contratamos");

        assertThat(v.getEstado()).isEqualTo("CERRADA");
        assertThat(v.getCerradaEn()).isNotNull();
        InOrder orden = inOrder(vacantes, auditoria, perfilIntegral, postulaciones, whatsapp);
        orden.verify(vacantes).save(v);
        orden.verify(auditoria).registrar(ORGANIZACION, QUIEN, "cerrar_vacante", "vacante", VACANTE,
                Map.of("estado", "PUBLICADA"), Map.of("estado", "CERRADA"), "Ya contratamos");
        orden.verify(perfilIntegral).alCerrarseLaVacante(VACANTE, v.getCerradaEn());
        orden.verify(postulaciones).flush();
        orden.verify(whatsapp).alCerrarLaConvocatoria(v);
    }

    @Test
    @DisplayName("sin el WhatsApp v2 configurado se cierra igual")
    void sinWhatsapp() {
        cierre.setWhatsapp(null);
        Vacante v = vacante("PUBLICADA", "PERMANENTE", null);

        cierre.cerrar(v, QUIEN, "Ya contratamos");

        assertThat(v.getEstado()).isEqualTo("CERRADA");
        verify(perfilIntegral).alCerrarseLaVacante(eq(VACANTE), any());
        verifyNoInteractions(whatsapp);
    }

    @Test
    @DisplayName("por su fecha: la cierra el sistema, con «Llegó la fecha de cierre» en la auditoría (AC-10)")
    void porSuFecha() {
        Vacante candidata = vacante("PUBLICADA", "FECHA", Instant.now().minusSeconds(30));
        Vacante bloqueada = vacante("PUBLICADA", "FECHA", candidata.getCierraEn());
        when(vacantes.queLlegaronASuFecha(any())).thenReturn(List.of(candidata));
        when(vacantes.bloquear(VACANTE, ORGANIZACION)).thenReturn(Optional.of(bloqueada));

        assertThat(cierre.cerrarLasQueLlegaronASuFecha()).isEqualTo(1);

        assertThat(bloqueada.getEstado()).isEqualTo("CERRADA");
        verify(auditoria).registrar(eq(ORGANIZACION), isNull(), eq("cerrar_vacante"), eq("vacante"),
                eq(VACANTE), any(), any(), eq("Llegó la fecha de cierre"));
        verify(perfilIntegral).alCerrarseLaVacante(eq(VACANTE), any());
        verify(whatsapp).alCerrarLaConvocatoria(bloqueada);
    }

    @Test
    @DisplayName("si el sondeo corre dos veces, la segunda la encuentra cerrada y no hace nada")
    void dosVecesNoCierraDosVeces() {
        Vacante candidata = vacante("PUBLICADA", "FECHA", Instant.now().minusSeconds(30));
        when(vacantes.queLlegaronASuFecha(any())).thenReturn(List.of(candidata));
        when(vacantes.bloquear(VACANTE, ORGANIZACION))
                .thenReturn(Optional.of(vacante("CERRADA", "FECHA", candidata.getCierraEn())));

        assertThat(cierre.cerrarLasQueLlegaronASuFecha()).isZero();

        verify(vacantes, never()).save(any());
        verifyNoInteractions(auditoria, perfilIntegral, whatsapp);
    }

    @Test
    @DisplayName("si entre tanto alguien movió la fecha o la pasó a Permanente, no se cierra")
    void siLaFechaCambioNoSeCierra() {
        Vacante candidata = vacante("PUBLICADA", "FECHA", Instant.now().minusSeconds(30));
        when(vacantes.queLlegaronASuFecha(any())).thenReturn(List.of(candidata));
        when(vacantes.bloquear(VACANTE, ORGANIZACION))
                .thenReturn(Optional.of(vacante("PUBLICADA", "FECHA", Instant.now().plusSeconds(3600))))
                .thenReturn(Optional.of(vacante("PUBLICADA", "PERMANENTE", null)))
                .thenReturn(Optional.empty());

        assertThat(cierre.cerrarLasQueLlegaronASuFecha()).isZero();
        assertThat(cierre.cerrarLasQueLlegaronASuFecha()).isZero();
        assertThat(cierre.cerrarLasQueLlegaronASuFecha()).isZero();

        verify(vacantes, never()).save(any());
        verifyNoInteractions(auditoria, perfilIntegral);
    }

    @Test
    @DisplayName("si una vacante falla, las demás se cierran igual")
    void unaQueFallaNoFrenaALasDemas() {
        Vacante rota = Vacante.builder().id(41L).organizacionId(ORGANIZACION).build();
        Vacante candidata = vacante("PUBLICADA", "FECHA", Instant.now().minusSeconds(30));
        when(vacantes.queLlegaronASuFecha(any())).thenReturn(List.of(rota, candidata));
        when(vacantes.bloquear(41L, ORGANIZACION)).thenThrow(new IllegalStateException("bloqueo"));
        when(vacantes.bloquear(VACANTE, ORGANIZACION))
                .thenReturn(Optional.of(vacante("PUBLICADA", "FECHA", candidata.getCierraEn())));

        assertThat(cierre.cerrarLasQueLlegaronASuFecha()).isEqualTo(1);
        verify(perfilIntegral).alCerrarseLaVacante(eq(VACANTE), any());
        verify(perfilIntegral, never()).alCerrarseLaVacante(eq(41L), any());
        verify(vacantes, never()).bloquear(anyLong(), eq(99L));
    }
}
